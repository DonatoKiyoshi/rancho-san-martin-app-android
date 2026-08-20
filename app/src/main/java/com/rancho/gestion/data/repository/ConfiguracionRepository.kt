package com.rancho.gestion.data.repository

import com.rancho.gestion.data.dao.ConfiguracionDao
import com.rancho.gestion.data.dao.RangoPesoDao
import com.rancho.gestion.data.entities.Configuracion
import com.rancho.gestion.data.entities.RangoPeso
import kotlinx.coroutines.flow.Flow

class ConfiguracionRepository(
    private val rangoPesoDao: RangoPesoDao,
    private val configuracionDao: ConfiguracionDao
) {
    fun rangos(): Flow<List<RangoPeso>> = rangoPesoDao.getAll()

    suspend fun agregarRango(nombre: String, min: Double, max: Double): ResultadoOperacion {
        if (min >= max) return ResultadoOperacion.Error("El mínimo debe ser menor al máximo")
        rangoPesoDao.insert(RangoPeso(nombreRango = nombre, limiteInferior = min, limiteSuperior = max))
        return ResultadoOperacion.Ok
    }

    suspend fun eliminarRango(rango: RangoPeso) = rangoPesoDao.delete(rango)
    
    fun getConfiguraciones(): Flow<List<Configuracion>> = configuracionDao.getAll()
}
