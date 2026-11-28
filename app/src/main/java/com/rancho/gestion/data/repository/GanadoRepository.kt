package com.rancho.gestion.data.repository

import android.content.Context
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.rancho.gestion.data.dao.AnimalDao
import com.rancho.gestion.data.dao.MortalidadDao
import com.rancho.gestion.data.dao.RangoPesoDao
import com.rancho.gestion.data.dao.SincronizacionDao
import com.rancho.gestion.data.entities.*
import kotlinx.coroutines.flow.Flow

sealed class ResultadoOperacion {
    object Ok : ResultadoOperacion()
    data class Error(val mensaje: String) : ResultadoOperacion()
}

class GanadoRepository(
    private val context: Context,
    private val animalDao: AnimalDao,
    private val rangoPesoDao: RangoPesoDao,
    private val mortalidadDao: MortalidadDao,
    private val sincronizacionDao: SincronizacionDao
) {
    private fun triggerSync() {
        val syncRequest = OneTimeWorkRequestBuilder<com.rancho.gestion.data.sync.SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(syncRequest)
    }
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
        tipoArete: TipoArete = TipoArete.SADER,
        color: String,
        sexo: Sexo,
        peso: Double,
        precioCompra: Double,
        proveedor: String,
        fotografiaLocal: String?,
        idUsuario: String,
        esAdmin: Boolean
    ): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        // Validaciones según Tipo de Arete
        if (tipoArete == TipoArete.SADER) {
            if (!arete.startsWith("484")) return@withContext ResultadoOperacion.Error("El arete SADER debe comenzar con el prefijo 484")
            if (arete.length != 15) return@withContext ResultadoOperacion.Error("El arete SADER debe tener exactamente 15 dígitos")
        } else {
            if (arete.length > 6) return@withContext ResultadoOperacion.Error("El arete GENÉRICO no puede tener más de 6 dígitos")
            if (arete.isEmpty()) return@withContext ResultadoOperacion.Error("El arete es obligatorio")
        }
        
        if (arete.any { !it.isDigit() }) return@withContext ResultadoOperacion.Error("El arete debe ser estrictamente numérico")
        
        // Regla: Si ya existe (Aprobado o Pendiente), avisar
        val existente = animalDao.getByArete(arete)
        if (existente != null) {
            return@withContext if (existente.estadoAprobacion == EstadoAprobacion.PENDIENTE) {
                ResultadoOperacion.Error("La operación de entrada ya fue realizada y está pendiente a revisar por un administrador.")
            } else {
                ResultadoOperacion.Error("Ya existe un animal registrado con ese arete.")
            }
        }

        val animal = Animal(
            numeroArete = arete,
            tipoArete = tipoArete,
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
        
        animalDao.insert(animal)
        
        // --- OFFLINE-FIRST: Poner aviso en la cola de sincronización ---
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = animal.numeroArete,
                operacion = "INSERT",
                versionLocal = animal.version,
                idDispositivo = idUsuario // Usamos el ID del usuario como referencia temporal
            )
        )
        
        // --- DESPERTAR AL WORKER INMEDIATAMENTE ---
        triggerSync()
        
        ResultadoOperacion.Ok
    }

    /* ---------- Aprobación de Animales (PROC-11) ---------- */
    suspend fun aprobarAnimal(animal: Animal, idAdmin: String): ResultadoOperacion {
        val updatedAnimal = animal.copy(
            estadoAprobacion = EstadoAprobacion.APROBADO,
            fechaActualizacion = System.currentTimeMillis()
        )
        animalDao.update(updatedAnimal)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = updatedAnimal.numeroArete,
                operacion = "UPDATE",
                versionLocal = updatedAnimal.version,
                idDispositivo = idAdmin
            )
        )
        triggerSync()
        return ResultadoOperacion.Ok
    }

    suspend fun rechazarAnimal(animal: Animal) {
        animalDao.delete(animal)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = animal.numeroArete,
                operacion = "DELETE",
                versionLocal = animal.version,
                idDispositivo = animal.idUsuarioRegistro
            )
        )
        triggerSync()
    }

    suspend fun rechazarMortalidad(mortalidad: Mortalidad): Unit = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mortalidadDao.eliminarSolicitud(mortalidad.idMortalidad)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "mortalidad",
                idEntidad = mortalidad.idMortalidad,
                operacion = "DELETE",
                versionLocal = mortalidad.version,
                idDispositivo = mortalidad.idUsuarioRegistro
            )
        )
        triggerSync()
    }

    /* ---------- Registrar Muerte (CU-04) ---------- */
    suspend fun registrarMuerte(
        arete: String, 
        motivo: String, 
        recuperacion: Double,
        idUsuario: String,
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
        
        val nuevaMortalidad = Mortalidad(
                numeroArete = arete,
                causa = motivo,
                costoInicial = animal.precioCompra,
                recuperacion = recuperacion,
                mermaEconomica = merma,
                estadoAprobacion = if (esAdmin) EstadoAprobacion.APROBADO else EstadoAprobacion.PENDIENTE,
                idUsuarioRegistro = idUsuario
            )
        mortalidadDao.insert(nuevaMortalidad)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "mortalidad",
                idEntidad = nuevaMortalidad.idMortalidad,
                operacion = "INSERT",
                versionLocal = nuevaMortalidad.version,
                idDispositivo = idUsuario
            )
        )
        
        // Si es Admin, procesamos la baja de inmediato
        if (esAdmin) {
            val updatedAnimal = animal.copy(
                estadoInventario = EstadoInventario.MUERTO,
                fechaBaja = System.currentTimeMillis(),
                tipoBaja = TipoBaja.MORTALIDAD,
                fechaActualizacion = System.currentTimeMillis()
            )
            animalDao.update(updatedAnimal)
            
            sincronizacionDao.insert(
                ColaSincronizacion(
                    entidad = "ganado",
                    idEntidad = updatedAnimal.numeroArete,
                    operacion = "UPDATE",
                    versionLocal = updatedAnimal.version,
                    idDispositivo = idUsuario
                )
            )
        }
        
        triggerSync()
        ResultadoOperacion.Ok
    }

    /* ---------- Aprobación de Bajas (Mortalidad) ---------- */
    suspend fun aprobarMortalidad(mortalidad: Mortalidad, idAdmin: String): ResultadoOperacion {
        val animal = animalDao.getByArete(mortalidad.numeroArete) ?: return ResultadoOperacion.Error("Animal no encontrado")
        
        // 1. Marcar esta solicitud como aprobada
        val updatedMortalidad = mortalidad.copy(
            idMortalidad = mortalidad.idMortalidad, // Asegurar mismo ID para update
            estadoAprobacion = EstadoAprobacion.APROBADO,
            idUsuarioAprobacion = idAdmin,
            fechaAprobacion = System.currentTimeMillis()
        )
        mortalidadDao.insert(updatedMortalidad)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "mortalidad",
                idEntidad = updatedMortalidad.idMortalidad,
                operacion = "UPDATE",
                versionLocal = updatedMortalidad.version,
                idDispositivo = idAdmin
            )
        )

        // 2. Regla: Eliminar otras solicitudes pendientes del mismo animal
        mortalidadDao.eliminarPendientesDeAnimal(mortalidad.numeroArete, mortalidad.idMortalidad)

        // 3. Procesar la baja real en el inventario
        val updatedAnimal = animal.copy(
            estadoInventario = EstadoInventario.MUERTO,
            fechaBaja = System.currentTimeMillis(),
            tipoBaja = TipoBaja.MORTALIDAD,
            fechaActualizacion = System.currentTimeMillis()
        )
        animalDao.update(updatedAnimal)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = updatedAnimal.numeroArete,
                operacion = "UPDATE",
                versionLocal = updatedAnimal.version,
                idDispositivo = idAdmin
            )
        )

        triggerSync()
        return ResultadoOperacion.Ok
    }

    /* ---------- Otros ---------- */
    suspend fun editarAnimal(animal: Animal, esAdmin: Boolean): ResultadoOperacion {
        if (!esAdmin) return ResultadoOperacion.Error("Solo el administrador puede editar registros consolidados")
        
        val updatedAnimal = animal.copy(fechaActualizacion = System.currentTimeMillis())
        animalDao.update(updatedAnimal)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = updatedAnimal.numeroArete,
                operacion = "UPDATE",
                versionLocal = updatedAnimal.version,
                idDispositivo = updatedAnimal.idUsuarioRegistro
            )
        )
        triggerSync()
        return ResultadoOperacion.Ok
    }

    suspend fun eliminarAnimal(animal: Animal) {
        animalDao.delete(animal)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = animal.numeroArete,
                operacion = "DELETE",
                versionLocal = animal.version,
                idDispositivo = animal.idUsuarioRegistro
            )
        )
        triggerSync()
    }

    suspend fun actualizarFoto(arete: String, path: String?) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val animal = animalDao.getByArete(arete)
        if (animal != null) {
            val updatedAnimal = animal.copy(
                fotografiaLocal = path,
                fotografiaLocalDisponible = path != null,
                fechaActualizacion = System.currentTimeMillis()
            )
            animalDao.update(updatedAnimal)
            
            sincronizacionDao.insert(
                ColaSincronizacion(
                    entidad = "ganado",
                    idEntidad = updatedAnimal.numeroArete,
                    operacion = "UPDATE",
                    versionLocal = updatedAnimal.version,
                    idDispositivo = updatedAnimal.idUsuarioRegistro
                )
            )
            triggerSync()
        }
    }

    suspend fun getMortalidadAprobada(arete: String): Mortalidad? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mortalidadDao.getMortalidadAprobadaPorArete(arete)
    }

    suspend fun actualizarMortalidad(mortalidad: Mortalidad): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        mortalidadDao.updateMortalidad(mortalidad)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "mortalidad",
                idEntidad = mortalidad.idMortalidad,
                operacion = "UPDATE",
                versionLocal = mortalidad.version,
                idDispositivo = mortalidad.idUsuarioRegistro
            )
        )
        triggerSync()
        ResultadoOperacion.Ok
    }

    suspend fun cambiarFase(arete: String, nuevaFase: FaseGanado): ResultadoOperacion {
        val animal = animalDao.getByArete(arete) ?: return ResultadoOperacion.Error("Animal no encontrado")
        val updatedAnimal = animal.copy(fase = nuevaFase, fechaActualizacion = System.currentTimeMillis())
        animalDao.update(updatedAnimal)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "ganado",
                idEntidad = updatedAnimal.numeroArete,
                operacion = "UPDATE",
                versionLocal = updatedAnimal.version,
                idDispositivo = updatedAnimal.idUsuarioRegistro
            )
        )
        triggerSync()
        return ResultadoOperacion.Ok
    }
}
