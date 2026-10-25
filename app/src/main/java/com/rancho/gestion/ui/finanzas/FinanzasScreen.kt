package com.rancho.gestion.ui.finanzas

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
    var egresoAEliminar by remember { mutableStateOf<Egreso?>(null) }
    val scope = rememberCoroutineScope()
    val (inicioMes, finMes) = remember { DateUtils.rangoPara(Periodo.MENSUAL) }

    val totales by produceState(
        initialValue = TotalesFinanzas(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        container, inicioMes, finMes
    ) {
        combine(
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.NOMINA, inicioMes, finMes),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.GASOLINA, inicioMes, finMes),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.ALIMENTO, inicioMes, finMes),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.FARMACOS, inicioMes, finMes),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.OTROS, inicioMes, finMes)
        ) { nomina, gasolina, alimento, farmacos, otros ->
            TotalesFinanzas(
                total = nomina + gasolina + alimento + farmacos + otros,
                nomina = nomina, gasolina = gasolina, alimento = alimento, farmacos = farmacos, otros = otros
            )
        }.collect { value = it }
    }

    val egresosRecientes by container.finanzasRepository.porRango(inicioMes, finMes).collectAsState(initial = emptyList())
    val isAdmin = SessionManager.isAdmin()

    FinanzasContent(
        totales = totales,
        egresosRecientes = egresosRecientes,
        currency = currency,
        fechaActual = System.currentTimeMillis(),
        isAdmin = isAdmin,
        onOpenDrawer = onOpenDrawer,
        onRegistrarEgresoClick = { mostrarDialogo = true },
        onDeleteEgreso = { egresoAEliminar = it }
    )

    if (mostrarDialogo) {
        RegistrarEgresoDialog(
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
    isAdmin: Boolean,
    onOpenDrawer: () -> Unit,
    onRegistrarEgresoClick: () -> Unit,
    onDeleteEgreso: (Egreso) -> Unit
) {
    val listState = rememberLazyListState()
    val density = LocalDensity.current.density
    val collapseThresholdPx = 200f * density

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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRegistrarEgresoClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("Registrar Egreso", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = MaterialTheme.colorScheme.primary,
                shape = RoundedCornerShape(50)
            )
        }
    ) { padding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Sticky Header con colapso progresivo y continuo al hacer scroll
            stickyHeader {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.background)
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
                                    categoryStyle = getCategoryStyle(CategoriaEgreso.NOMINA)
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    FinanzasStatCard(
                                        titulo = "Fármacos",
                                        valor = currency.format(totales.farmacos),
                                        iconRes = R.drawable.ic_farmacos_finanzas,
                                        categoryStyle = getCategoryStyle(CategoriaEgreso.FARMACOS),
                                        modifier = Modifier.weight(1f)
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
                                        modifier = Modifier.weight(1f)
                                    )
                                    FinanzasStatCard(
                                        titulo = "Otros",
                                        valor = currency.format(totales.otros),
                                        iconRes = R.drawable.ic_otros_finanzas,
                                        categoryStyle = getCategoryStyle(CategoriaEgreso.OTROS),
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    Text(
                        text = "Movimientos del mes de ${DateUtils.formatMonth(fechaActual)}",
                        style = TextStyle(
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onBackground
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                }
            }

            if (egresosRecientes.isEmpty()) {
                item {
                    Text(
                        text = "Aún no hay egresos registrados este mes.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            items(egresosRecientes, key = { it.idEgreso }) { egreso ->
                val calActual = Calendar.getInstance()
                val calEgreso = Calendar.getInstance().apply { timeInMillis = egreso.fechaEgreso }
                val esCorteVigente = calActual.get(Calendar.MONTH) == calEgreso.get(Calendar.MONTH) &&
                                   calActual.get(Calendar.YEAR) == calEgreso.get(Calendar.YEAR)

                EgresoRow(
                    egreso = egreso,
                    currency = currency,
                    canDelete = isAdmin && esCorteVigente,
                    onDelete = { onDeleteEgreso(egreso) }
                )
            }

            item { Spacer(Modifier.height(80.dp)) }
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
    categoryStyle: CategoryStyle? = null
) {
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

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isTotal) Color.Transparent else MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isTotal) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTotal) 6.dp else 1.5.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isTotal) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF0284C7), Color(0xFF2563EB))
                            )
                        )
                    } else Modifier
                )
                .height(cardHeight)
                .padding(horizontal = 16.dp, vertical = 10.dp)
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
                                    fontSize = 12.sp,
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
                                    fontSize = 22.sp,
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
                            tint = Color.White.copy(alpha = 0.85f),
                            modifier = Modifier
                                .size(32.dp)
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
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = titulo,
                        style = TextStyle(
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    )
                }

                Icon(
                    imageVector = ImageVector.vectorResource(id = iconRes),
                    contentDescription = null,
                    tint = categoryStyle?.accentColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .size(32.dp)
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
    onDelete: () -> Unit
) {
    val style = getCategoryStyle(egreso.categoria)

    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
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
                        style = TextStyle(fontSize = 16.sp, color = MaterialTheme.colorScheme.onSurface)
                    )
                    Spacer(Modifier.height(2.dp))
                    val detailText = if (egreso.descripcion.isNotBlank()) {
                        "${egreso.descripcion} • ${DateUtils.formatDay(egreso.fechaEgreso)}"
                    } else {
                        DateUtils.formatDay(egreso.fechaEgreso)
                    }
                    Text(
                        text = detailText,
                        style = TextStyle(fontSize = 13.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Spacer(Modifier.width(10.dp))
                    Box(
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFFEE2E2).copy(alpha = 0.2f))
                            .clickable(onClick = onDelete),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "Eliminar",
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(16.dp)
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
    onDismiss: () -> Unit,
    onSave: suspend (CategoriaEgreso, Double, String?, String?, Int?) -> ResultadoOperacion
) {
    var categoria by remember { mutableStateOf(CategoriaEgreso.OTROS) }
    var monto by remember { mutableStateOf("") }
    var descripcion by remember { mutableStateOf("") }
    var empleadoNombre by remember { mutableStateOf("") }
    var diasTrabajo by remember { mutableStateOf("") }
    var menuAbierto by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    SimpleFormDialog(
        title = "Registrar Egreso",
        onDismiss = onDismiss,
        onConfirm = {
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
    ) {
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
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = diasTrabajo,
                onValueChange = { diasTrabajo = it.filter { c -> c.isDigit() }.take(3) },
                label = { Text("Días trabajados") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = ThousandsSeparatorVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
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
                modifier = Modifier.fillMaxWidth()
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
        Egreso(idEgreso = 1, categoria = CategoriaEgreso.NOMINA, montoTotal = 5000.0, fechaEgreso = System.currentTimeMillis(), idUsuario = 1, descripcion = "Pago")
    )
    MaterialTheme {
        FinanzasContent(
            totales = dummyTotales,
            egresosRecientes = dummyEgresos,
            currency = NumberFormat.getCurrencyInstance(Locale("es", "MX")),
            fechaActual = System.currentTimeMillis(),
            isAdmin = true,
            onOpenDrawer = {},
            onRegistrarEgresoClick = {},
            onDeleteEgreso = {}
        )
    }
}
