package com.rancho.gestion.util

import android.content.Context
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xddf.usermodel.chart.*
import org.apache.poi.xssf.usermodel.XSSFClientAnchor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object ExcelGanadoGenerator {

    fun generateExcel(
        context: Context?,
        file: File,
        exportData: ReporteGanadoExportData
    ): Boolean {
        return try {
            val workbook = XSSFWorkbook()

            val styles = createStyles(workbook)

            // 1. HOJA DE REGISTROS (DETALLE COMPLETO)
            buildRegistrosSheet(workbook, exportData, styles)

            // 2. HOJA DE ANÁLISIS (TABLAS ORIGEN PARA LOS GRÁFICOS)
            val (rangeRango, rangeEstado, rangeSexo, rangeArete) = buildAnalisisSheet(workbook, exportData, styles)

            // 3. HOJA DE RESUMEN (KPIS + 4 GRÁFICOS NATIVOS DE EXCEL INCRUSTADOS)
            buildResumenSheet(workbook, exportData, styles, rangeRango, rangeEstado, rangeSexo, rangeArete)

            // 4. HOJA DE CATÁLOGOS Y CRITERIOS
            buildCatalogosSheet(workbook, exportData, styles)

            // Reordenar las hojas para que Resumen sea la primera
            workbook.setSheetOrder("Resumen", 0)
            workbook.setSheetOrder("Registros", 1)
            workbook.setSheetOrder("Análisis", 2)
            workbook.setSheetOrder("Catálogos y Criterios", 3)

            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ExcelGanadoGenerator", "Error al crear Excel de Ganado", e)
            false
        }
    }

    private fun Cell.setStyle(style: CellStyle?) {
        if (style != null) cellStyle = style
    }

    private fun createStyles(wb: XSSFWorkbook): Map<String, CellStyle> {
        val styles = mutableMapOf<String, CellStyle>()

        val fontTitle = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 16.toShort()
            color = IndexedColors.DARK_BLUE.index
        }
        val fontSubtitle = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 12.toShort()
            color = IndexedColors.GREY_80_PERCENT.index
        }
        val fontHeader = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 10.toShort()
            color = IndexedColors.WHITE.index
        }

        val styleTitle = wb.createCellStyle().apply { setFont(fontTitle) }
        val styleSubtitle = wb.createCellStyle().apply { setFont(fontSubtitle) }

        val styleHeader = wb.createCellStyle().apply {
            setFont(fontHeader)
            fillForegroundColor = IndexedColors.DARK_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }

        val styleSubHeader = wb.createCellStyle().apply {
            setFont(fontHeader)
            fillForegroundColor = IndexedColors.CORNFLOWER_BLUE.index
            fillPattern = FillPatternType.SOLID_FOREGROUND
            alignment = HorizontalAlignment.LEFT
            verticalAlignment = VerticalAlignment.CENTER
        }

        val styleCurrency = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("$#,##0.00")
            alignment = HorizontalAlignment.RIGHT
        }

        val styleNumber = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0")
            alignment = HorizontalAlignment.RIGHT
        }

        val styleDecimal = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("#,##0.0")
            alignment = HorizontalAlignment.RIGHT
        }

        val stylePercentage = wb.createCellStyle().apply {
            dataFormat = wb.createDataFormat().getFormat("0.0%")
            alignment = HorizontalAlignment.RIGHT
        }

        val styleText = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.LEFT
        }

        val styleCenter = wb.createCellStyle().apply {
            alignment = HorizontalAlignment.CENTER
        }

        styles["title"] = styleTitle
        styles["subtitle"] = styleSubtitle
        styles["header"] = styleHeader
        styles["subHeader"] = styleSubHeader
        styles["currency"] = styleCurrency
        styles["number"] = styleNumber
        styles["decimal"] = styleDecimal
        styles["percentage"] = stylePercentage
        styles["text"] = styleText
        styles["center"] = styleCenter

        return styles
    }

    // --- HOJA 1: RESUMEN (KPIS + 4 GRÁFICOS EXCEL INCRUSTADOS) ---
    private fun buildResumenSheet(
        wb: XSSFWorkbook,
        data: ReporteGanadoExportData,
        styles: Map<String, CellStyle>,
        rangeRango: ChartRanges,
        rangeEstado: ChartRanges,
        rangeSexo: ChartRanges,
        rangeArete: ChartRanges
    ) {
        val sheet = wb.createSheet("Resumen") as XSSFSheet
        sheet.isDisplayGridlines = true

        val stats = data.stats
        val locale = Locale.getDefault()

        // Encabezados
        val row0 = sheet.createRow(0)
        row0.createCell(0).apply { setCellValue(data.nombreRancho); setStyle(styles["title"]) }

        val row1 = sheet.createRow(1)
        row1.createCell(0).apply { setCellValue("Reporte de Ganado e Inventario — Resumen Ejecutivo"); setStyle(styles["subtitle"]) }

        val row2 = sheet.createRow(2)
        row2.createCell(0).apply { setCellValue("Generado: ${data.fechaGeneracionTxt}  |  Periodo: ${data.periodoTxt}"); setStyle(styles["text"]) }

        val row3 = sheet.createRow(3)
        row3.createCell(0).apply { setCellValue("FILTRO: ${data.filtrosAplicadosTxt}"); setStyle(styles["text"]) }

        // Bloque KPIS
        val row5 = sheet.createRow(5)
        row5.createCell(0).apply { setCellValue("MÉTRICA DE INVENTARIO"); setStyle(styles["header"]) }
        row5.createCell(1).apply { setCellValue("VALOR"); setStyle(styles["header"]) }
        row5.createCell(2).apply { setCellValue("UNIDAD / ACLARACIÓN"); setStyle(styles["header"]) }

        val kpis = listOf(
            Triple("Total de Animales", stats.totalAnimales.toDouble(), "cabezas filtradas"),
            Triple("Peso Total Registrado", stats.pesoTotal, "kg acumulados al ingreso"),
            Triple("Peso Promedio", stats.pesoPromedio, "kg / cabeza"),
            Triple("Animales Activos", stats.totalActivos.toDouble(), "${String.format(locale, "%.1f", stats.pctActivos)}% del inventario"),
            Triple("Animales Vendidos", stats.totalVendidos.toDouble(), "${String.format(locale, "%.1f", stats.pctVendidos)}% del inventario"),
            Triple("Muertes / Merma", stats.totalMuertos.toDouble(), "${String.format(locale, "%.1f", stats.pctMuertos)}% del inventario")
        )

        var rIdx = 6
        for ((label, valNum, sub) in kpis) {
            val row = sheet.createRow(rIdx++)
            row.createCell(0).apply { setCellValue(label); setStyle(styles["text"]) }
            row.createCell(1).apply { setCellValue(valNum); setStyle(if (label.contains("Peso")) styles["decimal"] else styles["number"]) }
            row.createCell(2).apply { setCellValue(sub); setStyle(styles["text"]) }
        }

        // Tablas resumidas de la izquierda
        rIdx += 2
        val rowRangoHeader = sheet.createRow(rIdx++)
        rowRangoHeader.createCell(0).apply { setCellValue("DISTRIBUCIÓN POR RANGO DE PESO"); setStyle(styles["subHeader"]) }
        rowRangoHeader.createCell(1).apply { setCellValue("CANTIDAD"); setStyle(styles["subHeader"]) }
        rowRangoHeader.createCell(2).apply { setCellValue("PORCENTAJE"); setStyle(styles["subHeader"]) }

        for (item in stats.distribucionRangos) {
            val row = sheet.createRow(rIdx++)
            row.createCell(0).apply { setCellValue(item.nombreRango); setStyle(styles["text"]) }
            row.createCell(1).apply { setCellValue(item.cantidad.toDouble()); setStyle(styles["number"]) }
            row.createCell(2).apply { setCellValue(item.porcentaje / 100.0); setStyle(styles["percentage"]) }
        }

        rIdx += 2
        val rowEstadoHeader = sheet.createRow(rIdx++)
        rowEstadoHeader.createCell(0).apply { setCellValue("DISTRIBUCIÓN POR ESTADO"); setStyle(styles["subHeader"]) }
        rowEstadoHeader.createCell(1).apply { setCellValue("CANTIDAD"); setStyle(styles["subHeader"]) }
        rowEstadoHeader.createCell(2).apply { setCellValue("PORCENTAJE"); setStyle(styles["subHeader"]) }

        for (item in stats.distribucionEstado) {
            val row = sheet.createRow(rIdx++)
            row.createCell(0).apply { setCellValue(item.label); setStyle(styles["text"]) }
            row.createCell(1).apply { setCellValue(item.cantidad.toDouble()); setStyle(styles["number"]) }
            row.createCell(2).apply { setCellValue(item.porcentaje / 100.0); setStyle(styles["percentage"]) }
        }

        sheet.setColumnWidth(0, 32 * 256)
        sheet.setColumnWidth(1, 18 * 256)
        sheet.setColumnWidth(2, 35 * 256)

        // --- CREACIÓN DE LOS 4 GRÁFICOS NATIVOS DE EXCEL EN HOJA RESUMEN ---
        val sheetAnalisis = wb.getSheet("Análisis") as XSSFSheet

        // GRÁFICO 1: Barras Horizontales por Rango de Peso (E5 a J20)
        createBarChart(
            sheetResumen = sheet,
            sheetAnalisis = sheetAnalisis,
            col1 = 4, row1 = 4, col2 = 10, row2 = 20,
            title = "Distribución por rango de peso",
            catRange = rangeRango.catRange,
            valRange = rangeRango.valRange,
            barDirection = BarDirection.BAR
        )

        // GRÁFICO 2: Dona por Estado (L5 a Q20)
        createDoughnutChart(
            sheetResumen = sheet,
            sheetAnalisis = sheetAnalisis,
            col1 = 11, row1 = 4, col2 = 17, row2 = 20,
            title = "Distribución por estado",
            catRange = rangeEstado.catRange,
            valRange = rangeEstado.valRange
        )

        // GRÁFICO 3: Columnas Verticales por Sexo (E22 a J37)
        createBarChart(
            sheetResumen = sheet,
            sheetAnalisis = sheetAnalisis,
            col1 = 4, row1 = 21, col2 = 10, row2 = 37,
            title = "Distribución por sexo",
            catRange = rangeSexo.catRange,
            valRange = rangeSexo.valRange,
            barDirection = BarDirection.COL
        )

        // GRÁFICO 4: Barras Horizontales por Tipo de Arete (L22 a Q37)
        createBarChart(
            sheetResumen = sheet,
            sheetAnalisis = sheetAnalisis,
            col1 = 11, row1 = 21, col2 = 17, row2 = 37,
            title = "Distribución por tipo de arete",
            catRange = rangeArete.catRange,
            valRange = rangeArete.valRange,
            barDirection = BarDirection.BAR
        )
    }

    data class ChartRanges(
        val catRange: CellRangeAddress,
        val valRange: CellRangeAddress
    )

    // --- HOJA 3: ANÁLISIS (TABLAS FUENTE PARA LOS 4 GRÁFICOS) ---
    private fun buildAnalisisSheet(
        wb: XSSFWorkbook,
        data: ReporteGanadoExportData,
        styles: Map<String, CellStyle>
    ): Array<ChartRanges> {
        val sheet = wb.createSheet("Análisis") as XSSFSheet
        sheet.isDisplayGridlines = true

        val stats = data.stats
        var rIdx = 0

        // 1. TABLA RANGO DE PESO
        val rRangoHead = sheet.createRow(rIdx++)
        rRangoHead.createCell(0).apply { setCellValue("RANGO DE PESO"); setStyle(styles["subHeader"]) }
        rRangoHead.createCell(1).apply { setCellValue("CANTIDAD"); setStyle(styles["subHeader"]) }
        rRangoHead.createCell(2).apply { setCellValue("PORCENTAJE"); setStyle(styles["subHeader"]) }

        val startRangoRow = rIdx
        for (item in stats.distribucionRangos) {
            val r = sheet.createRow(rIdx++)
            r.createCell(0).apply { setCellValue(item.nombreRango); setStyle(styles["text"]) }
            r.createCell(1).apply { setCellValue(item.cantidad.toDouble()); setStyle(styles["number"]) }
            r.createCell(2).apply { setCellValue(item.porcentaje / 100.0); setStyle(styles["percentage"]) }
        }
        val endRangoRow = (rIdx - 1).coerceAtLeast(startRangoRow)
        val rangeRango = ChartRanges(
            catRange = CellRangeAddress(startRangoRow, endRangoRow, 0, 0),
            valRange = CellRangeAddress(startRangoRow, endRangoRow, 1, 1)
        )

        rIdx += 2

        // 2. TABLA ESTADO
        val rEstadoHead = sheet.createRow(rIdx++)
        rEstadoHead.createCell(0).apply { setCellValue("ESTADO"); setStyle(styles["subHeader"]) }
        rEstadoHead.createCell(1).apply { setCellValue("CANTIDAD"); setStyle(styles["subHeader"]) }
        rEstadoHead.createCell(2).apply { setCellValue("PORCENTAJE"); setStyle(styles["subHeader"]) }

        val startEstadoRow = rIdx
        for (item in stats.distribucionEstado) {
            val r = sheet.createRow(rIdx++)
            r.createCell(0).apply { setCellValue(item.label); setStyle(styles["text"]) }
            r.createCell(1).apply { setCellValue(item.cantidad.toDouble()); setStyle(styles["number"]) }
            r.createCell(2).apply { setCellValue(item.porcentaje / 100.0); setStyle(styles["percentage"]) }
        }
        val endEstadoRow = (rIdx - 1).coerceAtLeast(startEstadoRow)
        val rangeEstado = ChartRanges(
            catRange = CellRangeAddress(startEstadoRow, endEstadoRow, 0, 0),
            valRange = CellRangeAddress(startEstadoRow, endEstadoRow, 1, 1)
        )

        rIdx += 2

        // 3. TABLA SEXO
        val rSexHead = sheet.createRow(rIdx++)
        rSexHead.createCell(0).apply { setCellValue("SEXO"); setStyle(styles["subHeader"]) }
        rSexHead.createCell(1).apply { setCellValue("CANTIDAD"); setStyle(styles["subHeader"]) }
        rSexHead.createCell(2).apply { setCellValue("PORCENTAJE"); setStyle(styles["subHeader"]) }

        val startSexoRow = rIdx
        for (s in stats.distribucionSexo) {
            val r = sheet.createRow(rIdx++)
            r.createCell(0).apply { setCellValue(s.label); setStyle(styles["text"]) }
            r.createCell(1).apply { setCellValue(s.cantidad.toDouble()); setStyle(styles["number"]) }
            r.createCell(2).apply { setCellValue(s.porcentaje / 100.0); setStyle(styles["percentage"]) }
        }
        val endSexoRow = (rIdx - 1).coerceAtLeast(startSexoRow)
        val rangeSexo = ChartRanges(
            catRange = CellRangeAddress(startSexoRow, endSexoRow, 0, 0),
            valRange = CellRangeAddress(startSexoRow, endSexoRow, 1, 1)
        )

        rIdx += 2

        // 4. TABLA TIPO DE ARETE
        val rAreteHead = sheet.createRow(rIdx++)
        rAreteHead.createCell(0).apply { setCellValue("TIPO DE ARETE"); setStyle(styles["subHeader"]) }
        rAreteHead.createCell(1).apply { setCellValue("CANTIDAD"); setStyle(styles["subHeader"]) }
        rAreteHead.createCell(2).apply { setCellValue("PORCENTAJE"); setStyle(styles["subHeader"]) }

        val startAreteRow = rIdx
        for (a in stats.distribucionTipoArete) {
            val r = sheet.createRow(rIdx++)
            r.createCell(0).apply { setCellValue(a.label); setStyle(styles["text"]) }
            r.createCell(1).apply { setCellValue(a.cantidad.toDouble()); setStyle(styles["number"]) }
            r.createCell(2).apply { setCellValue(a.porcentaje / 100.0); setStyle(styles["percentage"]) }
        }
        val endAreteRow = (rIdx - 1).coerceAtLeast(startAreteRow)
        val rangeArete = ChartRanges(
            catRange = CellRangeAddress(startAreteRow, endAreteRow, 0, 0),
            valRange = CellRangeAddress(startAreteRow, endAreteRow, 1, 1)
        )

        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 16 * 256)
        sheet.setColumnWidth(2, 20 * 256)

        return arrayOf(rangeRango, rangeEstado, rangeSexo, rangeArete)
    }

    // HELPER CREACIÓN GRÁFICOS BARRAS / COLUMNAS NATIVOS
    private fun createBarChart(
        sheetResumen: XSSFSheet,
        sheetAnalisis: XSSFSheet,
        col1: Int, row1: Int, col2: Int, row2: Int,
        title: String,
        catRange: CellRangeAddress,
        valRange: CellRangeAddress,
        barDirection: BarDirection
    ) {
        val drawing = sheetResumen.createDrawingPatriarch() as XSSFDrawing
        val anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2) as XSSFClientAnchor
        val chart = drawing.createChart(anchor)
        chart.setTitleText(title)
        chart.setTitleOverlay(false)

        val legend = chart.getOrAddLegend()
        legend.position = LegendPosition.BOTTOM

        val categoryAxis = chart.createCategoryAxis(AxisPosition.BOTTOM)
        val valueAxis = chart.createValueAxis(AxisPosition.LEFT)
        valueAxis.setCrosses(AxisCrosses.AUTO_ZERO)

        val data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis) as XDDFBarChartData
        data.setBarDirection(barDirection)

        val categories = XDDFDataSourcesFactory.fromStringCellRange(sheetAnalisis, catRange)
        val values = XDDFDataSourcesFactory.fromNumericCellRange(sheetAnalisis, valRange)

        val series = data.addSeries(categories, values) as XDDFBarChartData.Series
        series.setTitle("Cantidad", null)

        chart.plot(data)
    }

    // HELPER CREACIÓN GRÁFICO DONA NATIVO
    private fun createDoughnutChart(
        sheetResumen: XSSFSheet,
        sheetAnalisis: XSSFSheet,
        col1: Int, row1: Int, col2: Int, row2: Int,
        title: String,
        catRange: CellRangeAddress,
        valRange: CellRangeAddress
    ) {
        val drawing = sheetResumen.createDrawingPatriarch() as XSSFDrawing
        val anchor = drawing.createAnchor(0, 0, 0, 0, col1, row1, col2, row2) as XSSFClientAnchor
        val chart = drawing.createChart(anchor)
        chart.setTitleText(title)
        chart.setTitleOverlay(false)

        val legend = chart.getOrAddLegend()
        legend.position = LegendPosition.RIGHT

        val categories = XDDFDataSourcesFactory.fromStringCellRange(sheetAnalisis, catRange)
        val values = XDDFDataSourcesFactory.fromNumericCellRange(sheetAnalisis, valRange)

        val data = chart.createData(ChartTypes.DOUGHNUT, null, null) as XDDFDoughnutChartData
        data.setVaryColors(true)

        val series = data.addSeries(categories, values)
        series.setTitle("Estado", null)

        chart.plot(data)
    }

    // --- HOJA 2: REGISTROS ---
    private fun buildRegistrosSheet(wb: XSSFWorkbook, data: ReporteGanadoExportData, styles: Map<String, CellStyle>) {
        val sheet = wb.createSheet("Registros") as XSSFSheet
        sheet.isDisplayGridlines = true

        val headers = arrayOf(
            "Número de Arete", "Tipo de Arete", "Fecha Entrada", "Color", "Sexo", "Estado",
            "Peso Entrada (kg)", "Peso Salida (kg)", "Rango de Peso", "Precio Compra ($)",
            "Precio Venta ($)", "Proveedor", "Comprador", "Fecha Salida"
        )

        val rowHeader = sheet.createRow(0)
        for ((idx, h) in headers.withIndex()) {
            rowHeader.createCell(idx).apply { setCellValue(h); setStyle(styles["header"]) }
        }

        var rIdx = 1
        for (r in data.tablaRows) {
            val row = sheet.createRow(rIdx++)
            row.createCell(0).apply { setCellValue(r.numeroArete); setStyle(styles["center"]) }
            row.createCell(1).apply { setCellValue(r.tipoArete); setStyle(styles["center"]) }
            row.createCell(2).apply { setCellValue(r.fechaEntradaTexto); setStyle(styles["center"]) }
            row.createCell(3).apply { setCellValue(r.color); setStyle(styles["text"]) }
            row.createCell(4).apply { setCellValue(r.sexo); setStyle(styles["center"]) }
            row.createCell(5).apply { setCellValue(r.estado); setStyle(styles["center"]) }
            row.createCell(6).apply { setCellValue(r.pesoEntrada); setStyle(styles["decimal"]) }

            if (r.pesoSalida != null) {
                row.createCell(7).apply { setCellValue(r.pesoSalida); setStyle(styles["decimal"]) }
            } else {
                row.createCell(7).apply { setCellValue("N/A"); setStyle(styles["center"]) }
            }

            row.createCell(8).apply { setCellValue(r.rangoPeso); setStyle(styles["text"]) }
            row.createCell(9).apply { setCellValue(r.precioCompra); setStyle(styles["currency"]) }

            if (r.precioVenta != null) {
                row.createCell(10).apply { setCellValue(r.precioVenta); setStyle(styles["currency"]) }
            } else {
                row.createCell(10).apply { setCellValue("N/A"); setStyle(styles["center"]) }
            }

            row.createCell(11).apply { setCellValue(r.proveedor); setStyle(styles["text"]) }
            row.createCell(12).apply { setCellValue(r.comprador); setStyle(styles["text"]) }
            row.createCell(13).apply { setCellValue(r.fechaSalidaTexto); setStyle(styles["center"]) }
        }

        // AutoFiltros e Inmovilización
        sheet.setAutoFilter(CellRangeAddress(0, (rIdx - 1).coerceAtLeast(1), 0, headers.size - 1))
        sheet.createFreezePane(0, 1)

        headers.indices.forEach { col -> sheet.setColumnWidth(col, 18 * 256) }
        sheet.setColumnWidth(0, 22 * 256)
    }

    // --- HOJA 4: CATÁLOGOS Y CRITERIOS ---
    private fun buildCatalogosSheet(wb: XSSFWorkbook, data: ReporteGanadoExportData, styles: Map<String, CellStyle>) {
        val sheet = wb.createSheet("Catálogos y Criterios") as XSSFSheet
        sheet.isDisplayGridlines = true

        var rIdx = 0

        val r0 = sheet.createRow(rIdx++)
        r0.createCell(0).apply { setCellValue("DOCUMENTACIÓN Y CRITERIOS DE EXPORTACIÓN"); setStyle(styles["title"]) }

        rIdx++
        val rHead1 = sheet.createRow(rIdx++)
        rHead1.createCell(0).apply { setCellValue("CATÁLOGO DE ESTADOS DE INVENTARIO"); setStyle(styles["header"]) }
        rHead1.createCell(1).apply { setCellValue("DESCRIPCIÓN OPERATIVA"); setStyle(styles["header"]) }

        val catEstados = listOf(
            "ACTIVO" to "Animal en estancia activa en el rancho.",
            "VENDIDO" to "Baja consolidada por venta a un comprador.",
            "MUERTO" to "Baja por mortalidad o merma en el hato."
        )

        for ((est, desc) in catEstados) {
            val r = sheet.createRow(rIdx++)
            r.createCell(0).apply { setCellValue(est); setStyle(styles["text"]) }
            r.createCell(1).apply { setCellValue(desc); setStyle(styles["text"]) }
        }

        rIdx += 2
        val rHead2 = sheet.createRow(rIdx++)
        rHead2.createCell(0).apply { setCellValue("RANGO DE PESO"); setStyle(styles["subHeader"]) }
        rHead2.createCell(1).apply { setCellValue("LÍMITE INFERIOR (KG)"); setStyle(styles["subHeader"]) }
        rHead2.createCell(2).apply { setCellValue("LÍMITE SUPERIOR (KG)"); setStyle(styles["subHeader"]) }

        for (rg in data.rangosPesoList) {
            val r = sheet.createRow(rIdx++)
            r.createCell(0).apply { setCellValue(rg.nombreRango); setStyle(styles["text"]) }
            r.createCell(1).apply { setCellValue(rg.limiteInferior); setStyle(styles["decimal"]) }
            r.createCell(2).apply { setCellValue(rg.limiteSuperior); setStyle(styles["decimal"]) }
        }

        sheet.setColumnWidth(0, 30 * 256)
        sheet.setColumnWidth(1, 45 * 256)
        sheet.setColumnWidth(2, 25 * 256)
    }
}
