package com.rancho.gestion.domain

import com.rancho.gestion.data.entities.RangoPeso
import java.util.concurrent.TimeUnit

/* =======================================================================
 *  CLASIFICACION AUTOMATICA POR PESO
 * ======================================================================= */
object ClasificadorPeso {
    /** Devuelve el rango configurado (Configuracion) al que pertenece [peso]. */
    fun clasificar(peso: Double, rangos: List<RangoPeso>): RangoPeso? =
        rangos.firstOrNull { peso in it.limiteInferior..it.limiteSuperior }

    fun etiqueta(rango: RangoPeso?): String =
        if (rango == null) "Sin clasificar" else "${rango.limiteInferior.toInt()}-${rango.limiteSuperior.toInt()} kg"
}

/* =======================================================================
 *  ETAPAS Y TIEMPOS (Acopio / Desarrollo y Engorda)
 * ======================================================================= */
enum class Etapa { ACOPIO, DESARROLLO_ENGORDA, LISTO_VENTA }

data class EstadoEtapa(
    val etapa: Etapa,
    val diasEnRancho: Long,
    val alerta: Boolean,
    val mensaje: String
)

object EtapaCalculator {
    private const val ACOPIO_MAX_DIAS = 15L
    private const val ENGORDA_MIN_DIAS = 60L
    private const val ENGORDA_MAX_DIAS = 75L

    fun calcular(fechaEntrada: Long, ahora: Long = System.currentTimeMillis()): EstadoEtapa {
        val diff = ahora - fechaEntrada
        val dias = if (diff < 0) 0L else TimeUnit.MILLISECONDS.toDays(diff)
        
        return when {
            dias <= ACOPIO_MAX_DIAS -> EstadoEtapa(
                etapa = Etapa.ACOPIO,
                diasEnRancho = dias,
                alerta = false,
                mensaje = "Acopio: día $dias de máx. $ACOPIO_MAX_DIAS"
            )
            dias <= ENGORDA_MAX_DIAS -> EstadoEtapa(
                etapa = Etapa.DESARROLLO_ENGORDA,
                diasEnRancho = dias,
                alerta = dias > ENGORDA_MIN_DIAS,
                mensaje = if (dias > ENGORDA_MIN_DIAS)
                    "Desarrollo y Engorda: día $dias, cerca del límite ($ENGORDA_MAX_DIAS d)"
                else "Desarrollo y Engorda: día $dias (rango $ENGORDA_MIN_DIAS-$ENGORDA_MAX_DIAS)"
            )
            else -> EstadoEtapa(
                etapa = Etapa.LISTO_VENTA,
                diasEnRancho = dias,
                alerta = true,
                mensaje = "Listo para venta: $dias días en el rancho (superó $ENGORDA_MAX_DIAS d)"
            )
        }
    }
}

/* =======================================================================
 *  PERDIDA FINANCIERA POR MUERTE (MERMA)
 * ======================================================================= */
object MermaCalculator {
    /**
     * perdida neta = precio de compra - valor recuperado (p.ej. venta de
     * restos al rastro). Ejemplo del boceto: costo $20,000, recuperado
     * $8,000 => pérdida neta $12,000.
     */
    fun calcularPerdida(precioCompra: Double, valorRecuperado: Double): Double =
        (precioCompra - valorRecuperado).coerceAtLeast(0.0)
}

/* =======================================================================
 *  CAPACIDAD DE JAULAS (LOTES)
 * ======================================================================= */
object JaulaCapacidad {
    fun toneladas(pesoTotalKg: Double): Double = pesoTotalKg / 1000.0

    fun estaCompleta(
        cabezasActuales: Int,
        toneladasActuales: Double,
        capacidadCabezas: Int,
        capacidadToneladas: Double,
        tolerancia: Double
    ): Boolean =
        cabezasActuales >= capacidadCabezas ||
            toneladasActuales >= (capacidadToneladas + tolerancia)

    fun disponible(
        cabezasActuales: Int,
        toneladasActuales: Double,
        capacidadCabezas: Int,
        capacidadToneladas: Double,
        tolerancia: Double
    ): Pair<Int, Double> {
        val cabezasDisponibles = (capacidadCabezas - cabezasActuales).coerceAtLeast(0)
        val toneladasDisponibles =
            ((capacidadToneladas + tolerancia) - toneladasActuales).coerceAtLeast(0.0)
        return cabezasDisponibles to toneladasDisponibles
    }
}
