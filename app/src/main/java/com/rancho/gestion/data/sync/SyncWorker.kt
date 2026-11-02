package com.rancho.gestion.data.sync

import android.content.Context
import android.util.Log
import androidx.room.Room
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.rancho.gestion.data.AppDatabase
import com.rancho.gestion.data.entities.EstadoSincronizacion
import com.rancho.gestion.data.network.SupabaseApi
import com.rancho.gestion.data.network.SupabaseClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        Log.d("SyncWorker", "Iniciando sincronización con Supabase...")

        // Usar EXACTAMENTE el mismo nombre y configuración que en AppContainer / AppDatabase
        val database = Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "rancho_final_v9.db"
        )
        .fallbackToDestructiveMigration()
        .build()

        val syncDao = database.sincronizacionDao()
        val api = SupabaseClient.retrofit.create(SupabaseApi::class.java)

        val pendientes = syncDao.getPendientes()
        if (pendientes.isEmpty()) {
            Log.d("SyncWorker", "No hay datos pendientes para sincronizar.")
            return@withContext Result.success()
        }

        var algunError = false

        for (item in pendientes) {
            try {
                // Cambiar estado a ENVIANDO
                syncDao.update(item.copy(estado = EstadoSincronizacion.ENVIANDO))
                
                val eqParam = "eq.${item.idEntidad}"

                val response = when (item.entidad) {
                    "ganado" -> {
                        val dao = database.animalDao()
                        val data = dao.getByArete(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertAnimal(it) }
                            "UPDATE" -> data?.let { api.updateAnimal(eqParam, it) }
                            "DELETE" -> api.deleteAnimal(eqParam)
                            else -> null
                        }
                    }
                    "ventas" -> {
                        val dao = database.ventaDao()
                        val data = dao.getVentaById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertVenta(it) }
                            "UPDATE" -> data?.let { api.updateVenta(eqParam, it) }
                            "DELETE" -> api.deleteVenta(eqParam)
                            else -> null
                        }
                    }
                    "egresos" -> {
                        val dao = database.egresoDao()
                        val data = dao.getEgresoById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertEgreso(it) }
                            "UPDATE" -> data?.let { api.updateEgreso(eqParam, it) }
                            "DELETE" -> api.deleteEgreso(eqParam)
                            else -> null
                        }
                    }
                    "mortalidad" -> {
                        val dao = database.mortalidadDao()
                        val data = dao.getMortalidadById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertMortalidad(it) }
                            "UPDATE" -> data?.let { api.updateMortalidad(eqParam, it) }
                            "DELETE" -> api.deleteMortalidad(eqParam)
                            else -> null
                        }
                    }
                    "usuarios" -> {
                        val dao = database.usuarioDao()
                        val data = dao.getById(item.idEntidad)
                        when (item.operacion) {
                            "INSERT" -> data?.let { api.insertUsuario(it) }
                            "UPDATE" -> data?.let { api.updateUsuario(eqParam, it) }
                            "DELETE" -> api.deleteUsuario(eqParam)
                            else -> null
                        }
                    }
                    "rangos_peso" -> {
                         // Lógica similar para rangos_peso
                         null
                    }
                    else -> null
                }

                if (response != null && response.isSuccessful) {
                    Log.d("SyncWorker", "ÉXITO al enviar ${item.entidad} (ID: ${item.idEntidad})")
                    syncDao.update(
                        item.copy(
                            estado = EstadoSincronizacion.SINCRONIZADO,
                            fechaSincronizacion = System.currentTimeMillis()
                        )
                    )
                } else if (response != null) {
                    algunError = true
                    val errorMsg = response.errorBody()?.string() ?: "Error HTTP ${response.code()}"
                    Log.e("SyncWorker", "SUPABASE RECHAZÓ EL DATO. Entidad: ${item.entidad}. Error: $errorMsg")
                    syncDao.update(
                        item.copy(
                            estado = EstadoSincronizacion.ERROR,
                            ultimoError = errorMsg,
                            numeroIntentos = item.numeroIntentos + 1
                        )
                    )
                } else {
                     // Response null (por ejemplo si no implementamos esa entidad aún)
                     syncDao.update(
                        item.copy(
                            estado = EstadoSincronizacion.SINCRONIZADO,
                            fechaSincronizacion = System.currentTimeMillis()
                        )
                    )
                }
            } catch (e: Exception) {
                Log.e("SyncWorker", "Error al sincronizar id: ${item.idEntidad}", e)
                algunError = true
                syncDao.update(
                    item.copy(
                        estado = EstadoSincronizacion.ERROR,
                        ultimoError = e.message ?: "Excepción desconocida",
                        numeroIntentos = item.numeroIntentos + 1
                    )
                )
            }
        }

        // --- PURGA DE REGISTROS ANTIGUOS EN LA COLA ---
        // Después de sincronizar, borramos los registros que ya se sincronizaron con éxito
        // hace más de 30 días para evitar que la base local crezca infinitamente.
        try {
            val limiteDias = 30L
            val tiempoLimiteMs = System.currentTimeMillis() - (limiteDias * 24 * 60 * 60 * 1000)
            
            syncDao.limpiarCola(tiempoLimiteMs)
            
            Log.d("SyncWorker", "Purga de historial de sincronización completada.")
        } catch (e: Exception) {
            Log.e("SyncWorker", "Error al purgar historial de sincronización", e)
        }

        database.close()

        if (algunError) {
            return@withContext Result.retry()
        }
        
        Log.d("SyncWorker", "Sincronización completada exitosamente.")
        return@withContext Result.success()
    }
}
