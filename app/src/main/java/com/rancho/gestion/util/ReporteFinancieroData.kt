package com.rancho.gestion.util

import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.ui.finanzas.categoriaLabel
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.NumberFormat
import java.util.Locale

data class CategoriaResumen(
    val categoria: CategoriaEgreso,
    val label: String,
    val total: BigDecimal,
    val porcentaje: BigDecimal,
    val cantidadMovimientos: Int
)

data class ReporteFinancieroData(
    val desde: Long,
    val hasta: Long,
    val filtroCategoriasTexto: String,
    val incluirDescripcion: Boolean,
    val egresos: List<Egreso>,
    val totalGeneral: BigDecimal,
    val totalMovimientos: Int,
    val resumenCategorias: List<CategoriaResumen>
) {
    val currencyFormatter: NumberFormat = NumberFormat.getCurrencyInstance(Locale("es", "MX")).apply {
        maximumFractionDigits = 2
        minimumFractionDigits = 2
    }

    fun formatTotalGeneral(): String = currencyFormatter.format(totalGeneral)

    companion object {
        fun preparar(
            egresos: List<Egreso>,
            desde: Long,
            hasta: Long,
            filtroCategoriasTexto: String,
            incluirDescripcion: Boolean
        ): ReporteFinancieroData {
            val totalBD = egresos.fold(BigDecimal.ZERO) { acc, item ->
                acc.add(BigDecimal.valueOf(item.montoTotal))
            }.setScale(2, RoundingMode.HALF_UP)

            val resumen = CategoriaEgreso.values().map { cat ->
                val deCat = egresos.filter { it.categoria == cat }
                val catTotal = deCat.fold(BigDecimal.ZERO) { acc, item ->
                    acc.add(BigDecimal.valueOf(item.montoTotal))
                }.setScale(2, RoundingMode.HALF_UP)

                val pct = if (totalBD.compareTo(BigDecimal.ZERO) > 0) {
                    catTotal.multiply(BigDecimal(100)).divide(totalBD, 2, RoundingMode.HALF_UP)
                } else {
                    BigDecimal.ZERO
                }

                CategoriaResumen(
                    categoria = cat,
                    label = categoriaLabel(cat),
                    total = catTotal,
                    porcentaje = pct,
                    cantidadMovimientos = deCat.size
                )
            }.filter { it.total.compareTo(BigDecimal.ZERO) > 0 }
                .sortedByDescending { it.total }

            return ReporteFinancieroData(
                desde = desde,
                hasta = hasta,
                filtroCategoriasTexto = filtroCategoriasTexto,
                incluirDescripcion = incluirDescripcion,
                egresos = egresos,
                totalGeneral = totalBD,
                totalMovimientos = egresos.size,
                resumenCategorias = resumen
            )
        }
    }
}
