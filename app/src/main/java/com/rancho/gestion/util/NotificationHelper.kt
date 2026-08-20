package com.rancho.gestion.util

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.rancho.gestion.R

/**
 * Punto centralizado para el canal y el envío de notificaciones locales de
 * alerta (Acopio/Engorda vencida — ver domain/EtapaCalculator y
 * sync/AlertasWorker). Son notificaciones 100% locales, disparadas por un
 * Worker periódico que revisa la base de datos local: no requieren backend
 * ni conexión a internet.
 */
object NotificationHelper {
    const val CANAL_ALERTAS_ID = "alertas_ganado"
    private const val NOTIFICACION_ALERTAS_ID = 1001

    /** Crear el canal es obligatorio en Android 8+ (API 26+); se llama una vez en RanchoApp.onCreate. */
    fun crearCanalDeAlertas(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val canal = NotificationChannel(
                CANAL_ALERTAS_ID,
                context.getString(R.string.canal_alertas_nombre),
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                description = context.getString(R.string.canal_alertas_descripcion)
            }
            val manager = context.getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(canal)
        }
    }

    /** Muestra una notificación resumen si [cantidad] > 0. No hace nada si el permiso no fue concedido (Android 13+). */
    fun notificarAlertasDeEtapa(context: Context, cantidad: Int) {
        if (cantidad <= 0) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val concedido = ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!concedido) return
        }

        val titulo = if (cantidad == 1) "1 animal necesita atención" else "$cantidad animales necesitan atención"
        val texto = "Hay animales cerca de vencer (o que ya vencieron) su etapa de Acopio o Engorda. Revisa el Panel."

        val notificacion = NotificationCompat.Builder(context, CANAL_ALERTAS_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(titulo)
            .setContentText(texto)
            .setStyle(NotificationCompat.BigTextStyle().bigText(texto))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setAutoCancel(true)
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICACION_ALERTAS_ID, notificacion)
    }
}
