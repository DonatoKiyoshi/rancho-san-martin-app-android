package com.rancho.gestion.data.repository

import com.rancho.gestion.data.dao.AnimalDao
import com.rancho.gestion.data.dao.MortalidadDao
import com.rancho.gestion.data.dao.VentaDao
import com.rancho.gestion.data.entities.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

class VentaRepository(
    private val ventaDao: VentaDao,
    private val animalDao: AnimalDao,
    private val mortalidadDao: MortalidadDao
) {
    fun getVentas(): Flow<List<Venta>> = ventaDao.getAllVentas()
    fun getTodosLosDetalles(): Flow<List<DetalleVenta>> = ventaDao.getAllDetalles()
    fun getJaulasAbiertas(): Flow<List<Venta>> = ventaDao.getJaulasAbiertas()
    fun getDetalles(ventaId: Long): Flow<List<DetalleVenta>> = ventaDao.getDetallesPorVenta(ventaId)
    fun getCandidatos(): Flow<List<Animal>> = animalDao.getActivosDisponibles()

    /* ---------- Crear Jaula / Lote ---------- */
    suspend fun crearJaula(comprador: String, idUsuario: Long): Long {
        val venta = Venta(
            comprador = comprador,
            modalidad = ModalidadVenta.JAULA,
            montoTotal = 0.0,
            totalCabezas = 0,
            pesoTotal = 0.0,
            estadoAprobacion = EstadoAprobacion.PENDIENTE,
            idUsuarioRegistro = idUsuario,
            finalizada = false
        )
        return ventaDao.insertVenta(venta)
    }

    /* ---------- Venta Individual (Transacción robusta) ---------- */
    suspend fun realizarVentaIndividual(
        arete: String,
        comprador: String,
        pesoSalida: Double,
        montoCobro: Double,
        idUsuario: Long,
        esAdmin: Boolean,
        confirmarRemoverDeJaula: Boolean = false
    ): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val animal = animalDao.getByArete(arete.trim()) ?: return@withContext ResultadoOperacion.Error("Animal no encontrado")
            if (animal.estadoInventario != EstadoInventario.ACTIVO) {
                return@withContext ResultadoOperacion.Error("El animal ya no se encuentra activo")
            }
            if (animal.fase == FaseGanado.DESARROLLO) {
                return@withContext ResultadoOperacion.Error("El animal no puede venderse ya que se encuentra en fase de Desarrollo")
            }

            // Validar si tiene solicitudes pendientes (Falla 2)
            val ventaPendiente = ventaDao.getVentaPendienteDeAnimal(arete.trim())
            val muertePendiente = mortalidadDao.getMortalidadPendienteDeAnimal(arete.trim())
            if (ventaPendiente != null || muertePendiente != null) {
                return@withContext ResultadoOperacion.Error("El animal ya tiene una solicitud de venta o baja pendiente de revisión.")
            }

            // Validar si está en una jaula abierta (Falla 1)
            val jaulaAbierta = ventaDao.getJaulaAbiertaDeAnimal(arete.trim())
            if (jaulaAbierta != null && !confirmarRemoverDeJaula) {
                return@withContext ResultadoOperacion.Error("JAULA_CONFLICT:${jaulaAbierta.comprador}")
            }

            // Si se confirmó remover de la jaula, lo quitamos de cualquier jaula abierta
            if (jaulaAbierta != null && confirmarRemoverDeJaula) {
                val idsVentas = ventaDao.getIdsVentasDeAnimal(arete.trim())
                ventaDao.deleteDetalle(DetalleVenta(idVenta = jaulaAbierta.idVenta, numeroArete = arete.trim(), pesoSalida = 0.0, precioUnitario = 0.0, subtotal = 0.0)) // Nota: deleteDetalle real requiere el objeto completo o query
                // Mejor usar query directo para limpiar
                ventaDao.eliminarAnimalDeVentasPendientes(arete.trim()) // Esto limpia de cualquier jaula pendiente/abierta
                idsVentas.forEach { actualizarTotalesVenta(it) }
            }

            // 1. Crear el encabezado de venta marcado como INDIVIDUAL
            val ventaId = ventaDao.insertVenta(Venta(
                comprador = comprador.trim(),
                modalidad = ModalidadVenta.INDIVIDUAL,
                montoTotal = montoCobro,
                totalCabezas = 1,
                pesoTotal = pesoSalida,
                estadoAprobacion = if (esAdmin) EstadoAprobacion.APROBADO else EstadoAprobacion.PENDIENTE,
                idUsuarioRegistro = idUsuario,
                finalizada = true, // Las individuales nacen finalizadas
                idUsuarioAprobacion = if (esAdmin) idUsuario else null,
                fechaAprobacion = if (esAdmin) System.currentTimeMillis() else null
            ))

            // 2. Insertar detalle
            ventaDao.insertDetalle(DetalleVenta(
                idVenta = ventaId,
                numeroArete = arete.trim(),
                pesoSalida = pesoSalida,
                precioUnitario = if (pesoSalida > 0) montoCobro / pesoSalida else 0.0,
                subtotal = montoCobro
            ))

            // 3. Solo si es admin, se descuenta del inventario ahora mismo
            if (esAdmin) {
                animalDao.update(animal.copy(
                    estadoInventario = EstadoInventario.VENDIDO,
                    fechaBaja = System.currentTimeMillis(),
                    tipoBaja = TipoBaja.VENTA,
                    fechaActualizacion = System.currentTimeMillis()
                ))
            }

            ResultadoOperacion.Ok
        } catch (e: Exception) {
            android.util.Log.e("VentaRepository", "Error en realizarVentaIndividual", e)
            ResultadoOperacion.Error("Error al procesar venta: ${e.message}")
        }
    }

    /* ---------- Eliminar Jaula ---------- */
    suspend fun eliminarJaula(venta: Venta) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            // Eliminar detalles primero (si es que la cascada no está activa o falla)
            ventaDao.deleteDetallesByVenta(venta.idVenta)
            // Eliminar encabezado
            ventaDao.deleteVentaById(venta.idVenta)
        } catch (e: Exception) {
            android.util.Log.e("VentaRepository", "Error eliminando jaula", e)
        }
    }

    /* ---------- Vaciar Jaula (PROC-17) ---------- */
    suspend fun vaciarJaula(ventaId: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            ventaDao.deleteDetallesByVenta(ventaId)
            actualizarTotalesVenta(ventaId)
        } catch (e: Exception) {
            android.util.Log.e("VentaRepository", "Error vaciando jaula", e)
        }
    }

    /* ---------- Eliminar Animal de Venta (PROC-18) ---------- */
    suspend fun eliminarAnimalDeVenta(detalle: DetalleVenta) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            ventaDao.deleteDetalle(detalle)
            actualizarTotalesVenta(detalle.idVenta)
        } catch (e: Exception) {
            android.util.Log.e("VentaRepository", "Error eliminando animal de venta", e)
        }
    }

    /* ---------- Agregar Animal a Venta/Jaula ---------- */
    suspend fun agregarAnimalAVenta(
        ventaId: Long,
        arete: String,
        pesoSalida: Double,
        montoCobro: Double
    ): ResultadoOperacion {
        val animal = animalDao.getByArete(arete) ?: return ResultadoOperacion.Error("Animal no encontrado")
        
        if (animal.fase == FaseGanado.DESARROLLO) {
            return ResultadoOperacion.Error("El animal no puede agregarse a la jaula ya que se encuentra en fase de Desarrollo")
        }

        // Falla 2: Validar si tiene solicitudes pendientes
        val ventaPendiente = ventaDao.getVentaPendienteDeAnimal(arete.trim())
        val muertePendiente = mortalidadDao.getMortalidadPendienteDeAnimal(arete.trim())
        if (ventaPendiente != null || muertePendiente != null) {
            return ResultadoOperacion.Error("El animal ya tiene una solicitud de venta o baja pendiente de revisión.")
        }

        val venta = ventaDao.getVentaById(ventaId) ?: return ResultadoOperacion.Error("Jaula no encontrada")

        // Reglas de Capacidad con tolerancia (PROC-20)
        // Límite: 110 + 5 = 115 cabezas | 24.5 + 0.5 = 25.0 toneladas
        if (venta.totalCabezas >= 115) {
            return ResultadoOperacion.Error("Capacidad máxima de la jaula alcanzada (115 cabezas).")
        }
        if ((venta.pesoTotal / 1000.0) >= 25.0) {
            return ResultadoOperacion.Error("Límite de peso de la jaula alcanzado (25.0 toneladas).")
        }

        // 1. Insertar el detalle
        val detalle = DetalleVenta(
            idVenta = ventaId,
            numeroArete = arete,
            pesoSalida = pesoSalida,
            precioUnitario = if (pesoSalida > 0) montoCobro / pesoSalida else 0.0,
            subtotal = montoCobro
        )
        ventaDao.insertDetalle(detalle)

        // 2. Actualizar el encabezado de la venta (Jaula)
        actualizarTotalesVenta(ventaId)

        return ResultadoOperacion.Ok
    }

    private suspend fun actualizarTotalesVenta(ventaId: Long) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val detalles = ventaDao.getDetallesPorVentaOnce(ventaId)
            val venta = ventaDao.getVentaById(ventaId)
            
            if (venta != null) {
                val totalCabezas = detalles.size
                val pesoTotal = detalles.sumOf { it.pesoSalida }
                val montoTotal = detalles.sumOf { it.subtotal }
                
                ventaDao.updateVenta(venta.copy(
                    totalCabezas = totalCabezas,
                    pesoTotal = pesoTotal,
                    montoTotal = montoTotal,
                    fechaActualizacion = System.currentTimeMillis()
                ))
            }
        } catch (e: Exception) {
            android.util.Log.e("VentaRepository", "Error actualizando totales", e)
        }
    }

    /* ---------- Consolidar Venta (Cerrar Jaula / Enviar a Revisión) ---------- */
    suspend fun consolidarVenta(ventaId: Long, idUsuario: Long, esAdmin: Boolean, deAprobaciones: Boolean = false): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        try {
            val venta = ventaDao.getVentaById(ventaId) ?: return@withContext ResultadoOperacion.Error("Jaula no encontrada")
            val detalles = ventaDao.getDetallesPorVentaOnce(ventaId)
            
            if (detalles.isEmpty()) {
                return@withContext ResultadoOperacion.Error("No se puede vender una jaula vacía")
            }

            if (esAdmin) {
                // Falla 3: Resolución de conflictos
                if (deAprobaciones) {
                    detalles.forEach { detalle ->
                        // Si estamos aprobando esta venta, eliminamos ESTE arete de cualquier OTRA solicitud pendiente
                        // 1. Otras ventas individuales
                        ventaDao.eliminarVentasIndividualesPendientesDeAnimal(detalle.numeroArete)
                        // 2. Otras jaulas pendientes
                        val otrasVentasIds = ventaDao.getIdsVentasDeAnimal(detalle.numeroArete).filter { it != ventaId }
                        ventaDao.eliminarAnimalDeVentasPendientes(detalle.numeroArete)
                        otrasVentasIds.forEach { id -> actualizarTotalesVenta(id) }
                        // 3. Mortalidades pendientes
                        mortalidadDao.eliminarMortalidadesPendientesDeAnimal(detalle.numeroArete)
                    }
                }

                // 1. Si es admin, aprueba de inmediato
                ventaDao.updateVenta(venta.copy(
                    estadoAprobacion = EstadoAprobacion.APROBADO,
                    finalizada = true,
                    idUsuarioAprobacion = idUsuario,
                    fechaAprobacion = System.currentTimeMillis(),
                    fechaActualizacion = System.currentTimeMillis()
                ))

                // 2. Marcar todos los animales como VENDIDOS en el inventario
                detalles.forEach { detalle ->
                    val animal = animalDao.getByArete(detalle.numeroArete)
                    if (animal != null) {
                        animalDao.update(animal.copy(
                            estadoInventario = EstadoInventario.VENDIDO,
                            fechaBaja = System.currentTimeMillis(),
                            tipoBaja = TipoBaja.VENTA,
                            fechaActualizacion = System.currentTimeMillis()
                        ))
                    }
                }
            } else {
                // 3. Si es empleado, solo marca como finalizada para revisión del admin
                ventaDao.updateVenta(venta.copy(
                    finalizada = true,
                    fechaActualizacion = System.currentTimeMillis()
                ))
            }

            ResultadoOperacion.Ok
        } catch (e: Exception) {
            android.util.Log.e("VentaRepository", "Error consolidando venta", e)
            ResultadoOperacion.Error("Error al procesar la venta: ${e.message}")
        }
    }

    suspend fun rechazarVenta(venta: Venta) = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        if (venta.modalidad == ModalidadVenta.INDIVIDUAL) {
            // Si es individual, simplemente eliminamos el registro para que no aparezca más.
            // Los animales siguen activos porque el proceso de baja oficial nunca se consolidó.
            ventaDao.deleteDetallesByVenta(venta.idVenta)
            ventaDao.deleteVentaById(venta.idVenta)
        } else {
            // Al rechazar una jaula, vuelve a estar "abierta" para el empleado
            ventaDao.updateVenta(venta.copy(
                finalizada = false,
                estadoAprobacion = EstadoAprobacion.PENDIENTE,
                fechaActualizacion = System.currentTimeMillis()
            ))
        }
    }

    suspend fun getDetalleVenta(arete: String): Pair<Venta, DetalleVenta>? = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        val detalle = ventaDao.getDetalleVentaPorArete(arete) ?: return@withContext null
        val venta = ventaDao.getVentaById(detalle.idVenta) ?: return@withContext null
        venta to detalle
    }

    suspend fun actualizarVentaArchivada(venta: Venta, detalle: DetalleVenta): ResultadoOperacion = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
        ventaDao.updateVenta(venta)
        ventaDao.updateDetalleVenta(detalle)
        actualizarTotalesVenta(venta.idVenta)
        ResultadoOperacion.Ok
    }

    fun getVentasPendientes(): Flow<List<Venta>> = ventaDao.getVentasPendientes()
}
