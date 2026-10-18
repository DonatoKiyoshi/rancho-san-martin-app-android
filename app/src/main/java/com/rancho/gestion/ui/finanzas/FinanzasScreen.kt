package com.rancho.gestion.ui.finanzas

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
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
import com.rancho.gestion.ui.components.SimpleFormDialog
import com.rancho.gestion.ui.components.ThousandsSeparatorVisualTransformation
import com.rancho.gestion.ui.theme.RanchoTextSecondary
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
    val containerColor: Color,
    val iconRes: Int
)

fun getCategoryStyle(categoria: CategoriaEgreso): CategoryStyle = when (categoria) {
    CategoriaEgreso.NOMINA -> CategoryStyle(
        accentColor = Color(0xFF10B981), // Esmeralda / Verde
        containerColor = Color(0xFFECFDF5),
        iconRes = R.drawable.ic_nomina_finanzas
    )
    CategoriaEgreso.GASOLINA -> CategoryStyle(
        accentColor = Color(0xFFEA580C), // Naranja / Terracota
        containerColor = Color(0xFFFFF7ED),
        iconRes = R.drawable.ic_gasolina_finanzas
    )
    CategoriaEgreso.ALIMENTO -> CategoryStyle(
        accentColor = Color(0xFFD97706), // Ámbar / Dorado
        containerColor = Color(0xFFFFFBEB),
        iconRes = R.drawable.ic_alimentos_finanzas
    )
    CategoriaEgreso.FARMACOS -> CategoryStyle(
        accentColor = Color(0xFF8B5CF6), // Púrpura / Íñdigo
        containerColor = Color(0xFFF5F3FF),
        iconRes = R.drawable.ic_farmacos_finanzas
    )
    CategoriaEgreso.OTROS -> CategoryStyle(
        accentColor = Color(0xFF64748B), // Pizarra / Azul-Gris
        containerColor = Color(0xFFF8FAFC),
        iconRes = R.drawable.ic_otros_finanzas
    )
}

@Composable
fun FinanzasScreen(container: AppContainer, onOpenDrawer: () -> Unit) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
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
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar este egreso?",
                    textAlign = TextAlign.Center,
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

@OptIn(ExperimentalMaterial3Api::class)
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
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "Finanzas",
                        style = TextStyle(
                            fontSize = 28.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = Color(0xFF0F172A)
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onOpenDrawer) {
                        Icon(
                            Icons.Default.Home,
                            contentDescription = "Menú",
                            tint = Color(0xFF334155),
                            modifier = Modifier.size(26.dp)
                        )
                    }
                },
                actions = {
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Default.CloudDone,
                            contentDescription = "Sincronizado",
                            tint = Color(0xFF64748B),
                            modifier = Modifier.size(24.dp)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onRegistrarEgresoClick,
                icon = { Icon(Icons.Default.Add, contentDescription = null, tint = Color.White) },
                text = { Text("Registrar Egreso", fontWeight = FontWeight.Bold, color = Color.White) },
                containerColor = Color(0xFF3B82F6), // Indigo/Blue Accent
                shape = RoundedCornerShape(50)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 20.dp)
        ) {
            Spacer(Modifier.height(8.dp))

            // Grid de Tarjetas de Totales (2x3) con mayor espacio negativo (16.dp)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                FinanzasStatCard(
                    titulo = "Total",
                    valor = currency.format(totales.total),
                    iconRes = R.drawable.ic_total_finanzas,
                    isTotal = true,
                    modifier = Modifier.weight(1f)
                )
                FinanzasStatCard(
                    titulo = "Nómina",
                    valor = currency.format(totales.nomina),
                    iconRes = R.drawable.ic_nomina_finanzas,
                    categoryStyle = getCategoryStyle(CategoriaEgreso.NOMINA),
                    modifier = Modifier.weight(1f)
                )
            }
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

            Spacer(Modifier.height(28.dp))

            Text(
                text = "Movimientos del mes de ${DateUtils.formatMonth(fechaActual)}",
                style = TextStyle(
                    fontSize = 18.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = Color(0xFF0F172A)
                )
            )
            Spacer(Modifier.height(14.dp))

            if (egresosRecientes.isEmpty()) {
                Text(
                    text = "Aún no hay egresos registrados este mes.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF64748B)
                )
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
}

@Composable
private fun FinanzasStatCard(
    titulo: String,
    valor: String,
    iconRes: Int,
    modifier: Modifier = Modifier,
    isTotal: Boolean = false,
    categoryStyle: CategoryStyle? = null
) {
    val shape = RoundedCornerShape(22.dp)

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = shape,
        colors = CardDefaults.cardColors(
            containerColor = if (isTotal) Color.Transparent else Color.White
        ),
        border = if (isTotal) null else BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(
            defaultElevation = if (isTotal) 6.dp else 2.dp
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (isTotal) {
                        Modifier.background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF3B82F6), Color(0xFF6366F1))
                            )
                        )
                    } else Modifier
                )
                .heightIn(min = 100.dp)
                .padding(horizontal = 16.dp, vertical = 16.dp)
        ) {
            Column(modifier = Modifier.align(Alignment.TopStart)) {
                Text(
                    text = valor,
                    style = TextStyle(
                        fontSize = if (isTotal) 22.sp else 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isTotal) Color.White else Color(0xFF0F172A)
                    )
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = titulo,
                    style = TextStyle(
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (isTotal) Color.White.copy(alpha = 0.85f) else Color(0xFF64748B)
                    )
                )
            }

            Icon(
                imageVector = ImageVector.vectorResource(id = iconRes),
                contentDescription = null,
                tint = if (isTotal) Color.White.copy(alpha = 0.85f) else (categoryStyle?.accentColor ?: Color(0xFF64748B)),
                modifier = Modifier
                    .size(32.dp)
                    .align(Alignment.BottomEnd)
            )
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
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = BorderStroke(1.dp, Color(0xFFF1F5F9)),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
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
                // Contenedor circular suave translúcido con icono minimalista
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(CircleShape)
                        .background(style.containerColor),
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
                        style = TextStyle(fontSize = 16.sp, color = Color(0xFF0F172A))
                    )
                    Spacer(Modifier.height(2.dp))
                    val detailText = if (egreso.descripcion.isNotBlank()) {
                        "${egreso.descripcion} • ${DateUtils.formatDay(egreso.fechaEgreso)}"
                    } else {
                        DateUtils.formatDay(egreso.fechaEgreso)
                    }
                    Text(
                        text = detailText,
                        style = TextStyle(fontSize = 13.sp, color = Color(0xFF64748B))
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
                            .background(Color(0xFFFEE2E2))
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
