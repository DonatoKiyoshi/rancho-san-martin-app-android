package com.rancho.gestion.util

import androidx.compose.ui.graphics.Color
import com.rancho.gestion.data.entities.Animal
import com.rancho.gestion.data.entities.EstadoInventario
import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.entities.TipoArete

data class SexoStat(
    val sexo: Sexo,
    val label: String,
    val cantidad: Int,
    val porcentaje: Double,
    val color: Color
)

data class EstadoStat(
    val estado: EstadoInventario,
    val label: String,
    val cantidad: Int,
    val porcentaje: Double,
    val color: Color
)

data class RangoStat(
    val idRangoPeso: Long?,
    val nombreRango: String,
    val cantidad: Int,
    val porcentaje: Double
)

data class TipoAreteStat(
    val tipoArete: TipoArete,
    val label: String,
    val cantidad: Int,
    val porcentaje: Double
)

data class ReporteGanadoStats(
    val totalAnimales: Int,
    val pesoTotal: Double,
    val pesoPromedio: Double,
    val sinPesoRegistradoCount: Int,
    val totalActivos: Int,
    val pctActivos: Double,
    val totalVendidos: Int,
    val pctVendidos: Double,
    val totalMuertos: Int,
    val pctMuertos: Double,
    val distribucionSexo: List<SexoStat>,
    val distribucionEstado: List<EstadoStat>,
    val distribucionRangos: List<RangoStat>,
    val distribucionTipoArete: List<TipoAreteStat>
)

object ReporteGanadoDataCalculator {

    fun calcular(
        animales: List<Animal>,
        rangos: List<RangoPeso>
    ): ReporteGanadoStats {
        val total = animales.size
        val totalD = if (total > 0) total.toDouble() else 1.0

        val animalesConPeso = animales.filter { it.pesoEntrada > 0.0 }
        val sinPesoCount = total - animalesConPeso.size
        val pesoTotal = animalesConPeso.sumOf { it.pesoEntrada }
        val pesoPromedio = if (animalesConPeso.isNotEmpty()) pesoTotal / animalesConPeso.size else 0.0

        val activos = animales.count { it.estadoInventario == EstadoInventario.ACTIVO }
        val vendidos = animales.count { it.estadoInventario == EstadoInventario.VENDIDO }
        val muertos = animales.count { it.estadoInventario == EstadoInventario.MUERTO }

        val pctActivos = if (total > 0) (activos.toDouble() / totalD) * 100.0 else 0.0
        val pctVendidos = if (total > 0) (vendidos.toDouble() / totalD) * 100.0 else 0.0
        val pctMuertos = if (total > 0) (muertos.toDouble() / totalD) * 100.0 else 0.0

        // Sexo Stats
        val machos = animales.count { it.sexo == Sexo.MACHO }
        val hembras = animales.count { it.sexo == Sexo.HEMBRA }

        val distribucionSexo = listOf(
            SexoStat(Sexo.MACHO, "Machos", machos, if (total > 0) (machos.toDouble() / totalD) * 100.0 else 0.0, Color(0xFF31566B)),
            SexoStat(Sexo.HEMBRA, "Hembras", hembras, if (total > 0) (hembras.toDouble() / totalD) * 100.0 else 0.0, Color(0xFFD35400))
        )

        // Estado Stats
        val distribucionEstado = listOf(
            EstadoStat(EstadoInventario.ACTIVO, "Activos", activos, pctActivos, Color(0xFF3D7A54)),
            EstadoStat(EstadoInventario.VENDIDO, "Vendidos", vendidos, pctVendidos, Color(0xFF31566B)),
            EstadoStat(EstadoInventario.MUERTO, "Muertes", muertos, pctMuertos, Color(0xFFB3261E))
        )

        // Rangos Stats
        val listRangosStats = rangos.map { rango ->
            val cant = animales.count { animal ->
                animal.pesoEntrada >= rango.limiteInferior && animal.pesoEntrada <= rango.limiteSuperior
            }
            RangoStat(rango.idRangoPeso, rango.nombreRango, cant, if (total > 0) (cant.toDouble() / totalD) * 100.0 else 0.0)
        }.toMutableList()

        val sinRangoCount = animales.count { animal ->
            rangos.none { rango -> animal.pesoEntrada >= rango.limiteInferior && animal.pesoEntrada <= rango.limiteSuperior }
        }

        if (sinRangoCount > 0) {
            listRangosStats.add(
                RangoStat(null, "Sin Rango Definido", sinRangoCount, if (total > 0) (sinRangoCount.toDouble() / totalD) * 100.0 else 0.0)
            )
        }

        // Tipo Arete Stats
        val sader = animales.count { it.tipoArete == TipoArete.SADER }
        val otro = animales.count { it.tipoArete == TipoArete.OTRO }

        val distribucionTipoArete = listOf(
            TipoAreteStat(TipoArete.SADER, "SADER", sader, if (total > 0) (sader.toDouble() / totalD) * 100.0 else 0.0),
            TipoAreteStat(TipoArete.OTRO, "Genérico / Otro", otro, if (total > 0) (otro.toDouble() / totalD) * 100.0 else 0.0)
        )

        return ReporteGanadoStats(
            totalAnimales = total,
            pesoTotal = pesoTotal,
            pesoPromedio = pesoPromedio,
            sinPesoRegistradoCount = sinPesoCount,
            totalActivos = activos,
            pctActivos = pctActivos,
            totalVendidos = vendidos,
            pctVendidos = pctVendidos,
            totalMuertos = muertos,
            pctMuertos = pctMuertos,
            distribucionSexo = distribucionSexo,
            distribucionEstado = distribucionEstado,
            distribucionRangos = listRangosStats,
            distribucionTipoArete = distribucionTipoArete
        )
    }
}
