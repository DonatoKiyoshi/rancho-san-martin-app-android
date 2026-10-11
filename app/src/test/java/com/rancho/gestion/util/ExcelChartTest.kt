package com.rancho.gestion.util

import org.apache.poi.ss.util.CellRangeAddress
import org.apache.poi.xddf.usermodel.chart.*
import org.apache.poi.xssf.usermodel.XSSFChart
import org.apache.poi.xssf.usermodel.XSSFDrawing
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ExcelChartTest {

    @Test
    fun testCreateXSSFChart() {
        val workbook = XSSFWorkbook()
        val sheet = workbook.createSheet("TestSheet")

        val drawing = sheet.createDrawingPatriarch() as XSSFDrawing
        val anchor = drawing.createAnchor(0, 0, 0, 0, 3, 0, 10, 15)
        val chart = drawing.createChart(anchor) as XSSFChart

        chart.setTitleText("Test Title")
        chart.setTitleOverlay(false)

        val legend = chart.getOrAddLegend()
        assertNotNull(legend)

        val categories = XDDFDataSourcesFactory.fromStringCellRange(sheet, CellRangeAddress(1, 2, 0, 0))
        val values = XDDFDataSourcesFactory.fromNumericCellRange(sheet, CellRangeAddress(1, 2, 1, 1))

        val data = chart.createData(ChartTypes.PIE, null, null) as XDDFPieChartData
        data.setVaryColors(true)
        data.addSeries(categories, values)
        chart.plot(data)

        val tempFile = File.createTempFile("test_excel_chart", ".xlsx")
        FileOutputStream(tempFile).use { out ->
            workbook.write(out)
        }
        workbook.close()
        tempFile.delete()
    }
}
