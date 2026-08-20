package com.rancho.gestion

import android.app.Application
import com.rancho.gestion.sync.SyncManager
import com.rancho.gestion.util.NotificationHelper

class RanchoApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        NotificationHelper.crearCanalDeAlertas(this)

        // Programa la sincronización con la nube en cuanto haya conexión
        // (ver com.rancho.gestion.sync.SyncManager para el punto de extensión).
        SyncManager.programarSincronizacionPeriodica(this)
        // Purga diaria del archivo histórico (> 3 años).
        SyncManager.programarMantenimientoPeriodico(this)
        // Revisión diaria de alertas de Acopio/Engorda con notificación local.
        SyncManager.programarAlertasPeriodicas(this)
    }
}
