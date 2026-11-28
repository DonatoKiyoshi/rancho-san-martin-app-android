package com.rancho.gestion.ui.finanzas

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.lerp
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.components.SimpleFormDialog
import com.rancho.gestion.ui.components.ThousandsSeparatorVisualTransformation
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.Periodo
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale

private data class TotalesFinanzas(
    val total: Double,
    val nomina: Double,
    val gasolina: Double,
    val alimento: Double,
    val farmacos: Double,
    val otros: Double
)

data class CategoryStyle(
    val accentColor: Color,
    val iconRes: Int
)

fun getCategoryStyle(categoria: CategoriaEgreso): CategoryStyle = when (categoria) {
    CategoriaEgreso.NOMINA -> CategoryStyle(
        accentColor = Color(0xFF10B981), // Esmeralda / Verde brillante
        iconRes = R.drawable.ic_nomina_finanzas
    )
    CategoriaEgreso.GASOLINA -> CategoryStyle(
        accentColor = Color(0xFFEA580C), // Naranja / Terracota
        iconRes = R.drawable.ic_gasolina_finanzas
    )
    CategoriaEgreso.ALIMENTO -> CategoryStyle(
        accentColor = Color(0xFFD97706), // Ámbar / Dorado
        iconRes = R.drawable.ic_alimentos_finanzas
    )
    CategoriaEgreso.FARMACOS -> CategoryStyle(
        accentColor = Color(0xFF8B5CF6), // Púrpura / Íñdigo brillante
        iconRes = R.drawable.ic_farmacos_finanzas
    )
    CategoriaEgreso.OTROS -> CategoryStyle(
        accentColor = Color(0xFF38BDF8), // Azul brillante
        iconRes = R.drawable.ic_otros_finanzas
    )
}

@Composable
fun FinanzasScreen(container: AppContainer, onOpenDrawer: () -> Unit) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")) }
    var mostrarDialogo by remember { mutableStateOf(false) }
    var categoriaPreseleccionada by remember { mutableStateOf<CategoriaEgreso?>(null) }
    var egresoAEliminar by remember { mutableStateOf<Egreso?>(null) }
    val scope = rememberCoroutineScope()
    
    val fechaReferencia = System.currentTimeMillis()
    val (inicioSemana, finSemana) = remember(fechaReferencia) { getRangoSemanaGlobal(fechaReferencia) }

    val totales by produceState(
        initialValue = TotalesFinanzas(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        container, inicioSemana, finSemana
    ) {
        combine(
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.NOMINA, inicioSemana, finSemana),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.GASOLINA, inicioSemana, finSemana),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.ALIMENTO, inicioSemana, finSemana),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.FARMACOS, inicioSemana, finSemana),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.OTROS, inicioSemana, finSemana)
        ) { nomina, gasolina, alimento, farmacos, otros ->
            TotalesFinanzas(
                total = nomina + gasolina + alimento + farmacos + otros,
                nomina = nomina, gasolina = gasolina, alimento = alimento, farmacos = farmacos, otros = otros
            )
        }.collect { value = it }
    }

    val egresosRecientes by container.finanzasRepository.porRango(inicioSemana, finSemana).collectAsState(initial = emptyList())
    val isAdmin = SessionManager.isAdmin()
    val isDark by container.themeManager.isDarkMode.collectAsState()

    FinanzasContent(
        totales = totales,
        egresosRecientes = egresosRecientes,
        currency = currency,
        fechaActual = fechaReferencia,
        inicioSemana = inicioSemana,
        finSemana = finSemana,
        isAdmin = isAdmin,
        isDark = isDark,
        onOpenDrawer = onOpenDrawer,
        onRegistrarEgresoClick = { 
            categoriaPreseleccionada = null
            mostrarDialogo = true 
        },
        onCategoryClick = { categoria ->
            categoriaPreseleccionada = categoria
            mostrarDialogo = true
        },
        onDeleteEgreso = { egresoAEliminar = it }
    )

    if (mostrarDialogo) {
        RegistrarEgresoDialog(
            preselectedCategoria = categoriaPreseleccionada,
            onDismiss = { mostrarDialogo = false },
            onSave = { categoria, monto, descripcion, empleado, dias ->
                container.finanzasRepository.registrarEgreso(
                    categoria = categoria,
                    monto = monto,
                    descripcion = descripcion ?: "",
                    idUsuario = SessionManager.currentUserId(),
                    empleadoNombre = empleado,
                    diasTrabajados = dias
                )
            }
        )
    }

    if (egresoAEliminar != null) {
        AlertDialog(
            onDismissRequest = { egresoAEliminar = null },
            icon = {
                Icon(
                    imageVector = Icons.Default.Info,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
            },
            title = {
                Text(
                    "Eliminar Egreso",
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar este egreso?",
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = egresoAEliminar ?: return@Button
                        egresoAEliminar = null
                        scope.launch {
                            container.finanzasRepository.eliminarEgreso(target)
                        }
                    }
                ) {
                    Text("Sí")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { egresoAEliminar = null }) {
                    Text("No")
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun FinanzasContent(
    totales: TotalesFinanzas,
    egresosRecientes: List<Egreso>,
    currency: NumberFormat,
    fechaActual: Long,
    inicioSemana: Long,
    finSemana: Long,
    isAdmin: Boolean,
    isDark: Boolean,
    onOpenDrawer: () -> Unit,
    onRegistrarEgresoClick: () -> Unit,
    onCategoryClick: (CategoriaEgreso) -> Unit,
    onDeleteEgreso: (Egreso) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    val collapseThresholdPx = 200f * density

    val bgColor = if (isDark) Color(0xFF0F111A) else Color(0xFFF8F8FD)
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)

    val collapseProgress by remember {
        derivedStateOf {
            if (listState.firstVisibleItemIndex > 0) {
                1f
            } else {
                (listState.firstVisibleItemScrollOffset.toFloat() / collapseThresholdPx).coerceIn(0f, 1f)
            }
        }
    }

    Scaffold(
        topBar = { HomeTopBar(title = "Finanzas", onHomeClick = onOpenDrawer) },
        containerColor = bgColor,
        floatingActionButton = {
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(24.dp))
                    .background(Brush.linearGradient(listOf(Color(0xFF8B5CF6), Color(0xFF4F46E5))))
                    .clickable { onRegistrarEgresoClick() }
                    .padding(horizontal = 24.dp, vertical = 16.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = Color.White)
                    Spacer(Modifier.width(8.dp))
                    Text("Registrar Egreso", fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(bgColor)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sticky Header con colapso progresivo y continuo al hacer scroll
            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(bgColor)
                        .padding(vertical = 8.dp)
                ) {
                    // Fila 1: Total (se expande progresivamente a 100%) + Nómina (se desvanece progresivamente)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp * (1f - collapseProgress)),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FinanzasStatCard(
                            titulo = "Total",
                            valor = currency.format(totales.total),
                            iconRes = R.drawable.ic_total_finanzas,
                            isTotal = true,
                            collapseProgress = collapseProgress,
                            isDark = isDark,
                            modifier = Modifier.weight(1f + collapseProgress * 8f)
                        )

                        if (collapseProgress < 0.98f) {
                            Box(
                                modifier = Modifier
                                    .weight((1f - collapseProgress).coerceAtLeast(0.001f))
                                    .graphicsLayer {
                                        alpha = (1f - collapseProgress * 1.2f).coerceIn(0f, 1f)
                                    }
                            ) {
                                FinanzasStatCard(
                                    titulo = "Nómina",
                                    valor = currency.format(totales.nomina),
                                    iconRes = R.drawable.ic_nomina_finanzas,
                                    categoryStyle = getCategoryStyle(CategoriaEgreso.NOMINA),
                                    isDark = isDark,
                                    onClick = { onCategoryClick(CategoriaEgreso.NOMINA) }
                                )
                            }
                        }
                    }

                    // Filas 2 y 3: Tarjetas de Categorías (se contraen y desvanecen progresivamente al hacer scroll)
                    if (collapseProgress < 0.98f) {
                        val remainingAlpha = (1f - collapseProgress * 1.2f).coerceIn(0f, 1f)
                        val remainingHeight = (230.dp * (1f - collapseProgress)).coerceAtLeast(0.dp)

                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(remainingHeight)
                                .graphicsLayer {
                                    alpha = remainingAlpha
                                    scaleY = 1f - collapseProgress * 0.15f
                                    transformOrigin = TransformOrigin(0.5f, 0f)
                                }
                        ) {
                            Column {
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    FinanzasStatCard(
                                        titulo = "Gasolina",
                                        valor = currency.format(totales.gasolina),
                                        iconRes = R.drawable.ic_gasolina_finanzas,
                                        categoryStyle = getCategoryStyle(CategoriaEgreso.GASOLINA),
                                        modifier = Modifier.weight(1f),
                                        isDark = isDark,
                                        onClick = { onCategoryClick(CategoriaEgreso.GASOLINA) }
                                    )
                                    FinanzasStatCard(
                                        titulo = "Fármacos",
                                        valor = currency.format(totales.farmacos),
                                        iconRes = R.drawable.ic_farmacos_finanzas,
                                        categoryStyle = getCategoryStyle(CategoriaEgreso.FARMACOS),
                                        modifier = Modifier.weight(1f),
                                        isDark = isDark,
                                        onClick = { onCategoryClick(CategoriaEgreso.FARMACOS) }
                                    )
                                }
                                Spacer(Modifier.height(16.dp))
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    FinanzasStatCard(
                                        titulo = "Alimento",
                                        valor = currency.format(totales.alimento),
                                        iconRes = R.drawable.ic_alimentos_finanzas,
                                        categoryStyle = getCategoryStyle(CategoriaEgreso.ALIMENTO),
                                        modifier = Modifier.weight(1f),
                                        isDark = isDark,
                                        onClick = { onCategoryClick(CategoriaEgreso.ALIMENTO) }
                                    )
                                    FinanzasStatCard(
                                        titulo = "Otros",
                                        valor = currency.format(totales.otros),
                                        iconRes = R.drawable.ic_otros_finanzas,
                                        categoryStyle = getCategoryStyle(CategoriaEgreso.OTROS),
                                        modifier = Modifier.weight(1f),
                                        isDark = isDark,
                                        onClick = { onCategoryClick(CategoriaEgreso.OTROS) }
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .width(4.dp)
                                .height(40.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(Color(0xFF8B5CF6))
                        )
                        Spacer(Modifier.width(12.dp))
                        Column {
                            val tz = java.util.TimeZone.getTimeZone("America/Mexico_City")
                            val cal = Calendar.getInstance(tz).apply { timeInMillis = fechaActual }
                            cal.firstDayOfWeek = Calendar.SUNDAY
                            val semanaDelMes = cal.get(Calendar.WEEK_OF_MONTH)
                            val nombreMes = java.text.SimpleDateFormat("MMMM", java.util.Locale("es", "MX")).apply { timeZone = tz }.format(cal.time)
                            
                            Text(
                                text = "Movimientos de la semana $semanaDelMes del mes de $nombreMes",
                                style = TextStyle(
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor
                                )
                            )
                            Text(
                                text = "${cal.get(Calendar.YEAR)}",
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = if (isDark) Color(0xFFA0AEC0) else Color(0xFF64748B)
                                )
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            if (egresosRecientes.isEmpty()) {
                item {
                    Text(
                        text = "Aún no hay egresos registrados esta semana.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(egresosRecientes, key = { it.idEgreso }) { egreso ->
                val esCorteVigente = egreso.fechaEgreso in inicioSemana..finSemana

                EgresoRow(
                    egreso = egreso,
                    currency = currency,
                    canDelete = isAdmin && esCorteVigente,
                    isDark = isDark,
                    onDelete = { onDeleteEgreso(egreso) }
                )
            }

            item { Spacer(Modifier.height(100.dp)) }
        }
    }
}

@Composable
private fun FinanzasStatCard(
    titulo: String,
    valor: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    collapseProgress: Float = 0f,
    categoryStyle: CategoryStyle? = null,
    isDark: Boolean,
    onClick: (() -> Unit)? = null
) {
    val cardBgColor = if (isDark) Color(0xFF1A1C29) else Color.White
    val borderColor = if (isDark) Color(0xFF2A2D3D) else Color(0xFFE2E8F0)
    
    val shape = RoundedCornerShape(22.dp)
    val cardHeight = if (isTotal) {
        lerp(100.dp, 78.dp, collapseProgress)
    } else {
        100.dp
    }

    val centeredAlpha = if (isTotal) {
        ((collapseProgress - 0.25f) / 0.75f).coerceIn(0f, 1f)
    } else 0f
    val standardAlpha = if (isTotal) {
        (1f - collapseProgress * 1.5f).coerceIn(0f, 1f)
    } else 1f

    val cardModifier = if (onClick != null) {
        modifier.fillMaxWidth().clip(shape).clickable(onClick = onClick)
    } else {
        modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier,
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isTotal) Color.Transparent else cardBgColor
        ),
        border = if (isTotal || isDark) null else BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTotal) 8.dp else if (isDark) 0.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isTotal) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF8B5CF6), Color(0xFF3B82F6))
                            )
                        )
                    } else Modifier.drawBehind {
                        if (categoryStyle != null) {
                            val radius = size.minDimension * 0.75f
                            drawCircle(
                                color = categoryStyle.accentColor.copy(alpha = if (isDark) 0.15f else 0.1f),
                                radius = radius,
                                center = androidx.compose.ui.geometry.Offset(size.width, size.height)
                            )
                        }
                    }
                )
                .height(cardHeight)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            if (isTotal) {
                // Layout centrado para Total (se desvanece entrando conforme collapseProgress -> 1)
                if (centeredAlpha > 0f) {
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = centeredAlpha },
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = iconRes),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.9f),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(Modifier.width(12.dp))
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = valor,
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Total de Egresos",
                                style = TextStyle(
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }
                    }
                }

                // Layout estándar para Total (se desvanece saliendo conforme collapseProgress -> 1)
                if (standardAlpha > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer { alpha = standardAlpha }
                    ) {
                        Column(modifier = Modifier.align(Alignment.TopStart)) {
                            Text(
                                text = valor,
                                style = TextStyle(
                                    fontSize = 24.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White
                                )
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                text = titulo,
                                style = TextStyle(
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.White.copy(alpha = 0.85f)
                                )
                            )
                        }

                        Icon(
                            imageVector = ImageVector.vectorResource(id = iconRes),
                            contentDescription = null,
                            tint = Color.White.copy(alpha = 0.6f),
                            modifier = Modifier
                                .size(36.dp)
                                .align(Alignment.BottomEnd)
                        )
                    }
                }
            } else {
                // Layout estándar para tarjeta de categoría
                Column(modifier = Modifier.align(Alignment.TopStart)) {
                    Text(
                        text = valor,
                        style = TextStyle(
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDark) Color.White else Color(0xFF1E293B)
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = titulo,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (isDark) Color(0xFFA0AEC0) else Color(0xFF64748B)
                        )
                    )
                }

                Icon(
                    imageVector = ImageVector.vectorResource(id = iconRes),
                    contentDescription = null,
                    tint = categoryStyle?.accentColor ?: (if (isDark) Color(0xFFA0AEC0) else Color(0xFF64748B)),
                    modifier = Modifier
                        .size(36.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }
    }
}

@Composable
private fun EgresoRow(
    egreso: Egreso,
    currency: NumberFormat,
    canDelete: Boolean,
    isDark: Boolean,
    onDelete: () -> Unit
) {
    val style = getCategoryStyle(egreso.categoria)
    val cardBgColor = if (isDark) Color(0xFF1A1C29) else Color.White
    val borderColor = if (isDark) Color(0xFF2A2D3D) else Color(0xFFE2E8F0)
    val textColor = if (isDark) Color.White else Color(0xFF1E293B)
    val subtitleColor = if (isDark) Color(0xFFA0AEC0) else Color(0xFF64748B)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = cardBgColor),
        border = if (isDark) null else BorderStroke(1.dp, borderColor),
        elevation = CardDefaults.cardElevation(defaultElevation = if (isDark) 0.dp else 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(style.accentColor.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = style.iconRes),
                        contentDescription = null,
                        tint = style.accentColor,
                        modifier = Modifier.size(24.dp)
                    )
                }

                Spacer(Modifier.width(14.dp))

                Column {
                    Text(
                        text = categoriaLabel(egreso.categoria),
                        fontWeight = FontWeight.Bold,
                        style = TextStyle(fontSize = 16.sp, color = textColor)
                    )
                    Spacer(Modifier.height(2.dp))
                    val detailText = if (egreso.descripcion.isNotBlank()) {
                        "${egreso.descripcion}\n${DateUtils.formatDay(egreso.fechaEgreso)}"
                    } else {
                        DateUtils.formatDay(egreso.fechaEgreso)
                    }
                    Text(
                        text = detailText,
                        style = TextStyle(fontSize = 13.sp, color = subtitleColor, lineHeight = 16.sp)
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency.format(egreso.montoTotal),
                    fontWeight = FontWeight.Bold,
                    style = TextStyle(fontSize = 16.sp, color = style.accentColor)
                )

                if (canDelete) {
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .width(1.dp)
                            .height(24.dp)
                            .background(if (isDark) Color(0xFF2A2D3D) else Color(0xFFE2E8F0))
                    )
                    Spacer(Modifier.width(12.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color.Transparent)
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

fun categoriaLabel(categoria: CategoriaEgreso): String = when (categoria) {
    CategoriaEgreso.NOMINA -> "Nómina"
    CategoriaEgreso.GASOLINA -> "Gasolina"
    CategoriaEgreso.ALIMENTO -> "Alimento"
    CategoriaEgreso.FARMACOS -> "Fármacos"
    CategoriaEgreso.OTROS -> "Otros"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrarEgresoDialog(
    preselectedCategoria: CategoriaEgreso? = null,
    onDismiss: () -> Unit,
    onSave: suspend (CategoriaEgreso, Double, String?, String?, Int?) -> ResultadoOperacion
) {
    var categoria by remember { mutableStateOf(preselectedCategoria ?: CategoriaEgreso.OTROS) }
    var monto by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var empleadoNombre by remember { mutableStateOf("") }
    var diasTrabajo by remember { mutableStateOf("") }
    var menuAbierto by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val onSaveAction = {
        val montoD = monto.toDoubleOrNull()
        if (montoD == null || montoD <= 0) {
            error = "Ingresa un monto válido"
        } else if (categoria == CategoriaEgreso.NOMINA && (empleadoNombre.isBlank() || diasTrabajo.isBlank())) {
            error = "Completa los datos del empleado"
        } else {
            scope.launch {
                val descFinal = if (categoria == CategoriaEgreso.NOMINA) {
                    "${empleadoNombre.trim()} trabajó $diasTrabajo días"
                } else {
                    descripcion.ifBlank { null }
                }

                val resultado = onSave(
                    categoria,
                    montoD,
                    descFinal,
                    if (categoria == CategoriaEgreso.NOMINA) empleadoNombre.ifBlank { null } else null,
                    if (categoria == CategoriaEgreso.NOMINA) diasTrabajo.toIntOrNull() else null
                )
                when (resultado) {
                    is ResultadoOperacion.Ok -> onDismiss()
                    is ResultadoOperacion.Error -> error = resultado.mensaje
                }
            }
        }
    }

    SimpleFormDialog(
        title = if (preselectedCategoria != null) "Registrar egreso de ${categoriaLabel(preselectedCategoria)}" else "Registrar Egreso",
        onDismiss = onDismiss,
        onConfirm = { onSaveAction() }
    ) {
        if (preselectedCategoria == null) {
            ExposedDropdownMenuBox(expanded = menuAbierto, onExpandedChange = { menuAbierto = it }) {
                OutlinedTextField(
                    value = categoriaLabel(categoria),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Categoría") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = menuAbierto) }
                )
                ExposedDropdownMenu(expanded = menuAbierto, onDismissRequest = { menuAbierto = false }) {
                    CategoriaEgreso.values().forEach { c ->
                        DropdownMenuItem(text = { Text(categoriaLabel(c)) }, onClick = { categoria = c; menuAbierto = false })
                    }
                }
            }
            Spacer(Modifier.height(10.dp))
        }

        if (categoria == CategoriaEgreso.NOMINA) {
            OutlinedTextField(
                value = empleadoNombre,
                onValueChange = { input ->
                    if (input.length <= 50) {
                        val regex = Regex("[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚ]*")
                        if (regex.matches(input)) {
                            empleadoNombre = input
                        }
                    }
                },
                label = { Text("Nombre del empleado") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next)
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = diasTrabajo,
                onValueChange = { diasTrabajo = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Días trabajados") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Next),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = monto,
            onValueChange = { input ->
                val filtered = input.filter { it.isDigit() || it == '.' }
                if (filtered.count { it == '.' } <= 1) {
                    val parts = filtered.split('.')
                    val isDeleting = filtered.length < monto.length
                    if (isDeleting || parts[0].length <= 10) {
                        if (parts.size < 2 || parts[1].length <= 2) {
                            monto = filtered
                            error = null
                        }
                    }
                }
            },
            label = { Text("Monto") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = if (categoria == CategoriaEgreso.NOMINA) ImeAction.Done else ImeAction.Next
            ),
            keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                onDone = { 
                    if (categoria == CategoriaEgreso.NOMINA) {
                        onSaveAction()
                    }
                }
            ),
            visualTransformation = ThousandsSeparatorVisualTransformation(),
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (categoria != CategoriaEgreso.NOMINA && preselectedCategoria != null) Modifier.focusRequester(focusRequester)
                    else Modifier
                )
        )

        if (categoria != CategoriaEgreso.NOMINA) {
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = descripcion,
                onValueChange = { input ->
                    if (input.length <= 60) {
                        descripcion = input
                    }
                },
                label = { Text("Descripción (opcional)") },
                modifier = Modifier.fillMaxWidth(),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                    onDone = { onSaveAction() }
                )
            )
        }

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
    }
}

@Preview(showBackground = true)
@Composable
fun FinanzasPreview() {
    val dummyTotales = TotalesFinanzas(
        total = 50000.0,
        nomina = 20000.0,
        gasolina = 5000.0,
        alimento = 15000.0,
        farmacos = 5000.0,
        otros = 5000.0
    )
    val dummyEgresos = listOf(
        Egreso(idEgreso = "1", categoria = CategoriaEgreso.NOMINA, montoTotal = 5000.0, fechaEgreso = System.currentTimeMillis(), idUsuario = "1", descripcion = "Pago")
    )
    val ref = System.currentTimeMillis()
    val (dummyInicio, dummyFin) = getRangoSemanaGlobal(ref)
    
    MaterialTheme {
        FinanzasContent(
            totales = dummyTotales,
            egresosRecientes = dummyEgresos,
            currency = NumberFormat.getCurrencyInstance(Locale("es", "MX")),
            fechaActual = ref,
            inicioSemana = dummyInicio,
            finSemana = dummyFin,
            isAdmin = true,
            isDark = isSystemInDarkTheme(),
            onOpenDrawer = {},
            onRegistrarEgresoClick = {},
            onCategoryClick = {},
            onDeleteEgreso = {}
        )
    }
}

fun getRangoSemanaGlobal(fechaReferencia: Long): Pair<Long, Long> {
    val tz = java.util.TimeZone.getTimeZone("America/Mexico_City")
    val cal = java.util.Calendar.getInstance(tz)
    cal.timeInMillis = fechaReferencia
    cal.firstDayOfWeek = java.util.Calendar.SUNDAY
    cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SUNDAY)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 0)
    cal.set(java.util.Calendar.MINUTE, 0)
    cal.set(java.util.Calendar.SECOND, 0)
    cal.set(java.util.Calendar.MILLISECOND, 0)
    val inicio = cal.timeInMillis
    
    cal.set(java.util.Calendar.DAY_OF_WEEK, java.util.Calendar.SATURDAY)
    cal.set(java.util.Calendar.HOUR_OF_DAY, 23)
    cal.set(java.util.Calendar.MINUTE, 59)
    cal.set(java.util.Calendar.SECOND, 59)
    cal.set(java.util.Calendar.MILLISECOND, 999)
    val fin = cal.timeInMillis
    
    return inicio to fin
}
