package com.rancho.gestion.data.repository

import android.content.Context
import com.rancho.gestion.data.dao.ConfiguracionDao
import com.rancho.gestion.data.dao.RangoPesoDao
import com.rancho.gestion.data.dao.SincronizacionDao
import com.rancho.gestion.data.entities.ColaSincronizacion
import com.rancho.gestion.data.entities.Configuracion
import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.sync.SyncManager
import kotlinx.coroutines.flow.Flow

class ConfiguracionRepository(
    private val rangoPesoDao: RangoPesoDao,
    private val configuracionDao: ConfiguracionDao,
    private val sincronizacionDao: SincronizacionDao,
    private val context: Context? = null
) {
    private fun triggerSync() = context?.let { SyncManager.sincronizarAhora(it) }

    fun rangos(): Flow<List<RangoPeso>> = rangoPesoDao.getAll()

    suspend fun agregarRango(nombre: String, min: Double, max: Double): ResultadoOperacion {
        if (min >= max) return ResultadoOperacion.Error("El mínimo debe ser menor al máximo")
        
        val rango = RangoPeso(nombreRango = nombre, limiteInferior = min, limiteSuperior = max)
        rangoPesoDao.insert(rango)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "rangos_peso",
                idEntidad = rango.idRangoPeso,
                operacion = "INSERT",
                versionLocal = rango.version,
                idDispositivo = "sistema"
            )
        )
        triggerSync()
        return ResultadoOperacion.Ok
    }

    suspend fun eliminarRango(rango: RangoPeso) {
        rangoPesoDao.delete(rango)
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "rangos_peso",
                idEntidad = rango.idRangoPeso,
                operacion = "DELETE",
                versionLocal = rango.version,
                idDispositivo = "sistema"
            )
        )
        triggerSync()
    }
    
    fun getConfiguraciones(): Flow<List<Configuracion>> = configuracionDao.getAll()
}
