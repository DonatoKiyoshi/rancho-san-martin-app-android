package com.rancho.gestion.domain

import com.rancho.gestion.data.entities.RangoPeso
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.concurrent.TimeUnit

/**
 * Pruebas unitarias de domain/BusinessLogic.kt.
 *
 * Es lógica pura de Kotlin (sin Android ni Compose), así que corre directo
 * en la JVM local, sin emulador ni dispositivo:
 *   ./gradlew test
 * o desde Android Studio, clic derecho sobre esta clase → "Run".
 */
class ClasificadorPesoTest {
    private val rangos = listOf(
        RangoPeso(idRangoPeso = "1", limiteInferior = 130.0, limiteSuperior = 220.0, nombreRango = "R1"),
        RangoPeso(idRangoPeso = "2", limiteInferior = 221.0, limiteSuperior = 280.0, nombreRango = "R2")
    )

    @Test
    fun `clasifica un peso dentro del primer rango`() {
        val rango = ClasificadorPeso.clasificar(180.0, rangos)
        assertEquals(130.0, rango?.limiteInferior)
        assertEquals(220.0, rango?.limiteSuperior)
    }

    @Test
    fun `clasifica un peso dentro del segundo rango`() {
        val rango = ClasificadorPeso.clasificar(250.0, rangos)
        assertEquals(221.0, rango?.limiteInferior)
    }

    @Test
    fun `peso fuera de todos los rangos no clasifica`() {
        val rango = ClasificadorPeso.clasificar(50.0, rangos)
        assertEquals(null, rango)
        assertEquals("Sin clasificar", ClasificadorPeso.etiqueta(rango))
    }

    @Test
    fun `etiqueta formatea el rango encontrado`() {
        val rango = ClasificadorPeso.clasificar(200.0, rangos)
        assertEquals("130-220 kg", ClasificadorPeso.etiqueta(rango))
    }
}

class EtapaCalculatorTest {
    private val unDiaMs = TimeUnit.DAYS.toMillis(1)

    @Test
    fun `dentro del rango de acopio no hay alerta`() {
        val entrada = System.currentTimeMillis() - (5 * unDiaMs)
        val estado = EtapaCalculator.calcular(entrada)
        assertEquals(Etapa.ACOPIO, estado.etapa)
        assertFalse(estado.alerta)
    }

    @Test
    fun `en desarrollo y engorda antes del minimo no hay alerta`() {
        val entrada = System.currentTimeMillis() - (30 * unDiaMs)
        val estado = EtapaCalculator.calcular(entrada)
        assertEquals(Etapa.DESARROLLO_ENGORDA, estado.etapa)
        assertFalse(estado.alerta)
    }

    @Test
    fun `cerca del limite de engorda si hay alerta`() {
        val entrada = System.currentTimeMillis() - (70 * unDiaMs)
        val estado = EtapaCalculator.calcular(entrada)
        assertEquals(Etapa.DESARROLLO_ENGORDA, estado.etapa)
        assertTrue(estado.alerta)
    }

    @Test
    fun `pasado el maximo de engorda queda listo para venta con alerta`() {
        val entrada = System.currentTimeMillis() - (90 * unDiaMs)
        val estado = EtapaCalculator.calcular(entrada)
        assertEquals(Etapa.LISTO_VENTA, estado.etapa)
        assertTrue(estado.alerta)
    }
}

class MermaCalculatorTest {
    @Test
    fun `calcula la perdida neta igual que el ejemplo del boceto`() {
        // $20,000 de compra - $8,000 recuperado = $12,000 de perdida neta.
        val perdida = MermaCalculator.calcularPerdida(20000.0, 8000.0)
        assertEquals(12000.0, perdida, 0.0)
    }

    @Test
    fun `si el valor recuperado supera el precio de compra la perdida no es negativa`() {
        val perdida = MermaCalculator.calcularPerdida(5000.0, 9000.0)
        assertEquals(0.0, perdida, 0.0)
    }
}

class JaulaCapacidadTest {
    @Test
    fun `convierte kilogramos a toneladas`() {
        assertEquals(24.0, JaulaCapacidad.toneladas(24000.0), 0.0)
    }

    @Test
    fun `jaula completa al alcanzar el tope de cabezas`() {
        val completa = JaulaCapacidad.estaCompleta(
            cabezasActuales = 110, toneladasActuales = 10.0,
            capacidadCabezas = 110, capacidadToneladas = 24.0, tolerancia = 0.5
        )
        assertTrue(completa)
    }

    @Test
    fun `jaula completa al superar tope de toneladas mas tolerancia`() {
        val completa = JaulaCapacidad.estaCompleta(
            cabezasActuales = 50, toneladasActuales = 24.6,
            capacidadCabezas = 110, capacidadToneladas = 24.0, tolerancia = 0.5
        )
        assertTrue(completa)
    }

    @Test
    fun `jaula no completa dentro de ambos topes`() {
        val completa = JaulaCapacidad.estaCompleta(
            cabezasActuales = 50, toneladasActuales = 10.0,
            capacidadCabezas = 110, capacidadToneladas = 24.0, tolerancia = 0.5
        )
        assertFalse(completa)
    }

    @Test
    fun `disponible calcula cabezas y toneladas restantes`() {
        val (cabezas, toneladas) = JaulaCapacidad.disponible(
            cabezasActuales = 100, toneladasActuales = 20.0,
            capacidadCabezas = 110, capacidadToneladas = 24.0, tolerancia = 0.5
        )
        assertEquals(10, cabezas)
        assertEquals(4.5, toneladas, 0.0)
    }
}
