package com.rancho.gestion.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.domain.EstadoEtapa
import com.rancho.gestion.domain.Etapa
import com.rancho.gestion.domain.EtapaCalculator
import com.rancho.gestion.ui.components.AreteTag
import com.rancho.gestion.ui.components.EtapaBadge
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.components.StatCard
import com.rancho.gestion.ui.theme.RanchoAccent
import com.rancho.gestion.ui.theme.RanchoCard
import com.rancho.gestion.ui.theme.RanchoError
import com.rancho.gestion.ui.theme.RanchoSuccess
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private data class PanelStats(
    val cabezasActivas: Int,
    val toneladasActivas: Double,
    val machos: Int,
    val hembras: Int,
    val efectivoGanado: Double,
    val egresosMes: Double
)

@Composable
fun PanelScreen(
    container: AppContainer, 
    onOpenDrawer: () -> Unit,
    onCabezasActivasClick: () -> Unit = {},
    onToneladasActivasClick: () -> Unit = {},
    onMachosClick: () -> Unit = {},
    onHembrasClick: () -> Unit = {},
    onEfectivoGanadoClick: () -> Unit = {},
    onEgresosPeriodoClick: () -> Unit = {}
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val usuario by SessionManager.usuarioActual.collectAsState()

    val stats by produceState(initialValue = PanelStats(0, 0.0, 0, 0, 0.0, 0.0), container) {
        val (inicioMes, ahora) = DateUtils.rangoPara(com.rancho.gestion.util.Periodo.MENSUAL)
        combine(
            container.ganadoRepository.activos(),
            container.finanzasRepository.totalPorRango(inicioMes, ahora)
        ) { activos, egresosMes ->
            val cabezasActivas = activos.size
            val toneladas = activos.sumOf { it.pesoEntrada } / 1000.0
            val machos = activos.count { it.sexo == Sexo.MACHO }
            val hembras = activos.count { it.sexo == Sexo.HEMBRA }
            val efectivoGanado = activos.sumOf { it.precioCompra }
            
            PanelStats(cabezasActivas, toneladas, machos, hembras, efectivoGanado, egresosMes)
        }.collect { value = it }
    }

    val activos by container.ganadoRepository.activos().collectAsState(initial = emptyList())
    val alertas = remember(activos) {
        activos
            .map { it to EtapaCalculator.calcular(it.fechaEntrada) }
            .filter { (_, estado) -> estado.alerta }
            .sortedByDescending { (_, estado) -> estado.diasEnRancho }
            .take(5)
    }

    PanelContent(
        nombreUsuario = usuario?.nombre ?: usuario?.nombreUsuario,
        stats = stats,
        alertas = alertas,
        currency = currency,
        onOpenDrawer = onOpenDrawer,
        onCabezasActivasClick = onCabezasActivasClick,
        onToneladasActivasClick = onToneladasActivasClick,
        onMachosClick = onMachosClick,
        onHembrasClick = onHembrasClick,
        onEfectivoGanadoClick = onEfectivoGanadoClick,
        onEgresosPeriodoClick = onEgresosPeriodoClick
    )
}

@Composable
private fun PanelContent(
    nombreUsuario: String?,
    stats: PanelStats,
    alertas: List<Pair<Animal, EstadoEtapa>>,
    currency: NumberFormat,
    onOpenDrawer: () -> Unit,
    onCabezasActivasClick: () -> Unit,
    onToneladasActivasClick: () -> Unit,
    onMachosClick: () -> Unit,
    onHembrasClick: () -> Unit,
    onEfectivoGanadoClick: () -> Unit,
    onEgresosPeriodoClick: () -> Unit
) {
    Scaffold(topBar = { HomeTopBar(title = "Panel", onHomeClick = onOpenDrawer) }) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier
                .padding(padding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(vertical = 16.dp, horizontal = 0.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item(span = { GridItemSpan(2) }) {
                Column(Modifier.padding(bottom = 8.dp)) {
                    Text(
                        text = saludo(nombreUsuario),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.ExtraBold
                    )
                    Text(
                        text = fechaDeHoy(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = RanchoTextSecondary
                    )
                }
            }

            if (alertas.isNotEmpty()) {
                item(span = { GridItemSpan(2) }) {
                    AlertasCard(alertas)
                }
            }

            item {
                StatCard(
                    titulo = "Cabezas activas",
                    valor = stats.cabezasActivas.toString(),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_menu_ganado),
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onCabezasActivasClick() }
                )
            }
            item {
                StatCard(
                    titulo = "Toneladas activas",
                    valor = String.format(Locale.forLanguageTag("es-MX"), "%.1f", stats.toneladasActivas),
                    icon = Icons.Default.Scale,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onToneladasActivasClick() }
                )
            }
            item {
                StatCard(
                    titulo = "Machos",
                    valor = stats.machos.toString(),
                    icon = Icons.Default.Male,
                    accentColor = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth().clickable { onMachosClick() }
                )
            }
            item {
                StatCard(
                    titulo = "Hembras",
                    valor = stats.hembras.toString(),
                    icon = Icons.Default.Female,
                    accentColor = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.fillMaxWidth().clickable { onHembrasClick() }
                )
            }
            item(span = { GridItemSpan(2) }) {
                StatCard(
                    titulo = "Efectivo del ganado",
                    valor = currency.format(stats.efectivoGanado),
                    icon = ImageVector.vectorResource(id = R.drawable.ic_money_stack),
                    accentColor = RanchoSuccess,
                    modifier = Modifier.fillMaxWidth().clickable { onEfectivoGanadoClick() }
                )
            }
            item(span = { GridItemSpan(2) }) {
                StatCard(
                    titulo = "Egresos del periodo",
                    valor = "-" + currency.format(stats.egresosMes),
                    icon = Icons.Default.TrendingDown,
                    accentColor = RanchoError,
                    modifier = Modifier.fillMaxWidth().clickable { onEgresosPeriodoClick() }
                )
            }
        }
    }
}

@Composable
private fun AlertasCard(alertas: List<Pair<Animal, EstadoEtapa>>) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = RanchoCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(RanchoAccent.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = RanchoAccent, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Necesitan atención (${alertas.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold
                )
            }
            Spacer(Modifier.height(16.dp))
            alertas.forEachIndexed { index, (animal, _) ->
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    AreteTag(animal.numeroArete, animal.tipoArete)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = "Fase: ${animal.fase.name}",
                        style = MaterialTheme.typography.bodySmall,
                        color = RanchoTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                    EtapaBadge(animal.fase)
                }
                if (index != alertas.lastIndex) Spacer(Modifier.height(12.dp))
            }
        }
    }
}

private fun saludo(nombre: String?): String {
    val quien = nombre?.takeIf { it.isNotBlank() } ?: "de nuevo"
    return "Hola, $quien"
}

private fun fechaDeHoy(): String {
    val formatter = SimpleDateFormat("EEEE d 'de' MMMM", Locale("es", "MX"))
    val texto = formatter.format(java.util.Date())
    return texto.replaceFirstChar { it.uppercase() }
}

@Preview(showBackground = true, widthDp = 360)
@Composable
fun PanelPreview() {
    val dummyStats = PanelStats(120, 45.5, 70, 50, 1440000.0, 85000.0)
    val dummyAnimal = Animal(numeroArete = "484101", color = "Negro", sexo = Sexo.MACHO, pesoEntrada = 450.0, precioCompra = 12000.0, proveedor = "P1", idUsuarioRegistro = "1", idRangoPeso = null)
    val dummyEstado = EstadoEtapa(Etapa.ACOPIO, 45, true, "Alerta")

    MaterialTheme {
        PanelContent(
            nombreUsuario = "Admin",
            stats = dummyStats,
            alertas = listOf(dummyAnimal to dummyEstado),
            currency = NumberFormat.getCurrencyInstance(Locale("es", "MX")),
            onOpenDrawer = {},
            onCabezasActivasClick = {},
            onToneladasActivasClick = {},
            onMachosClick = {},
            onHembrasClick = {},
            onEfectivoGanadoClick = {},
            onEgresosPeriodoClick = {}
        )
    }
}
