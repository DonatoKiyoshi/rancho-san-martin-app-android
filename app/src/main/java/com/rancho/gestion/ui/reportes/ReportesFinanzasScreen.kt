package com.rancho.gestion.ui.reportes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.finanzas.categoriaLabel
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.Periodo
import java.text.NumberFormat
import java.util.Locale

@Composable
fun ReportesFinanzasScreen(container: AppContainer, onBack: () -> Unit) {
    var periodo by remember { mutableStateOf(Periodo.MENSUAL) }
    var categoriaFiltro by remember { mutableStateOf<CategoriaEgreso?>(null) } // null = todas

    val (desde, hasta) = remember(periodo) { DateUtils.rangoPara(periodo) }
    val egresos by produceState(initialValue = emptyList<Egreso>(), container, desde, hasta) {
        value = container.finanzasRepository.porRangoOnce(desde, hasta)
    }

    ReportesFinanzasContent(
        periodo = periodo,
        onPeriodoChange = { periodo = it },
        categoriaFiltro = categoriaFiltro,
        onCategoriaFiltroClick = {
            val opciones = listOf<CategoriaEgreso?>(null) + CategoriaEgreso.values().toList()
            val idx = opciones.indexOf(categoriaFiltro)
            categoriaFiltro = opciones[(idx + 1) % opciones.size]
        },
        desde = desde,
        hasta = hasta,
        egresos = egresos,
        onBack = onBack
    )
}

@Composable
private fun ReportesFinanzasContent(
    periodo: Periodo,
    onPeriodoChange: (Periodo) -> Unit,
    categoriaFiltro: CategoriaEgreso?,
    onCategoriaFiltroClick: () -> Unit,
    desde: Long,
    hasta: Long,
    egresos: List<Egreso>,
    onBack: () -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val filtrados = remember(egresos, categoriaFiltro) {
        egresos.filter { categoriaFiltro == null || it.categoria == categoriaFiltro }
    }
    val total = filtrados.sumOf { it.montoTotal }
    val porCategoria = CategoriaEgreso.values().map { c ->
        categoriaLabel(c) to egresos.filter { it.categoria == c }.sumOf { it.montoTotal }
    }

    Scaffold(topBar = { BackTopBar(title = "Reportes de Finanzas", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Periodo.values().forEach { p ->
                    FilterChip(selected = periodo == p, onClick = { onPeriodoChange(p) }, label = { Text(periodoLabel(p)) })
                }
            }
            Spacer(Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AssistChip(
                    onClick = onCategoriaFiltroClick,
                    label = { Text("Categoria: ${categoriaFiltro?.let { categoriaLabel(it) } ?: "todas"}") }
                )
            }
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Resumen (${DateUtils.formatDay(desde)} – ${DateUtils.formatDay(hasta)})", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total del periodo")
                    Text(currency.format(total), fontWeight = FontWeight.Bold)
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Movimientos")
                    Text(filtrados.size.toString())
                }
            }

            Spacer(Modifier.height(20.dp))
            Text("Gasto por categoría", fontWeight = FontWeight.Bold)
            SimpleBarChart(data = porCategoria)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun ReportesFinanzasPreview() {
    MaterialTheme {
        ReportesFinanzasContent(
            periodo = Periodo.MENSUAL,
            onPeriodoChange = {},
            categoriaFiltro = null,
            onCategoriaFiltroClick = {},
            desde = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
            hasta = System.currentTimeMillis(),
            egresos = listOf(
                Egreso(idEgreso = 1, categoria = CategoriaEgreso.ALIMENTO, montoTotal = 5000.0, fechaEgreso = System.currentTimeMillis(), idUsuario = 1, descripcion = "Pago")
            ),
            onBack = {}
        )
    }
}
