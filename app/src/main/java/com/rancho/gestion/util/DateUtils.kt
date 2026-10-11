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

    /** Último día (23:59:59.999) del mes que contiene [millis]. */
    fun finDeMes(millis: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = millis
        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        return cal.timeInMillis
    }

    /** Devuelve el rango [desde, hasta] de milisegundos para el periodo dado. */
    fun rangoPara(periodo: Periodo, ahora: Long = System.currentTimeMillis()): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.timeInMillis = ahora
        when (periodo) {
            Periodo.DIARIO -> {
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val desde = cal.timeInMillis
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                return desde to cal.timeInMillis
            }
            Periodo.SEMANAL -> {
                cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val desde = cal.timeInMillis
                cal.add(Calendar.DAY_OF_WEEK, 6)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                return desde to cal.timeInMillis
            }
            Periodo.MENSUAL -> return inicioDeMes(ahora) to finDeMes(ahora)
            Periodo.ANUAL -> {
                cal.set(Calendar.DAY_OF_YEAR, 1)
                cal.set(Calendar.HOUR_OF_DAY, 0); cal.set(Calendar.MINUTE, 0)
                cal.set(Calendar.SECOND, 0); cal.set(Calendar.MILLISECOND, 0)
                val desde = cal.timeInMillis
                cal.set(Calendar.MONTH, 11)
                cal.set(Calendar.DAY_OF_MONTH, 31)
                cal.set(Calendar.HOUR_OF_DAY, 23); cal.set(Calendar.MINUTE, 59)
                cal.set(Calendar.SECOND, 59); cal.set(Calendar.MILLISECOND, 999)
                return desde to cal.timeInMillis
            }
        }
    }

    fun añosAtras(anios: Int, desde: Long = System.currentTimeMillis()): Long {
        val cal = Calendar.getInstance()
        cal.timeInMillis = desde
        cal.add(Calendar.YEAR, -anios)
        return cal.timeInMillis
    }

    /** Rango para un día específico */
    fun rangoDiaEspecifico(anio: Int, mes: Int, dia: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(anio, mes, dia, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        cal.set(anio, mes, dia, 23, 59, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    /** Rango para una semana específica de un mes. La semana 1 es la que contiene el 1er día del mes. */
    fun rangoSemanaEspecifica(anio: Int, mes: Int, semanaDelMes: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, anio)
        cal.set(Calendar.MONTH, mes)
        cal.set(Calendar.WEEK_OF_MONTH, semanaDelMes)
        cal.set(Calendar.DAY_OF_WEEK, cal.firstDayOfWeek)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.add(Calendar.DAY_OF_WEEK, 6)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    /** Rango para un mes específico */
    fun rangoMesEspecifico(anio: Int, mes: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, anio)
        cal.set(Calendar.MONTH, mes)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.DAY_OF_MONTH, cal.getActualMaximum(Calendar.DAY_OF_MONTH))
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }

    /** Rango para un año completo específico */
    fun rangoAnioEspecifico(anio: Int): Pair<Long, Long> {
        val cal = Calendar.getInstance()
        cal.set(Calendar.YEAR, anio)
        cal.set(Calendar.MONTH, Calendar.JANUARY)
        cal.set(Calendar.DAY_OF_MONTH, 1)
        cal.set(Calendar.HOUR_OF_DAY, 0)
        cal.set(Calendar.MINUTE, 0)
        cal.set(Calendar.SECOND, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis

        cal.set(Calendar.MONTH, Calendar.DECEMBER)
        cal.set(Calendar.DAY_OF_MONTH, 31)
        cal.set(Calendar.HOUR_OF_DAY, 23)
        cal.set(Calendar.MINUTE, 59)
        cal.set(Calendar.SECOND, 59)
        cal.set(Calendar.MILLISECOND, 999)
        val end = cal.timeInMillis
        return start to end
    }
}
