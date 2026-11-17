package com.rancho.gestion.util

import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import org.junit.Assert.assertEquals
import org.junit.Test
import java.math.BigDecimal

class ExcelGeneratorTest {

    @Test
    fun testReporteFinancieroDataPreparacion() {
        val egresos = listOf(
            Egreso(
                idEgreso = "1",
                fechaEgreso = System.currentTimeMillis(),
                categoria = CategoriaEgreso.NOMINA,
                descripcion = "Pago semana",
                montoTotal = 10000.0,
                idUsuario = "1"
            ),
            Egreso(
                idEgreso = "2",
                fechaEgreso = System.currentTimeMillis(),
                categoria = CategoriaEgreso.ALIMENTO,
                descripcion = "Compra alimento",
                montoTotal = 5000.0,
                idUsuario = "1"
            )
        )

        val data = ReporteFinancieroData.preparar(egresos, 0L, 100L, "Todas", true)

        assertEquals(BigDecimal("15000.00"), data.totalGeneral)
        assertEquals(2, data.totalMovimientos)
        assertEquals(2, data.resumenCategorias.size)

        val catNomina = data.resumenCategorias.first { it.categoria == CategoriaEgreso.NOMINA }
        assertEquals(BigDecimal("10000.00"), catNomina.total)
        assertEquals(BigDecimal("66.67"), catNomina.porcentaje)
    }

    @Test
    fun testReporteFinancieroDataVacio() {
        val data = ReporteFinancieroData.preparar(emptyList(), 0L, 100L, "Todas", true)

        assertEquals(BigDecimal("0.00"), data.totalGeneral)
        assertEquals(0, data.totalMovimientos)
        assertEquals(0, data.resumenCategorias.size)
    }
}
