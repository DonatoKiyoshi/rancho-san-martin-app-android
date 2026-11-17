package com.rancho.gestion.util

import com.rancho.gestion.data.entities.Animal
import com.rancho.gestion.data.entities.EstadoInventario
import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.entities.TipoArete
import com.rancho.gestion.ui.reportes.ReporteGanadoRow
import org.apache.poi.xssf.usermodel.XSSFSheet
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream

class ReporteGanadoExportTest {

    private val sampleAnimales = listOf(
        Animal(
            numeroArete = "484000000000001",
            tipoArete = TipoArete.SADER,
            color = "Negro",
            sexo = Sexo.MACHO,
            proveedor = "Rancho San Martín",
            precioCompra = 12000.0,
            pesoEntrada = 200.0,
            idRangoPeso = "1",
            estadoInventario = EstadoInventario.ACTIVO,
            idUsuarioRegistro = "1"
        ),
        Animal(
            numeroArete = "484000000000002",
            tipoArete = TipoArete.SADER,
            color = "Careto",
            sexo = Sexo.HEMBRA,
            proveedor = "Rancho San Martín",
            precioCompra = 15000.0,
            pesoEntrada = 300.0,
            idRangoPeso = "2",
            estadoInventario = EstadoInventario.VENDIDO,
            idUsuarioRegistro = "1"
        )
    )

    private val sampleRangos = listOf(
        RangoPeso(idRangoPeso = "1", limiteInferior = 130.0, limiteSuperior = 220.0, nombreRango = "Becerro"),
        RangoPeso(idRangoPeso = "2", limiteInferior = 221.0, limiteSuperior = 350.0, nombreRango = "Añojo")
    )

    private val sampleTablaRows = listOf(
        ReporteGanadoRow(
            numeroArete = "484000000000001",
            tipoArete = "SADER",
            fechaEntradaTexto = "12/10/2026",
            color = "Negro",
            sexo = "Macho",
            estado = "Activo",
            pesoEntrada = 200.0,
            pesoEntradaTexto = "200 kg",
            pesoSalida = null,
            pesoSalidaTexto = "N/A",
            rangoPeso = "Becerro",
            precioCompra = 12000.0,
            precioCompraTexto = "$12,000.00",
            precioVenta = null,
            precioVentaTexto = "N/A",
            proveedor = "Rancho San Martín",
            comprador = "N/A",
            fechaSalidaTexto = "N/A",
            fotografiaLocal = null
        ),
        ReporteGanadoRow(
            numeroArete = "484000000000002",
            tipoArete = "SADER",
            fechaEntradaTexto = "12/10/2026",
            color = "Careto",
            sexo = "Hembra",
            estado = "Vendido",
            pesoEntrada = 300.0,
            pesoEntradaTexto = "300 kg",
            pesoSalida = 380.0,
            pesoSalidaTexto = "380 kg",
            rangoPeso = "Añojo",
            precioCompra = 15000.0,
            precioCompraTexto = "$15,000.00",
            precioVenta = 22000.0,
            precioVentaTexto = "$22,000.00",
            proveedor = "Rancho San Martín",
            comprador = "Carnes GDL",
            fechaSalidaTexto = "15/10/2026",
            fotografiaLocal = null
        )
    )

    @Test
    fun testPrepararExportData() {
        val stats = ReporteGanadoDataCalculator.calcular(sampleAnimales, sampleRangos)
        val exportData = ReporteGanadoExportDataBuilder.preparar(
            nombreRancho = "Rancho San Martín",
            periodoTxt = "Mensual",
            filtrosAplicadosTxt = "tipo de arete: ambos, color: todos, estado: todos, rango de peso: todos",
            stats = stats,
            tablaRows = sampleTablaRows,
            rangos = sampleRangos
        )

        assertEquals("Rancho San Martín", exportData.nombreRancho)
        assertEquals(2, exportData.totalRegistros)
        assertEquals(2, exportData.stats.totalAnimales)
        assertEquals(2, exportData.tablaRows.size)
    }

    @Test
    fun testExcelGeneratorIncrustaCuatroGraficosNativos() {
        val stats = ReporteGanadoDataCalculator.calcular(sampleAnimales, sampleRangos)
        val exportData = ReporteGanadoExportDataBuilder.preparar(
            nombreRancho = "Rancho San Martín",
            periodoTxt = "Mensual",
            filtrosAplicadosTxt = "tipo de arete: ambos, color: todos, estado: todos, rango de peso: todos",
            stats = stats,
            tablaRows = sampleTablaRows,
            rangos = sampleRangos
        )

        val tempFile = File.createTempFile("test_excel_charts", ".xlsx")
        val result = ExcelGanadoGenerator.generateExcel(null, tempFile, exportData)
        assertTrue(result)

        // Abrir e inspeccionar el archivo generado con Apache POI
        val wb = XSSFWorkbook(tempFile.inputStream())
        assertEquals(4, wb.numberOfSheets)

        val sheetResumen = wb.getSheet("Resumen") as XSSFSheet
        val drawing = sheetResumen.createDrawingPatriarch()
        val charts = drawing.charts

        // VERIFICACIÓN CLAVE: Se deben encontrar exactamente 4 gráficos
        assertEquals(4, charts.size)

        val titles = charts.map { it.titleText.string }
        assertTrue(titles.contains("Distribución por rango de peso"))
        assertTrue(titles.contains("Distribución por estado"))
        assertTrue(titles.contains("Distribución por sexo"))
        assertTrue(titles.contains("Distribución por tipo de arete"))

        wb.close()
        tempFile.delete()
    }
}
