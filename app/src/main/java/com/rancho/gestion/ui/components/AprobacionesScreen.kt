package com.rancho.gestion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.ui.theme.RanchoSuccess
import com.rancho.gestion.ui.theme.RanchoError
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch

@Composable
fun AprobacionesScreen(
    container: AppContainer,
    onOpenDrawer: () -> Unit,
    onNavigateToDetalle: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    val animalesPendientes by container.ganadoRepository.pendientesAprobacion().collectAsState(initial = emptyList())
    val muertesPendientes by container.ganadoRepository.mortalidadPendiente().collectAsState(initial = emptyList())
    val ventasPendientes by container.ventaRepository.getVentasPendientes().collectAsState(initial = emptyList())

    var tabIndex by remember { mutableStateOf(0) }
    
    // Estados para diálogos de confirmación
    var itemParaAprobar by remember { mutableStateOf<Any?>(null) }
    var itemParaRechazar by remember { mutableStateOf<Any?>(null) }

    // Configuración de tabs con conteo (RF-21 / Visual)
    val tabs = listOf(
        TabItem("Entradas", animalesPendientes.size),
        TabItem("Ventas", ventasPendientes.size),
        TabItem("Muertes", muertesPendientes.size)
    )

    Scaffold(topBar = { HomeTopBar(title = "Aprobaciones", onHomeClick = onOpenDrawer) }) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            TabRow(selectedTabIndex = tabIndex) {
                tabs.forEachIndexed { index, item ->
                    Tab(
                        selected = tabIndex == index, 
                        onClick = { tabIndex = index },
                        text = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(item.title)
                                if (item.count > 0) {
                                    Spacer(Modifier.width(6.dp))
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(RanchoError),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = item.count.toString(),
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }
                    )
                }
            }

            Spacer(Modifier.height(16.dp))

            when (tabIndex) {
                0 -> EntradasPendientes(
                    animales = animalesPendientes,
                    onAprobar = { itemParaAprobar = it },
                    onRechazar = { itemParaRechazar = it },
                    onClick = { onNavigateToDetalle(it.numeroArete) }
                )
                1 -> VentasPendientes(
                    ventas = ventasPendientes,
                    onAprobar = { itemParaAprobar = it },
                    onRechazar = { itemParaRechazar = it }
                )
                2 -> MuertesPendientes(
                    muertes = muertesPendientes,
                    onAprobar = { itemParaAprobar = it },
                    onRechazar = { itemParaRechazar = it }
                )
            }
        }
    }

    // Diálogos de Confirmación
    if (itemParaAprobar != null) {
        ConfirmDialog(
            title = "Confirmar Aprobación",
            message = "¿Estás seguro de aprobar esta solicitud?",
            onConfirm = {
                val item = itemParaAprobar
                itemParaAprobar = null // Cerrar inmediatamente
                if (item == null) return@ConfirmDialog
                scope.launch {
                    when (item) {
                        is Animal -> container.ganadoRepository.aprobarAnimal(item, SessionManager.currentUserId())
                        is Venta -> container.ventaRepository.consolidarVenta(item.idVenta, SessionManager.currentUserId(), true, deAprobaciones = true)
                        is Mortalidad -> container.ganadoRepository.aprobarMortalidad(item, SessionManager.currentUserId())
                    }
                }
            },
            onDismiss = { itemParaAprobar = null }
        )
    }

    if (itemParaRechazar != null) {
        ConfirmDialog(
            title = "Confirmar Rechazo",
            message = "¿Deseas rechazar esta solicitud? Esta acción la eliminará de la lista de pendientes.",
            confirmLabel = "Rechazar",
            onConfirm = {
                val item = itemParaRechazar
                itemParaRechazar = null // Cerrar inmediatamente
                if (item == null) return@ConfirmDialog
                scope.launch {
                    when (item) {
                        is Animal -> container.ganadoRepository.rechazarAnimal(item)
                        is Venta -> container.ventaRepository.rechazarVenta(item)
                        is Mortalidad -> container.ganadoRepository.rechazarMortalidad(item)
                    }
                }
            },
            onDismiss = { itemParaRechazar = null }
        )
    }
}

private data class TabItem(val title: String, val count: Int)

@Composable
private fun EntradasPendientes(
    animales: List<Animal>,
    onAprobar: (Animal) -> Unit,
    onRechazar: (Animal) -> Unit,
    onClick: (Animal) -> Unit
) {
    if (animales.isEmpty()) {
        EmptyState(icon = Icons.Default.Check, title = "No hay entradas pendientes")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(animales, key = { it.numeroArete }) { animal ->
                AprobacionCard(
                    title = "Arete ${animal.numeroArete}",
                    subtitle = "Proveedor: ${animal.proveedor}\nSexo: ${if(animal.sexo == Sexo.MACHO) "Macho" else "Hembra"}\nPeso: ${animal.pesoEntrada.toInt()} kg\nPrecio de Compra: ${animal.precioCompra}",
                    onAprobar = { onAprobar(animal) },
                    onRechazar = { onRechazar(animal) },
                    onClick = { onClick(animal) }
                )
            }
        }
    }
}

@Composable
private fun MuertesPendientes(
    muertes: List<Mortalidad>,
    onAprobar: (Mortalidad) -> Unit,
    onRechazar: (Mortalidad) -> Unit
) {
    if (muertes.isEmpty()) {
        EmptyState(icon = Icons.Default.Check, title = "No hay muertes pendientes")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(muertes, key = { it.idMortalidad }) { muerte ->
                AprobacionCard(
                    title = "Muerte: Arete ${muerte.numeroArete}",
                    subtitle = "Causa: ${muerte.causa}\nRegistrado por ID: ${muerte.idUsuarioRegistro}",
                    onAprobar = { onAprobar(muerte) },
                    onRechazar = { onRechazar(muerte) }
                )
            }
        }
    }
}

@Composable
private fun VentasPendientes(
    ventas: List<Venta>,
    onAprobar: (Venta) -> Unit,
    onRechazar: (Venta) -> Unit
) {
    if (ventas.isEmpty()) {
        EmptyState(icon = Icons.Default.Check, title = "No hay ventas pendientes")
    } else {
        LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            items(ventas, key = { it.idVenta }) { venta ->
                val modalidadStr = if (venta.modalidad == ModalidadVenta.INDIVIDUAL) "Venta Individual" else "Venta por Jaula"
                AprobacionCard(
                    title = "Venta: ${venta.comprador}",
                    subtitle = "Modalidad: $modalidadStr\nCabezas: ${venta.totalCabezas} · Total: $${venta.montoTotal}",
                    onAprobar = { onAprobar(venta) },
                    onRechazar = { onRechazar(venta) }
                )
            }
        }
    }
}

@Composable
private fun AprobacionCard(
    title: String,
    subtitle: String,
    onAprobar: () -> Unit,
    onRechazar: () -> Unit,
    onClick: (() -> Unit)? = null
) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth().then(
            if (onClick != null) Modifier.clickable { onClick() } else Modifier
        ),
        shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.ExtraBold)
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onRechazar) {
                    Icon(Icons.Default.Close, contentDescription = "Rechazar", tint = RanchoError)
                }
                IconButton(onClick = onAprobar) {
                    Icon(Icons.Default.Check, contentDescription = "Aprobar", tint = RanchoSuccess)
                }
            }
        }
    }
}
