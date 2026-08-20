package com.rancho.gestion.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class Periodo { DIARIO, SEMANAL, MENSUAL, ANUAL }

/**
 * Utilidades de fecha para reportes. La nota del boceto pide que la fecha
 * de corte de los reportes sea siempre el día 1 de cada mes.
 */
object DateUtils {
    private val dayFormatter = SimpleDateFormat("dd/MM/yyyy", Locale("es", "MX"))
    private val monthFormatter = SimpleDateFormat("MMMM yyyy", Locale("es", "MX"))

    fun formatDay(millis: Long): String = dayFormatter.format(Date(millis))
    fun formatMonth(millis: Long): String = monthFormatter.format(Date(millis))

    /** Primer día (00:00) del mes que contiene [millis] — fecha de corte. */
    fun inicioDeMes(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        return cal.timeInMillis
    }

    /** Devuelve el rango [desde, hasta] de milisegundos para el periodo dado, terminando "ahora". */
    fun rangoPara(periodo: Periodo, ahora: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ahora
        when (periodo) {
            Periodo.DIARIO -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            }
            Periodo.SEMANAL -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            }
            Periodo.MENSUAL -> return inicioDeMes(ahora) to ahora
            Periodo.ANUAL -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
            }
        }
        return cal.timeInMillis to ahora
    }

    fun añosAtras(anios: Int, desde: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = desde
        cal.add(Calendar.YEAR, -anios)
        return cal.timeInMillis
    }
}
