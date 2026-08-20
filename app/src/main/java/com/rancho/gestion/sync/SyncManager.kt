package com.rancho.gestion.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.rancho.gestion.data.AppDatabase
import com.rancho.gestion.data.entities.Animal
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

        val request = PeriodicWorkRequestBuilder<SyncWorker>(30, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()

        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_SYNC,
            ExistingPeriodicWorkPolicy.KEEP,
            request
        )
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

class SyncWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            // Lógica de sincronización offline-to-cloud (PROC-05)
            Result.success()
        } catch (e: Exception) {
            Result.retry()
        }
    }
}

class MaintenanceWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        return try {
            val db = AppDatabase.getInstance(applicationContext)
            val limite = DateUtils.añosAtras(3)
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
