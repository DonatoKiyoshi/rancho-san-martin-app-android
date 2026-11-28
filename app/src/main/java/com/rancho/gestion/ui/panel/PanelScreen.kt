package com.rancho.gestion.ui.panel

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.domain.EstadoEtapa
import com.rancho.gestion.domain.Etapa
import com.rancho.gestion.domain.EtapaCalculator
import com.rancho.gestion.ui.components.AreteTag
import com.rancho.gestion.ui.components.EtapaBadge
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.flow.combine
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Locale

private val panelLightBg = Color(0xFFF4F7FB)
private val panelLightCard = Color(0xFFFFFFFF)
private val panelDarkBg = Color(0xFF0B0C0E)
private val panelDarkCard = Color(0xFF131416)

private val colorBlue = Color(0xFF3B82F6)
private val colorPurple = Color(0xFF8B5CF6)
private val colorGreen = Color(0xFF22C55E)
private val colorOrange = Color(0xFFF97316)
private val colorRed = Color(0xFFEF4444)
private val colorDarkNavy = Color(0xFF0A2B4E)

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

    val isSystemDark = isSystemInDarkTheme()
    val isAppDark by container.themeManager.isDarkMode.collectAsState(initial = isSystemDark)

    PanelContent(
        nombreUsuario = usuario?.nombre ?: usuario?.nombreUsuario,
        stats = stats,
        alertas = alertas,
        currency = currency,
        isDark = isAppDark,
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
    isDark: Boolean,
    onOpenDrawer: () -> Unit,
    onCabezasActivasClick: () -> Unit,
    onToneladasActivasClick: () -> Unit,
    onMachosClick: () -> Unit,
    onHembrasClick: () -> Unit,
    onEfectivoGanadoClick: () -> Unit,
    onEgresosPeriodoClick: () -> Unit
) {
    val bgColor = if (isDark) panelDarkBg else panelLightBg

    Scaffold(
        containerColor = bgColor
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
        ) {
            PanelHeader(
                nombreUsuario = nombreUsuario,
                isDark = isDark,
                onMenuClick = onOpenDrawer,
                topPadding = padding.calculateTopPadding()
            )

            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                contentPadding = PaddingValues(top = 16.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                if (alertas.isNotEmpty()) {
                    item(span = { GridItemSpan(2) }) {
                        AlertasCard(alertas, isDark)
                    }
                }

                item {
                    StatGridCard(
                        titulo = "Cabezas activas",
                        valor = stats.cabezasActivas.toString(),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_menu_ganado),
                        color = colorBlue,
                        isDark = isDark,
                        onClick = onCabezasActivasClick
                    )
                }
                item {
                    StatGridCard(
                        titulo = "Toneladas activas",
                        valor = String.format(Locale.forLanguageTag("es-MX"), "%.1f", stats.toneladasActivas),
                        icon = Icons.Default.Scale,
                        color = colorPurple,
                        isDark = isDark,
                        onClick = onToneladasActivasClick
                    )
                }
                item {
                    StatGridCard(
                        titulo = "Machos",
                        valor = stats.machos.toString(),
                        icon = Icons.Default.Male,
                        color = colorGreen,
                        isDark = isDark,
                        onClick = onMachosClick
                    )
                }
                item {
                    StatGridCard(
                        titulo = "Hembras",
                        valor = stats.hembras.toString(),
                        icon = Icons.Default.Female,
                        color = colorOrange,
                        isDark = isDark,
                        onClick = onHembrasClick
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    FinancialCard(
                        titulo = "Efectivo del ganado",
                        valor = currency.format(stats.efectivoGanado),
                        icon = ImageVector.vectorResource(id = R.drawable.ic_money_stack),
                        endIcon = Icons.AutoMirrored.Filled.TrendingUp,
                        color = colorGreen,
                        isDark = isDark,
                        onClick = onEfectivoGanadoClick
                    )
                }
                item(span = { GridItemSpan(2) }) {
                    FinancialCard(
                        titulo = "Egresos del periodo",
                        valor = "-" + currency.format(stats.egresosMes),
                        icon = Icons.Default.TrendingDown,
                        endIcon = Icons.Default.Receipt,
                        color = colorRed,
                        isDark = isDark,
                        onClick = onEgresosPeriodoClick
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    nombreUsuario: String?,
    isDark: Boolean,
    onMenuClick: () -> Unit,
    topPadding: Dp
) {
    val headerBg = if (isDark) Color(0xFF050B14) else colorDarkNavy

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(130.dp + topPadding)
            .drawBehind {
                val path = Path().apply {
                    moveTo(0f, 0f)
                    lineTo(size.width, 0f)
                    lineTo(size.width, size.height * 0.75f)
                    quadraticTo(
                        size.width * 0.5f, size.height * 1.1f,
                        0f, size.height * 0.75f
                    )
                    close()
                }
                drawPath(
                    path = path,
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            headerBg,
                            if (isDark) Color(0xFF0B192C) else Color(0xFF153A66)
                        )
                    )
                )
                if (isDark) {
                    val linePath = Path().apply {
                        moveTo(0f, size.height * 0.75f)
                        quadraticTo(
                            size.width * 0.5f, size.height * 1.1f,
                            size.width, size.height * 0.75f
                        )
                    }
                    drawPath(
                        path = linePath,
                        color = colorBlue,
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 4f)
                    )
                }
            }
    ) {
        Column(
            modifier = Modifier
                .padding(top = topPadding + 16.dp, start = 20.dp, end = 20.dp)
                .fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                IconButton(
                    onClick = onMenuClick,
                    modifier = Modifier.offset(x = (-12).dp, y = (-12).dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Menu,
                        contentDescription = "Menú",
                        tint = Color.White
                    )
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 4.dp, top = 4.dp)
                ) {
                    Text(
                        text = saludo(nombreUsuario),
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CalendarMonth,
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.7f),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = fechaDeHoy(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.7f)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun StatGridCard(
    titulo: String,
    valor: String,
    icon: ImageVector,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) panelDarkCard else panelLightCard
    val textColor = if (isDark) Color.White else Color.Black
    val labelColor = if (isDark) Color.LightGray else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isDark) 0.dp else 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(
                            Brush.linearGradient(
                                colors = listOf(color.copy(alpha = 0.2f), color.copy(alpha = 0.1f))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = titulo,
                        tint = color,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(12.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = valor,
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = null,
                    tint = labelColor,
                    modifier = Modifier.size(20.dp).padding(bottom = 4.dp)
                )
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = titulo,
                fontSize = 13.sp,
                color = labelColor,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(12.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.6f)
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(color, color.copy(alpha = 0.2f))
                        )
                    )
            )
        }
    }
}

@Composable
private fun FinancialCard(
    titulo: String,
    valor: String,
    icon: ImageVector,
    endIcon: ImageVector,
    color: Color,
    isDark: Boolean,
    onClick: () -> Unit
) {
    val cardBg = if (isDark) panelDarkCard else panelLightCard
    val textColor = if (isDark) Color.White else Color.Black
    val labelColor = if (isDark) Color.LightGray else Color.Gray

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() },
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isDark) 0.dp else 4.dp)
    ) {
        Row(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(CircleShape)
                    .background(color),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = titulo,
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            Spacer(modifier = Modifier.width(16.dp))

            Box(
                modifier = Modifier
                    .width(1.dp)
                    .height(40.dp)
                    .background(if (isDark) Color.DarkGray else Color(0xFFE5E7EB))
            )

            Spacer(modifier = Modifier.width(16.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = valor,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = textColor
                )
                Text(
                    text = titulo,
                    fontSize = 13.sp,
                    color = labelColor,
                    fontWeight = FontWeight.Medium
                )
            }

            Box(
                modifier = Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = endIcon,
                    contentDescription = null,
                    tint = color,
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}

@Composable
private fun AlertasCard(alertas: List<Pair<Animal, EstadoEtapa>>, isDark: Boolean) {
    val cardBg = if (isDark) panelDarkCard else panelLightCard
    val textColor = if (isDark) Color.White else Color.Black
    val subtitleColor = if (isDark) Color.LightGray else Color.Gray

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = cardBg),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isDark) 0.dp else 4.dp)
    ) {
        Column(Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(32.dp)
                        .clip(CircleShape)
                        .background(colorRed.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text("!", color = colorRed, fontWeight = FontWeight.Black)
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "Necesitan atención (${alertas.size})",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = textColor
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
                        color = subtitleColor,
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
            nombreUsuario = "Administrador",
            stats = dummyStats,
            alertas = listOf(dummyAnimal to dummyEstado),
            currency = NumberFormat.getCurrencyInstance(Locale("es", "MX")),
            isDark = false,
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
