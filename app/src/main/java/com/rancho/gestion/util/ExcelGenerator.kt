package com.rancho.gestion.util

import android.content.Context
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.ui.finanzas.categoriaLabel
import org.apache.poi.ss.usermodel.*
import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xddf.usermodel.chart.*
import org.apache.poi.xssf.usermodel.XSSFChart
import org.apache.poi.xssf.usermodel.XSSFColor
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object ExcelGenerator {

    fun generateExcel(
        context: Context,
        file: File,
        egresos: List<Egreso>,
        desde: Long,
        hasta: Long,
        filtroCategorias: String,
        incluirDescripcion: Boolean
    ): Boolean {
        return try {
            val data = ReporteFinancieroData.preparar(egresos, desde, hasta, filtroCategorias, incluirDescripcion)
            val workbook = XSSFWorkbook()

            val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-MX"))
            val periodStr = "Del ${dateFormat.format(Date(desde))} al ${dateFormat.format(Date(hasta))}"

            // Setup Cell Styles
            val styles = createStyles(workbook)

            // --- HOJA 1: RESUMEN ---
            buildResumenSheet(workbook, data, periodStr, styles)

            // --- HOJA 2: MOVIMIENTOS ---
            buildMovimientosSheet(workbook, data, periodStr, styles, dateFormat)

            // --- HOJA 3: CATEGORÍAS ---
            buildCategoriasSheet(workbook, data, periodStr, styles)

            FileOutputStream(file).use { out ->
                workbook.write(out)
            }
            workbook.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("ExcelGenerator", "Error al crear Excel", e)
            false
        }
    }

    private fun Cell.setStyle(style: CellStyle?) {
        if (style != null) {
            cellStyle = style
        }
    }

    private fun buildResumenSheet(
        workbook: XSSFWorkbook,
        data: ReporteFinancieroData,
        periodStr: String,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet("Resumen") as XSSFSheet
        sheet.isDisplayGridlines = true

        // Title Block
        val row0 = sheet.createRow(0)
        row0.createCell(0).apply { setCellValue("Rancho San Martín"); setStyle(styles["title"]) }

        val row1 = sheet.createRow(1)
        row1.createCell(0).apply { setCellValue("Informe Financiero - Resumen Ejecutivo"); setStyle(styles["subtitle"]) }

        val row2 = sheet.createRow(2)
        row2.createCell(0).apply { setCellValue("Periodo: $periodStr"); setStyle(styles["meta"]) }

        val row3 = sheet.createRow(3)
        row3.createCell(0).apply { setCellValue("Categorías seleccionadas: ${data.filtroCategoriasTexto}"); setStyle(styles["meta"]) }

        // KPI Box
        val row5 = sheet.createRow(5)
        row5.createCell(0).apply { setCellValue("Total de Egresos:"); setStyle(styles["kpiLabel"]) }
        row5.createCell(1).apply { setCellValue(data.totalGeneral.toDouble()); setStyle(styles["kpiValue"]) }

        val row6 = sheet.createRow(6)
        row6.createCell(0).apply { setCellValue("Movimientos Incluidos:"); setStyle(styles["kpiLabel"]) }
        row6.createCell(1).apply { setCellValue(data.totalMovimientos.toDouble()); setStyle(styles["kpiCount"]) }

        // Summary Table Headers (Row 8)
        val row8 = sheet.createRow(8)
        val headers = arrayOf("Categoría", "Total Egresos ($)", "Porcentaje (%)", "Movimientos")
        for ((colIdx, header) in headers.withIndex()) {
            row8.createCell(colIdx).apply { setCellValue(header); setStyle(styles["header"]) }
        }

        var rIdx = 9
        val startCatRow = rIdx + 1 // 1-based for Excel formula
        for (catRes in data.resumenCategorias) {
            val row = sheet.createRow(rIdx++)
            row.createCell(0).apply { setCellValue(catRes.label); setStyle(styles["dataText"]) }
            row.createCell(1).apply { setCellValue(catRes.total.toDouble()); setStyle(styles["currency"]) }
            row.createCell(2).apply { setCellValue(catRes.porcentaje.toDouble() / 100.0); setStyle(styles["percentage"]) }
            row.createCell(3).apply { setCellValue(catRes.cantidadMovimientos.toDouble()); setStyle(styles["dataNumber"]) }
        }
        val endCatRow = rIdx // 1-based for Excel formula

        // Summary Total Row
        val totalRow = sheet.createRow(rIdx)
        totalRow.createCell(0).apply { setCellValue("Total General"); setStyle(styles["totalLabel"]) }
        totalRow.createCell(1).apply { cellFormula = "SUM(B$startCatRow:B$endCatRow)"; setStyle(styles["totalCurrency"]) }
        totalRow.createCell(2).apply { cellFormula = "SUM(C$startCatRow:C$endCatRow)"; setStyle(styles["totalPercentage"]) }
        totalRow.createCell(3).apply { cellFormula = "SUM(D$startCatRow:D$endCatRow)"; setStyle(styles["totalNumber"]) }

        // Set column widths
        sheet.setColumnWidth(0, 22 * 256)
        sheet.setColumnWidth(1, 20 * 256)
        sheet.setColumnWidth(2, 18 * 256)
        sheet.setColumnWidth(3, 16 * 256)

        // Add Native Charts on Resumen Sheet
        if (data.resumenCategorias.isNotEmpty() && data.totalGeneral.toDouble() > 0) {
            val catRange = CellRangeAddress(8, rIdx - 1, 0, 0)
            val valRange = CellRangeAddress(8, rIdx - 1, 1, 1)

            // Horizontal Bar Chart
            createBarChart(sheet, "Comparativa por Categoría", catRange, valRange, 5, 1, 13, 18)

            // Donut / Pie Chart
            createPieChart(sheet, "Distribución por Categoría", catRange, valRange, 5, 19, 13, 35)
        }
    }

    private fun buildMovimientosSheet(
        workbook: XSSFWorkbook,
        data: ReporteFinancieroData,
        periodStr: String,
        styles: Map<String, CellStyle>,
        dateFormat: SimpleDateFormat
    ) {
        val sheet = workbook.createSheet("Movimientos") as XSSFSheet
        sheet.isDisplayGridlines = true

        // Row 0 & 1: Header info
        val row0 = sheet.createRow(0)
        row0.createCell(0).apply { setCellValue("Rancho San Martín - Detalle de Movimientos"); setStyle(styles["subtitle"]) }

        val row1 = sheet.createRow(1)
        row1.createCell(0).apply { setCellValue("Periodo: $periodStr"); setStyle(styles["meta"]) }

        // Table Headers at Row 3
        val headerRow = sheet.createRow(3)
        val headers = if (data.incluirDescripcion) arrayOf("Fecha", "Categoría", "Monto", "Descripción") else arrayOf("Fecha", "Categoría", "Monto")
        for ((i, h) in headers.withIndex()) {
            headerRow.createCell(i).apply { setCellValue(h); setStyle(styles["header"]) }
        }

        var rIdx = 4
        val startDataRow = rIdx + 1 // 1-based index for Excel
        for ((index, egreso) in data.egresos.withIndex()) {
            val row = sheet.createRow(rIdx++)
            val isEven = index % 2 == 0
            val textStyle = if (isEven) styles["dataText"] else styles["dataTextAlt"]
            val currStyle = if (isEven) styles["currency"] else styles["currencyAlt"]

            row.createCell(0).apply { setCellValue(dateFormat.format(Date(egreso.fechaEgreso))); setStyle(textStyle) }
            row.createCell(1).apply { setCellValue(categoriaLabel(egreso.categoria)); setStyle(textStyle) }
            // CRITICAL: Stored as numeric value, formatted as currency
            row.createCell(2).apply { setCellValue(egreso.montoTotal); setStyle(currStyle) }

            if (data.incluirDescripcion) {
                row.createCell(3).apply { setCellValue(egreso.descripcion); setStyle(if (isEven) styles["dataWrap"] else styles["dataWrapAlt"]) }
            }
        }
        val endDataRow = maxOf(4, rIdx) // 1-based index for Excel

        // Total Row at bottom
        val totalRow = sheet.createRow(rIdx)
        totalRow.createCell(0).apply { setCellValue("Total General"); setStyle(styles["totalLabel"]) }
        totalRow.createCell(1).apply { setCellValue(""); setStyle(styles["totalLabel"]) }
        totalRow.createCell(2).apply {
            if (data.egresos.isNotEmpty()) {
                cellFormula = "SUM(C$startDataRow:C$endDataRow)"
            } else {
                setCellValue(0.0)
            }
            setStyle(styles["totalCurrency"])
        }
        if (data.incluirDescripcion) {
            totalRow.createCell(3).apply { setCellValue(""); setStyle(styles["totalLabel"]) }
        }

        // Apply AutoFilter across movement columns
        val lastCol = if (data.incluirDescripcion) 3 else 2
        val filterEndRow = maxOf(3, rIdx - 1)
        sheet.setAutoFilter(CellRangeAddress(3, filterEndRow, 0, lastCol))

        // Freeze header row
        sheet.createFreezePane(0, 4)

        // Set column widths
        sheet.setColumnWidth(0, 16 * 256)
        sheet.setColumnWidth(1, 20 * 256)
        sheet.setColumnWidth(2, 18 * 256)
        if (data.incluirDescripcion) {
            sheet.setColumnWidth(3, 40 * 256)
        }
    }

    private fun buildCategoriasSheet(
        workbook: XSSFWorkbook,
        data: ReporteFinancieroData,
        periodStr: String,
        styles: Map<String, CellStyle>
    ) {
        val sheet = workbook.createSheet("Categorías") as XSSFSheet
        sheet.isDisplayGridlines = true

        val row0 = sheet.createRow(0)
        row0.createCell(0).apply { setCellValue("Rancho San Martín - Resumen por Categorías"); setStyle(styles["subtitle"]) }

        val row1 = sheet.createRow(1)
        row1.createCell(0).apply { setCellValue("Periodo: $periodStr"); setStyle(styles["meta"]) }

        // Header Row at Row 3
        val headerRow = sheet.createRow(3)
        val headers = arrayOf("Categoría", "Total Egresos ($)", "Porcentaje del Total", "Número de Movimientos")
        for ((i, h) in headers.withIndex()) {
            headerRow.createCell(i).apply { setCellValue(h); setStyle(styles["header"]) }
        }

        var rIdx = 4
        val startCatRow = rIdx + 1
        for (catRes in data.resumenCategorias) {
            val row = sheet.createRow(rIdx++)
            row.createCell(0).apply { setCellValue(catRes.label); setStyle(styles["dataText"]) }
            row.createCell(1).apply { setCellValue(catRes.total.toDouble()); setStyle(styles["currency"]) }
            row.createCell(2).apply { setCellValue(catRes.porcentaje.toDouble() / 100.0); setStyle(styles["percentage"]) }
            row.createCell(3).apply { setCellValue(catRes.cantidadMovimientos.toDouble()); setStyle(styles["dataNumber"]) }
        }
        val endCatRow = maxOf(4, rIdx - 1)

        // Total Row
        val totalRow = sheet.createRow(rIdx)
        totalRow.createCell(0).apply { setCellValue("Total General"); setStyle(styles["totalLabel"]) }
        totalRow.createCell(1).apply { cellFormula = "SUM(B$startCatRow:B$endCatRow)"; setStyle(styles["totalCurrency"]) }
        totalRow.createCell(2).apply { cellFormula = "SUM(C$startCatRow:C$endCatRow)"; setStyle(styles["totalPercentage"]) }
        totalRow.createCell(3).apply { cellFormula = "SUM(D$startCatRow:D$endCatRow)"; setStyle(styles["totalNumber"]) }

        sheet.setColumnWidth(0, 22 * 256)
        sheet.setColumnWidth(1, 20 * 256)
        sheet.setColumnWidth(2, 22 * 256)
        sheet.setColumnWidth(3, 22 * 256)

        // Add Native Charts on Categorias Sheet linked to Categorias data
        if (data.resumenCategorias.isNotEmpty() && data.totalGeneral.toDouble() > 0) {
            val catRange = CellRangeAddress(3, endCatRow, 0, 0)
            val valRange = CellRangeAddress(3, endCatRow, 1, 1)

            // Horizontal Bar Chart
            createBarChart(sheet, "Comparativa de Gastos por Categoría", catRange, valRange, 5, 1, 13, 18)

            // Pie Chart
            createPieChart(sheet, "Porcentaje de Gastos por Categoría", catRange, valRange, 5, 19, 13, 35)
        }
    }

    private fun createPieChart(
        sheet: XSSFSheet,
        title: String,
        catRange: CellRangeAddress,
        valRange: CellRangeAddress,
        startCol: Int, startRow: Int, endCol: Int, endRow: Int
    ) {
        val drawing = sheet.createDrawingPatriarch() as XSSFDrawing
        val anchor = drawing.createAnchor(0, 0, 0, 0, startCol, startRow, endCol, endRow)
        val chart = drawing.createChart(anchor) as XSSFChart
        chart.setTitleText(title)
        chart.setTitleOverlay(false)

        val legend = chart.getOrAddLegend()
        legend.setPosition(LegendPosition.RIGHT)

        val categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, catRange)
        val values = XDDFDataSourcesFactory.fromNumericCellRange(sheet, valRange)

        val data = chart.createData(ChartTypes.PIE, null, null) as XDDFPieChartData
        data.setVaryColors(true)
        data.addSeries(categories, values)
        chart.plot(data)
    }

    private fun createBarChart(
        sheet: XSSFSheet,
        title: String,
        catRange: CellRangeAddress,
        valRange: CellRangeAddress,
        startCol: Int, startRow: Int, endCol: Int, endRow: Int
    ) {
        val drawing = sheet.createDrawingPatriarch() as XSSFDrawing
        val anchor = drawing.createAnchor(0, 0, 0, 0, startCol, startRow, endCol, endRow)
        val chart = drawing.createChart(anchor) as XSSFChart
        chart.setTitleText(title)

        val legend = chart.getOrAddLegend()
        legend.setPosition(LegendPosition.TOP_RIGHT)

        val categoryAxis = chart.createCategoryAxis(AxisPosition.LEFT)
        val valueAxis = chart.createValueAxis(AxisPosition.BOTTOM)

        val categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, catRange)
        val values = XDDFDataSourcesFactory.fromNumericCellRange(sheet, valRange)

        val data = chart.createData(ChartTypes.BAR, categoryAxis, valueAxis) as XDDFBarChartData
        data.setBarDirection(BarDirection.BAR) // Horizontal bar chart
        data.setVaryColors(true)
        val series = data.addSeries(categories, values)
        series.setTitle("Monto ($)", null)
        chart.plot(data)
    }

    private fun createStyles(wb: XSSFWorkbook): Map<String, CellStyle> {
        val currencyFormat = wb.createDataFormat().getFormat("$#,##0.00")
        val percentageFormat = wb.createDataFormat().getFormat("0.0%")
        val numberFormat = wb.createDataFormat().getFormat("#,##0")

        val fontHeader = wb.createFont().apply {
            bold = true
            color = IndexedColors.WHITE.index
            fontHeightInPoints = 11
        }
        val fontTitle = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 16
            color = IndexedColors.DARK_BLUE.index
        }
        val fontSubtitle = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 13
            color = IndexedColors.ROYAL_BLUE.index
        }
        val fontBold = wb.createFont().apply {
            bold = true
            fontHeightInPoints = 11
        }
        val fontRegular = wb.createFont().apply {
            fontHeightInPoints = 10
        }

        val headerFillColor = XSSFColor(byteArrayOf(15, 39, 64), null) // #0F2740 Dark Petrol
        val altRowColor = XSSFColor(byteArrayOf(241.toByte(), 245.toByte(), 249.toByte()), null) // #F1F5F9 Light Gray

        // Header style
        val headerStyle = wb.createCellStyle().apply {
            setFont(fontHeader)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            (this as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(headerFillColor)
            alignment = HorizontalAlignment.CENTER
            verticalAlignment = VerticalAlignment.CENTER
        }

        // Title style
        val titleStyle = wb.createCellStyle().apply { setFont(fontTitle) }
        val subtitleStyle = wb.createCellStyle().apply { setFont(fontSubtitle) }
        val metaStyle = wb.createCellStyle().apply { setFont(fontRegular) }

        val kpiLabelStyle = wb.createCellStyle().apply { setFont(fontBold) }
        val kpiValueStyle = wb.createCellStyle().apply {
            setFont(fontTitle)
            dataFormat = currencyFormat
        }
        val kpiCountStyle = wb.createCellStyle().apply {
            setFont(fontBold)
            dataFormat = numberFormat
        }

        // Data styles
        val dataText = wb.createCellStyle().apply { setFont(fontRegular) }
        val dataTextAlt = wb.createCellStyle().apply {
            setFont(fontRegular)
            fillPattern = FillPatternType.SOLID_FOREGROUND
            (this as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(altRowColor)
        }

        val currencyStyle = wb.createCellStyle().apply {
            setFont(fontRegular)
            dataFormat = currencyFormat
            alignment = HorizontalAlignment.RIGHT
        }
        val currencyStyleAlt = wb.createCellStyle().apply {
            setFont(fontRegular)
            dataFormat = currencyFormat
            alignment = HorizontalAlignment.RIGHT
            fillPattern = FillPatternType.SOLID_FOREGROUND
            (this as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(altRowColor)
        }

        val percentageStyle = wb.createCellStyle().apply {
            setFont(fontRegular)
            dataFormat = percentageFormat
            alignment = HorizontalAlignment.RIGHT
        }
        val dataNumberStyle = wb.createCellStyle().apply {
            setFont(fontRegular)
            dataFormat = numberFormat
            alignment = HorizontalAlignment.RIGHT
        }

        val dataWrapStyle = wb.createCellStyle().apply {
            setFont(fontRegular)
            wrapText = true
        }
        val dataWrapStyleAlt = wb.createCellStyle().apply {
            setFont(fontRegular)
            wrapText = true
            fillPattern = FillPatternType.SOLID_FOREGROUND
            (this as org.apache.poi.xssf.usermodel.XSSFCellStyle).setFillForegroundColor(altRowColor)
        }

        // Total styles
        val totalLabel = wb.createCellStyle().apply {
            setFont(fontBold)
            alignment = HorizontalAlignment.LEFT
        }
        val totalCurrency = wb.createCellStyle().apply {
            setFont(fontBold)
            dataFormat = currencyFormat
            alignment = HorizontalAlignment.RIGHT
        }
        val totalPercentage = wb.createCellStyle().apply {
            setFont(fontBold)
            dataFormat = percentageFormat
            alignment = HorizontalAlignment.RIGHT
        }
        val totalNumber = wb.createCellStyle().apply {
            setFont(fontBold)
            dataFormat = numberFormat
            alignment = HorizontalAlignment.RIGHT
        }

        return mapOf(
            "header" to headerStyle,
            "title" to titleStyle,
            "subtitle" to subtitleStyle,
            "meta" to metaStyle,
            "kpiLabel" to kpiLabelStyle,
            "kpiValue" to kpiValueStyle,
            "kpiCount" to kpiCountStyle,
            "dataText" to dataText,
            "dataTextAlt" to dataTextAlt,
            "currency" to currencyStyle,
            "currencyAlt" to currencyStyleAlt,
            "percentage" to percentageStyle,
            "dataNumber" to dataNumberStyle,
            "dataWrap" to dataWrapStyle,
            "dataWrapAlt" to dataWrapStyleAlt,
            "totalLabel" to totalLabel,
            "totalCurrency" to totalCurrency,
            "totalPercentage" to totalPercentage,
            "totalNumber" to totalNumber
        )
    }
}
