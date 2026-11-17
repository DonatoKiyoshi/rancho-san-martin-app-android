package com.rancho.gestion.util

import com.rancho.gestion.data.entities.Animal
import com.rancho.gestion.data.entities.EstadoInventario
import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.entities.TipoArete
import org.junit.Assert.assertEquals
import org.junit.Test

class ReporteGanadoDataTest {

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
        ),
        Animal(
            numeroArete = "1001",
            tipoArete = TipoArete.OTRO,
            color = "Hosco",
            sexo = Sexo.MACHO,
            proveedor = "Proveedor B",
            precioCompra = 10000.0,
            pesoEntrada = 250.0,
            idRangoPeso = "2",
            estadoInventario = EstadoInventario.MUERTO,
            idUsuarioRegistro = "1"
        )
    )

    private val sampleRangos = listOf(
        RangoPeso(idRangoPeso = "1", limiteInferior = 130.0, limiteSuperior = 220.0, nombreRango = "Becerro"),
        RangoPeso(idRangoPeso = "2", limiteInferior = 221.0, limiteSuperior = 350.0, nombreRango = "Añojo")
    )

    @Test
    fun testCalculosReporteGanadoCorrectos() {
        val stats = ReporteGanadoDataCalculator.calcular(sampleAnimales, sampleRangos)

        // Totales generales
        assertEquals(3, stats.totalAnimales)
        assertEquals(750.0, stats.pesoTotal, 0.01)
        assertEquals(250.0, stats.pesoPromedio, 0.01)

        // Conteo por estado
        assertEquals(1, stats.totalActivos)
        assertEquals(1, stats.totalVendidos)
        assertEquals(1, stats.totalMuertos)

        assertEquals(33.33, stats.pctActivos, 0.1)
        assertEquals(33.33, stats.pctVendidos, 0.1)
        assertEquals(33.33, stats.pctMuertos, 0.1)

        // Conteo por sexo (2 Machos, 1 Hembra)
        val machos = stats.distribucionSexo.first { it.sexo == Sexo.MACHO }
        val hembras = stats.distribucionSexo.first { it.sexo == Sexo.HEMBRA }
        assertEquals(2, machos.cantidad)
        assertEquals(1, hembras.cantidad)

        // Conteo por rango (1 Becerro [200kg], 2 Añojo [300kg, 250kg])
        val becerro = stats.distribucionRangos.first { it.idRangoPeso == "1" }
        val anojo = stats.distribucionRangos.first { it.idRangoPeso == "2" }
        assertEquals(1, becerro.cantidad)
        assertEquals(2, anojo.cantidad)

        // Tipo de Arete (2 SADER, 1 OTRO)
        val sader = stats.distribucionTipoArete.first { it.tipoArete == TipoArete.SADER }
        val otro = stats.distribucionTipoArete.first { it.tipoArete == TipoArete.OTRO }
        assertEquals(2, sader.cantidad)
        assertEquals(1, otro.cantidad)
    }
}
