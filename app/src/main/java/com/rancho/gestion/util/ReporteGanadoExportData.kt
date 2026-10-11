package com.rancho.gestion.util

import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.ui.reportes.ReporteGanadoRow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class ReporteGanadoExportData(
    val nombreRancho: String = "Rancho San Martín",
    val fechaGeneracionTxt: String,
    val periodoTxt: String,
    val filtrosAplicadosTxt: String,
    val totalRegistros: Int,
    val stats: ReporteGanadoStats,
    val tablaRows: List<ReporteGanadoRow>,
    val rangosPesoList: List<RangoPeso>
)

object ReporteGanadoExportDataBuilder {
    fun preparar(
        nombreRancho: String = "Rancho San Martín",
        periodoTxt: String,
        filtrosAplicadosTxt: String,
        stats: ReporteGanadoStats,
        tablaRows: List<ReporteGanadoRow>,
        rangos: List<RangoPeso>
    ): ReporteGanadoExportData {
        val sdf = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
        return ReporteGanadoExportData(
            nombreRancho = nombreRancho,
            fechaGeneracionTxt = sdf.format(Date()),
            periodoTxt = periodoTxt,
            filtrosAplicadosTxt = filtrosAplicadosTxt,
            totalRegistros = tablaRows.size,
            stats = stats,
            tablaRows = tablaRows,
            rangosPesoList = rangos
        )
    }
}
