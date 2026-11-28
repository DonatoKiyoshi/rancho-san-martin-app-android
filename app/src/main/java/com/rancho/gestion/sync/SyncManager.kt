package com.rancho.gestion.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rancho.gestion.data.AppDatabase
import com.rancho.gestion.data.entities.Animal
import com.rancho.gestion.data.entities.ColaSincronizacion
import com.rancho.gestion.data.entities.EstadoSincronizacion
import com.rancho.gestion.domain.EtapaCalculator
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.NotificationHelper
import kotlinx.coroutines.flow.first
import java.util.concurrent.TimeUnit

object SyncManager {
    private const val WORK_SYNC = "rancho_sync_work"
    private const val WORK_MANTENIMIENTO = "rancho_mantenimiento_work"
    private const val WORK_ALERTAS = "rancho_alertas_work"

    fun programarSincronizacionPeriodica(context: Context) {
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()

        val request = PeriodicWorkRequestBuilder<com.rancho.gestion.data.sync.SyncWorker>(15, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_SYNC,
            ExistingPeriodicWorkPolicy.UPDATE, // NUEVO: Obliga a Android a reemplazar el Worker viejo
            request
        )
        
        // NUEVO: Lanzar una petición de una sola vez inmediatamente para que no haya que esperar 15 min en la primera prueba
        val immediateRequest = OneTimeWorkRequestBuilder<com.rancho.gestion.data.sync.SyncWorker>()
            .setConstraints(constraints)
            .build()
            
        WorkManager.getInstance(context).enqueueUniqueWork(
            WORK_SYNC + "_immediate",
            androidx.work.ExistingWorkPolicy.REPLACE,
            immediateRequest
        )
    }

    fun sincronizarAhora(context: Context) {
        val request = OneTimeWorkRequestBuilder<com.rancho.gestion.data.sync.SyncWorker>().build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun programarMantenimientoPeriodico(context: Context) {
        val request = PeriodicWorkRequestBuilder<MaintenanceWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_MANTENIMIENTO,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }

    fun programarAlertasPeriodicas(context: Context) {
        val request = PeriodicWorkRequestBuilder<AlertasWorker>(24, TimeUnit.HOURS).build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_ALERTAS,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
    }
}

// Eliminar el SyncWorker antiguo vacío


class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val limite = DateUtils.añosAtras(3)
            
            // 1. Obtener los IDs de los animales que van a ser purgados
            val idsParaPurgar = db.animalDao().getAntiguosIds(limite)
            
            // 2. Registrar cada eliminación en la cola de sincronización para que se borren en Supabase
            val syncDao = db.sincronizacionDao()
            val ahora = System.currentTimeMillis()
            idsParaPurgar.forEach { id ->
                syncDao.insert(
                    ColaSincronizacion(
                        entidad = "ganado",
                        idEntidad = id,
                        operacion = "DELETE",
                        estado = EstadoSincronizacion.PENDIENTE,
                        versionLocal = 1,
                        fechaOperacion = ahora,
                        idDispositivo = "SYSTEM_MAINTENANCE"
                    )
                )
            }
            
            // 3. Eliminar los registros de la base de datos local
            db.animalDao().purgarAntiguos(limite)
            
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class AlertasWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val activos: List<Animal> = db.animalDao().getActivos().first()
            val ahora = System.currentTimeMillis()
            val enAlerta = activos.count { animal -> 
                EtapaCalculator.calcular(animal.fechaEntrada, ahora).alerta 
            }
            NotificationHelper.notificarAlertasDeEtapa(applicationContext, enAlerta)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}
