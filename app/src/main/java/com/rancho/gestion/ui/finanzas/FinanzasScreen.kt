package com.rancho.gestion.ui.finanzas

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.components.SimpleFormDialog
import com.rancho.gestion.ui.components.StatCard
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

@Composable
fun FinanzasScreen(container: AppContainer, onOpenDrawer: () -> Unit) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    var mostrarDialogo by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val (inicioMes, ahora) = remember { DateUtils.rangoPara(Periodo.MENSUAL) }

    val totales by produceState(
        initialValue = TotalesFinanzas(0.0, 0.0, 0.0, 0.0, 0.0, 0.0),
        container
    ) {
        combine(
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.NOMINA, inicioMes, ahora),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.GASOLINA, inicioMes, ahora),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.ALIMENTO, inicioMes, ahora),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.FARMACOS, inicioMes, ahora),
            container.finanzasRepository.totalPorCategoria(CategoriaEgreso.OTROS, inicioMes, ahora)
        ) { nomina, gasolina, alimento, farmacos, otros ->
            TotalesFinanzas(
                total = nomina + gasolina + alimento + farmacos + otros,
                nomina = nomina, gasolina = gasolina, alimento = alimento, farmacos = farmacos, otros = otros
            )
        }.collect { value = it }
    }

    val egresosRecientes by container.finanzasRepository.porRango(inicioMes, ahora).collectAsState(initial = emptyList())
    val isAdmin = SessionManager.isAdmin()

    FinanzasContent(
        totales = totales,
        egresosRecientes = egresosRecientes,
        currency = currency,
        fechaActual = ahora,
        isAdmin = isAdmin,
        onOpenDrawer = onOpenDrawer,
        onRegistrarEgresoClick = { mostrarDialogo = true },
        onDeleteEgreso = { /* Se manejará según reglas del negocio */ }
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
}

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
        topBar = { HomeTopBar(title = "Finanzas", onHomeClick = onOpenDrawer) },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = onRegistrarEgresoClick, icon = { Icon(Icons.Default.Add, null) }, text = { Text("Registrar Egreso") })
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 20.dp)) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Total", currency.format(totales.total), Modifier.weight(1f))
                StatCard("Nomina", currency.format(totales.nomina), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Gasolina", currency.format(totales.gasolina), Modifier.weight(1f))
                StatCard("Fármacos", currency.format(totales.farmacos), Modifier.weight(1f))
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                StatCard("Alimento", currency.format(totales.alimento), Modifier.weight(1f))
                StatCard("Otros", currency.format(totales.otros), Modifier.weight(1f))
            }

            Spacer(Modifier.height(20.dp))
            Text("Movimientos del mes (${DateUtils.formatMonth(fechaActual)})", fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            if (egresosRecientes.isEmpty()) {
                Text("Aún no hay egresos registrados este mes.")
            }
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(egresosRecientes, key = { it.idEgreso }) { egreso ->
                    // Validación de Fecha de Corte (CU-17 / PROC-04)
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
private fun EgresoRow(egreso: Egreso, currency: NumberFormat, canDelete: Boolean, onDelete: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text(categoriaLabel(egreso.categoria), fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleSmall)
                if (egreso.descripcion.isNotBlank()) Text(egreso.descripcion, style = MaterialTheme.typography.bodyMedium)
                Text(DateUtils.formatDay(egreso.fechaEgreso), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(currency.format(egreso.montoTotal), fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.primary)
                if (canDelete) {
                    IconButton(onClick = onDelete) { 
                        Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp)) 
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
            } else {
                scope.launch {
                    val resultado = onSave(
                        categoria,
                        montoD,
                        descripcion.ifBlank { null },
                        empleadoNombre.ifBlank { null },
                        diasTrabajo.toIntOrNull()
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
                onValueChange = { empleadoNombre = it },
                label = { Text("Nombre del empleado") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
            OutlinedTextField(
                value = diasTrabajo,
                onValueChange = { diasTrabajo = it.filter { c -> c.isDigit() } },
                label = { Text("Días trabajados") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(10.dp))
        }

        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Monto") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = descripcion,
            onValueChange = { descripcion = it },
            label = { Text("Descripción (opcional)") },
            modifier = Modifier.fillMaxWidth()
        )
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
