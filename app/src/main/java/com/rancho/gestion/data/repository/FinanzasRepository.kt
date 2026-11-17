package com.rancho.gestion.data.repository

import com.rancho.gestion.data.dao.EgresoDao
import com.rancho.gestion.data.dao.SincronizacionDao
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.ColaSincronizacion
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.data.entities.NominaDetalle
import android.content.Context
import com.rancho.gestion.sync.SyncManager
import com.rancho.gestion.util.DateUtils
import kotlinx.coroutines.flow.Flow
import java.util.Calendar

class FinanzasRepository(
    private val egresoDao: EgresoDao, 
    private val sincronizacionDao: SincronizacionDao,
    private val context: Context? = null
) {

    fun porRango(desde: Long, hasta: Long): Flow<List<Egreso>> = egresoDao.getEgresosPorRango(desde, hasta)
    
    fun getPrimeraFechaEgreso(): Flow<Long?> = egresoDao.getPrimeraFechaEgreso()

    suspend fun porRangoOnce(desde: Long, hasta: Long): List<Egreso> = egresoDao.getEgresosPorRangoOnce(desde, hasta)

    fun totalPorRango(desde: Long, hasta: Long): Flow<Double> = egresoDao.getTotalPorRango(desde, hasta)

    fun totalPorCategoria(categoria: CategoriaEgreso, desde: Long, hasta: Long): Flow<Double> =
        egresoDao.getTotalPorCategoria(categoria, desde, hasta)

    suspend fun registrarEgreso(
        categoria: CategoriaEgreso,
        monto: Double,
        descripcion: String,
        idUsuario: String,
        empleadoNombre: String? = null,
        diasTrabajados: Int? = null
    ): ResultadoOperacion {
        if (monto <= 0.0) return ResultadoOperacion.Error("El monto debe ser mayor a cero")
        
        val egreso = Egreso(
            categoria = categoria,
            montoTotal = monto,
            descripcion = descripcion.trim(),
            idUsuario = idUsuario
        )
        
        egresoDao.insert(egreso)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "egresos",
                idEntidad = egreso.idEgreso,
                operacion = "INSERT",
                versionLocal = egreso.version,
                idDispositivo = idUsuario
            )
        )
        
        if (categoria == CategoriaEgreso.NOMINA && empleadoNombre != null && diasTrabajados != null) {
            val nomina = NominaDetalle(
                    idEgreso = egreso.idEgreso,
                    nombreEmpleado = empleadoNombre,
                    diasTrabajados = diasTrabajados
                )
            egresoDao.insertNomina(nomina)
            sincronizacionDao.insert(
                ColaSincronizacion(
                    entidad = "nomina",
                    idEntidad = nomina.idNomina,
                    operacion = "INSERT",
                    versionLocal = 1,
                    idDispositivo = idUsuario
                )
            )
        }
        
        context?.let { SyncManager.sincronizarAhora(it) }
        return ResultadoOperacion.Ok
    }

    suspend fun eliminarEgreso(egreso: Egreso): ResultadoOperacion {
        return try {
            egresoDao.delete(egreso)
            sincronizacionDao.insert(
                ColaSincronizacion(
                    entidad = "egresos",
                    idEntidad = egreso.idEgreso,
                    operacion = "DELETE",
                    versionLocal = egreso.version,
                    idDispositivo = egreso.idUsuario
                )
            )
            context?.let { SyncManager.sincronizarAhora(it) }
            ResultadoOperacion.Ok
        } catch (e: Exception) {
            ResultadoOperacion.Error("Error al eliminar egreso: ${e.message}")
        }
    }

    /* ---------- Validación de Fecha de Corte (PROC-04) ---------- */
    suspend fun puedeEditar(egreso: Egreso): Boolean {
        val cal = Calendar.getInstance()
        val mesActual = cal.get(Calendar.MONTH)
        val anioActual = cal.get(Calendar.YEAR)
        
        cal.timeInMillis = egreso.fechaEgreso
        return cal.get(Calendar.MONTH) == mesActual && cal.get(Calendar.YEAR) == anioActual
    }
}
