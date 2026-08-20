package com.rancho.gestion.ui.reportes

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.Periodo

@Composable
fun ReportesGanadoScreen(container: AppContainer, onBack: () -> Unit) {
    var periodo by remember { mutableStateOf(Periodo.MENSUAL) }
    var proveedorFiltro by remember { mutableStateOf("todos") }

    val activos by container.ganadoRepository.activos().collectAsState(initial = emptyList())
    val archivados by container.ganadoRepository.archivados().collectAsState(initial = emptyList())
    val proveedores by container.ganadoRepository.proveedores().collectAsState(initial = emptyList())

    val (desde, hasta) = remember(periodo) { DateUtils.rangoPara(periodo) }

    val entradas = remember(activos, archivados, desde, hasta, proveedorFiltro) {
        (activos + archivados).filter {
            it.fechaEntrada in desde..hasta &&
                (proveedorFiltro == "todos" || it.proveedor == proveedorFiltro)
        }
    }
    
    val ventas = remember(archivados, desde, hasta) {
        archivados.filter {
            it.estadoInventario == EstadoInventario.VENDIDO && (it.fechaBaja ?: 0) in desde..hasta
        }
    }
    
    val muertes = remember(archivados, desde, hasta) {
        archivados.filter { 
            it.estadoInventario == EstadoInventario.MUERTO && (it.fechaBaja ?: 0) in desde..hasta 
        }
    }
    
    ReportesGanadoContent(
        periodo = periodo,
        onPeriodoChange = { periodo = it },
        proveedorFiltro = proveedorFiltro,
        onProveedorFiltroClick = {
            val opciones = listOf("todos") + proveedores
            val idx = opciones.indexOf(proveedorFiltro)
            proveedorFiltro = opciones[(idx + 1) % opciones.size]
        },
        desde = desde,
        hasta = hasta,
        entradas = entradas,
        ventas = ventas,
        muertes = muertes,
        totalActivos = activos.size,
        totalArchivados = archivados.size,
        onBack = onBack
    )
}

@Composable
private fun ReportesGanadoContent(
    periodo: Periodo,
    onPeriodoChange: (Periodo) -> Unit,
    proveedorFiltro: String,
    onProveedorFiltroClick: () -> Unit,
    desde: Long,
    hasta: Long,
    entradas: List<Animal>,
    ventas: List<Animal>,
    muertes: List<Animal>,
    totalActivos: Int,
    totalArchivados: Int,
    onBack: () -> Unit
) {
    val machos = entradas.count { it.sexo == Sexo.MACHO }
    val hembras = entradas.count { it.sexo == Sexo.HEMBRA }

    Scaffold(topBar = { BackTopBar(title = "Reportes de Ganado", onBack = onBack) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            FiltroChips(
                periodo = periodo,
                onPeriodo = onPeriodoChange,
                segundoLabel = "Proveedor: $proveedorFiltro",
                onSegundoClick = onProveedorFiltroClick
            )
            Spacer(Modifier.height(12.dp))

            SectionCard {
                Text("Resumen (${DateUtils.formatDay(desde)} – ${DateUtils.formatDay(hasta)})", fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(10.dp))
                ResumenLinea("Entradas registradas", entradas.size)
                ResumenLinea("  · Machos", machos)
                ResumenLinea("  · Hembras", hembras)
                ResumenLinea("Ventas registradas", ventas.size)
                ResumenLinea("Muertes / merma", muertes.size)
                ResumenLinea("Activos actualmente", totalActivos)
                ResumenLinea("Total archivado histórico", totalArchivados)
            }

            Spacer(Modifier.height(20.dp))
            Text("Entradas vs. Ventas vs. Muertes", fontWeight = FontWeight.Bold)
            SimpleBarChart(
                data = listOf(
                    "Entradas" to entradas.size.toDouble(),
                    "Ventas" to ventas.size.toDouble(),
                    "Muertes" to muertes.size.toDouble()
                )
            )
        }
    }
}

@Composable
private fun ResumenLinea(label: String, valor: Int) {
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label)
        Text(valor.toString(), fontWeight = FontWeight.Medium)
    }
}

@Composable
fun FiltroChips(
    periodo: Periodo,
    onPeriodo: (Periodo) -> Unit,
    segundoLabel: String,
    onSegundoClick: () -> Unit
) {
    Column {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Periodo.values().forEach { p ->
                FilterChip(
                    selected = periodo == p,
                    onClick = { onPeriodo(p) },
                    label = { Text(periodoLabel(p)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AssistChip(onClick = onSegundoClick, label = { Text(segundoLabel) })
        }
    }
}

fun periodoLabel(p: Periodo): String = when (p) {
    Periodo.DIARIO -> "Diario"
    Periodo.SEMANAL -> "Semanal"
    Periodo.MENSUAL -> "Mensual"
    Periodo.ANUAL -> "Anual"
}

@Preview(showBackground = true)
@Composable
fun ReportesGanadoPreview() {
    MaterialTheme {
        ReportesGanadoContent(
            periodo = Periodo.MENSUAL,
            onPeriodoChange = {},
            proveedorFiltro = "todos",
            onProveedorFiltroClick = {},
            desde = System.currentTimeMillis() - 30L * 24 * 60 * 60 * 1000,
            hasta = System.currentTimeMillis(),
            entradas = emptyList(),
            ventas = emptyList(),
            muertes = emptyList(),
            totalActivos = 100,
            totalArchivados = 50,
            onBack = {}
        )
    }
}
