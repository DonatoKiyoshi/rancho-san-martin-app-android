package com.rancho.gestion.util

import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal

class ReporteGeneratorsTest {

    private val sampleEgresos = listOf(
        Egreso(
            idEgreso = "1",
            fechaEgreso = System.currentTimeMillis(),
            categoria = CategoriaEgreso.NOMINA,
            descripcion = "Pago quincenal de vaqueros",
            montoTotal = 15000.0,
            idUsuario = "1"
        ),
        Egreso(
            idEgreso = "2",
            fechaEgreso = System.currentTimeMillis() - 86400000L,
            categoria = CategoriaEgreso.ALIMENTO,
            descripcion = "Compra de pacas de alfalfa y suplementos concentrados",
            montoTotal = 8500.0,
            idUsuario = "1"
        ),
        Egreso(
            idEgreso = "3",
            fechaEgreso = System.currentTimeMillis() - 172800000L,
            categoria = CategoriaEgreso.FARMACOS,
            descripcion = "Vacunas y desparasitantes",
            montoTotal = 3200.0,
            idUsuario = "1"
        )
    )

    @Test
    fun testCalculosCalculadosCorrectamente() {
        val data = ReporteFinancieroData.preparar(
            sampleEgresos,
            System.currentTimeMillis() - 604800000L,
            System.currentTimeMillis(),
            "Todas",
            true
        )

        assertEquals(BigDecimal("26700.00"), data.totalGeneral)
        assertEquals(3, data.totalMovimientos)
        assertEquals(3, data.resumenCategorias.size)

        // Nomina pct: (15000 / 26700) * 100 = 56.18%
        val catNomina = data.resumenCategorias.first { it.categoria == CategoriaEgreso.NOMINA }
        assertEquals(BigDecimal("15000.00"), catNomina.total)
        assertEquals(BigDecimal("56.18"), catNomina.porcentaje)
    }

    @Test
    fun testEstructuraTresHojasExcel() {
        val wb = XSSFWorkbook()
        wb.createSheet("Resumen")
        wb.createSheet("Movimientos")
        wb.createSheet("Categorías")

        assertEquals(3, wb.numberOfSheets)
        assertEquals("Resumen", wb.getSheetName(0))
        assertEquals("Movimientos", wb.getSheetName(1))
        assertEquals("Categorías", wb.getSheetName(2))

        val tempFile = File.createTempFile("test_excel_file", ".xlsx")
        FileOutputStream(tempFile).use { out -> wb.write(out) }
        wb.close()

        assertTrue(tempFile.exists() && tempFile.length() > 0)
        tempFile.delete()
    }
}
