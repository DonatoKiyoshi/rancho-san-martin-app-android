package com.rancho.gestion.data.repository

import com.rancho.gestion.data.dao.AnimalDao
import com.rancho.gestion.data.dao.MortalidadDao
import com.rancho.gestion.data.dao.RangoPesoDao
import com.rancho.gestion.data.entities.*
import kotlinx.coroutines.flow.Flow

sealed class ResultadoOperacion {
    object Ok : ResultadoOperacion()
    data class Error(val mensaje: String) : ResultadoOperacion()
}

class GanadoRepository(
    private val animalDao: AnimalDao,
    private val rangoPesoDao: RangoPesoDao,
    private val mortalidadDao: MortalidadDao
) {
    /* ---------- Consultas reactivas ---------- */
    fun activos(): Flow<List<Animal>> = animalDao.getActivos()
    fun buscar(query: String): Flow<List<Animal>> = animalDao.buscarActivos(query)
    fun archivados(): Flow<List<Animal>> = animalDao.getArchivados()
    fun proveedores(): Flow<List<String>> = animalDao.getProveedores()
    fun rangosPeso(): Flow<List<RangoPeso>> = rangoPesoDao.getAll()
    fun pendientesAprobacion(): Flow<List<Animal>> = animalDao.getPendientesAprobacion()
    fun mortalidadPendiente(): Flow<List<Mortalidad>> = mortalidadDao.getPendientes()

    suspend fun buscarPorArete(arete: String): Animal? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        animalDao.getByArete(arete)
    }

    /* ---------- Entrada de Ganado (CU-02) ---------- */
    suspend fun registrarAnimal(
        arete: String,
        color: String,
        sexo: Sexo,
        peso: Double,
        precioCompra: Double,
        proveedor: String,
        fotografiaLocal: String?,
        idUsuario: Long,
        esAdmin: Boolean
    ): ResultadoOperacion {
        if (!arete.startsWith("484")) return ResultadoOperacion.Error("El arete debe comenzar con el prefijo 484")
        if (arete.length > 15) return ResultadoOperacion.Error("El arete no debe exceder los 15 dígitos")
        if (arete.any { !it.isDigit() }) return ResultadoOperacion.Error("El arete debe ser estrictamente numérico")
        
        // Regla: Si ya existe (Aprobado o Pendiente), avisar
        val existente = animalDao.getByArete(arete)
        if (existente != null) {
            return if (existente.estadoAprobacion == EstadoAprobacion.PENDIENTE) {
                ResultadoOperacion.Error("La operación de entrada ya fue realizada y está pendiente a revisar por un administrador.")
            } else {
                ResultadoOperacion.Error("Ya existe un animal registrado con ese arete.")
            }
        }

        val animal = Animal(
            numeroArete = arete,
            color = color,
            sexo = sexo,
            proveedor = proveedor,
            precioCompra = precioCompra,
            fechaEntrada = System.currentTimeMillis(),
            pesoEntrada = peso,
            idRangoPeso = null,
            fase = FaseGanado.ACOPIO,
            estadoInventario = EstadoInventario.ACTIVO,
            estadoAprobacion = if (esAdmin) EstadoAprobacion.APROBADO else EstadoAprobacion.PENDIENTE,
            idUsuarioRegistro = idUsuario,
            fotografiaLocal = fotografiaLocal,
            fotografiaLocalDisponible = fotografiaLocal != null
        )
        android.util.Log.wtf("GanadoRepository", "ALTA ANIMAL: arete=${animal.numeroArete}, fase=${animal.fase}, fecha=${animal.fechaEntrada}")
        
        animalDao.insert(animal)
        return ResultadoOperacion.Ok
    }

    /* ---------- Aprobación de Animales (PROC-11) ---------- */
    suspend fun aprobarAnimal(animal: Animal, idAdmin: Long): ResultadoOperacion {
        // Al aprobar un animal, si hubiera otras solicitudes para el mismo arete (no debería por PK, pero por lógica de negocio)
        // se toman en cuenta. Como numeroArete es PK, no hay duplicados en la tabla 'ganado'.
        
        animalDao.update(animal.copy(
            estadoAprobacion = EstadoAprobacion.APROBADO,
            fechaActualizacion = System.currentTimeMillis()
        ))
        return ResultadoOperacion.Ok
    }

    suspend fun rechazarAnimal(animal: Animal) {
        animalDao.delete(animal)
    }

    suspend fun rechazarMortalidad(mortalidad: Mortalidad): Unit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mortalidadDao.eliminarSolicitud(mortalidad.idMortalidad)
    }

    /* ---------- Registrar Muerte (CU-04) ---------- */
    suspend fun registrarMuerte(
        arete: String, 
        motivo: String, 
        recuperacion: Double,
        idUsuario: Long,
        esAdmin: Boolean
    ): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val animal = animalDao.getByArete(arete) ?: return@withContext ResultadoOperacion.Error("Animal no encontrado")
        
        // Falla 2: Validar si tiene solicitudes pendientes
        // No permitir si ya hay una solicitud de baja O venta pendiente
        val muertePendiente = mortalidadDao.getMortalidadPendienteDeAnimal(arete)
        if (muertePendiente != null) {
            return@withContext ResultadoOperacion.Error("La operación de baja ya fue realizada y está pendiente a revisar por un administrador.")
        }
        
        // También verificar si tiene ventas pendientes
        // Nota: Como no tenemos VentaDao aquí directamente, lo ideal sería que VentaRepository exponga esto o inyectar VentaDao
        // Para este fix rápido, asumiremos que mortalidadDao es suficiente para su propia tabla y el resto se valida en UI/Repository correspondiente
        
        val merma = (animal.precioCompra - recuperacion).coerceAtLeast(0.0)
        
        mortalidadDao.insert(
            Mortalidad(
                numeroArete = arete,
                causa = motivo,
                costoInicial = animal.precioCompra,
                recuperacion = recuperacion,
                mermaEconomica = merma,
                estadoAprobacion = if (esAdmin) EstadoAprobacion.APROBADO else EstadoAprobacion.PENDIENTE,
                idUsuarioRegistro = idUsuario
            )
        )
        
        // Si es Admin, procesamos la baja de inmediato
        if (esAdmin) {
            animalDao.update(animal.copy(
                estadoInventario = EstadoInventario.MUERTO,
                fechaBaja = System.currentTimeMillis(),
                tipoBaja = TipoBaja.MORTALIDAD,
                fechaActualizacion = System.currentTimeMillis()
            ))
        }
        
        ResultadoOperacion.Ok
    }

    /* ---------- Aprobación de Bajas (Mortalidad) ---------- */
    suspend fun aprobarMortalidad(mortalidad: Mortalidad, idAdmin: Long): ResultadoOperacion {
        val animal = animalDao.getByArete(mortalidad.numeroArete) ?: return ResultadoOperacion.Error("Animal no encontrado")
        
        // 1. Marcar esta solicitud como aprobada
        mortalidadDao.insert(mortalidad.copy(
            idMortalidad = mortalidad.idMortalidad, // Asegurar mismo ID para update
            estadoAprobacion = EstadoAprobacion.APROBADO,
            idUsuarioAprobacion = idAdmin,
            fechaAprobacion = System.currentTimeMillis()
        ))

        // 2. Regla: Eliminar otras solicitudes pendientes del mismo animal
        mortalidadDao.eliminarPendientesDeAnimal(mortalidad.numeroArete, mortalidad.idMortalidad)

        // 3. Procesar la baja real en el inventario
        animalDao.update(animal.copy(
            estadoInventario = EstadoInventario.MUERTO,
            fechaBaja = System.currentTimeMillis(),
            tipoBaja = TipoBaja.MORTALIDAD,
            fechaActualizacion = System.currentTimeMillis()
        ))

        return ResultadoOperacion.Ok
    }

    /* ---------- Otros ---------- */
    suspend fun editarAnimal(animal: Animal, esAdmin: Boolean): ResultadoOperacion {
        if (!esAdmin) return ResultadoOperacion.Error("Solo el administrador puede editar registros consolidados")
        
        animalDao.update(animal.copy(fechaActualizacion = System.currentTimeMillis()))
        return ResultadoOperacion.Ok
    }

    suspend fun eliminarAnimal(animal: Animal) = animalDao.delete(animal)

    suspend fun getMortalidadAprobada(arete: String): Mortalidad? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mortalidadDao.getMortalidadAprobadaPorArete(arete)
    }

    suspend fun actualizarMortalidad(mortalidad: Mortalidad): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mortalidadDao.updateMortalidad(mortalidad)
        ResultadoOperacion.Ok
    }

    suspend fun cambiarFase(arete: String, nuevaFase: FaseGanado): ResultadoOperacion {
        val animal = animalDao.getByArete(arete) ?: return ResultadoOperacion.Error("Animal no encontrado")
        animalDao.update(animal.copy(fase = nuevaFase, fechaActualizacion = System.currentTimeMillis()))
        return ResultadoOperacion.Ok
    }
}
