package com.rancho.gestion.ui.ganado

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Input
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.ui.components.AreteTag
import com.rancho.gestion.ui.components.BarcodeScannerScreen
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.EmptyState
import com.rancho.gestion.ui.components.EtapaBadge
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.theme.*
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

data class GanadoFilters(
    val sexo: String = "Todos",
    val fase: String = "Todos",
    val color: String = "Todos",
    val tipo: String = "Activo", // Solo Admin puede cambiar
    val tipoBaja: String = "Todos" // Solo Admin
)

@Composable
fun GanadoListScreen(
    container: AppContainer,
    onOpenDrawer: () -> Unit,
    onAgregarAnimal: () -> Unit,
    onAnimalClick: (String) -> Unit = {}
) {
    var query by remember { mutableStateOf("") }
    var filters by remember { mutableStateOf(GanadoFilters()) }
    val isAdmin = SessionManager.isAdmin()

    // Lógica de búsqueda y filtrado
    val animalesActivosFlow = remember(isAdmin, query, filters) {
        container.ganadoRepository.activos().map { list ->
            if (query.isNotBlank()) {
                list.filter { it.numeroArete.contains(query, ignoreCase = true) }
            } else {
                // Aplicar filtros solo si el tipo es Activo o Todos
                if (filters.tipo == "Archivado") emptyList()
                else list.filter { animal ->
                    val matchSexo = filters.sexo == "Todos" || animal.sexo.name == filters.sexo.uppercase()
                    val matchFase = filters.fase == "Todos" || animal.fase.name == filters.fase.uppercase()
                    val matchColor = filters.color == "Todos" || animal.color == filters.color
                    matchSexo && matchFase && matchColor
                }
            }
        }
    }
    
    val animalesArchivadosFlow = remember(isAdmin, query, filters) {
        if (!isAdmin) kotlinx.coroutines.flow.flowOf(emptyList())
        else container.ganadoRepository.archivados().map { list ->
            if (query.isNotBlank()) {
                list.filter { it.numeroArete.contains(query, ignoreCase = true) }
            } else {
                if (filters.tipo == "Activo") emptyList()
                else list.filter { animal ->
                    val matchSexo = filters.sexo == "Todos" || animal.sexo.name == filters.sexo.uppercase()
                    val matchColor = filters.color == "Todos" || animal.color == filters.color
                    val matchBaja = filters.tipoBaja == "Todos" || (
                        if (filters.tipoBaja == "Venta") animal.estadoInventario == EstadoInventario.VENDIDO
                        else animal.estadoInventario == EstadoInventario.MUERTO
                    )
                    matchSexo && matchColor && matchBaja
                }
            }
        }
    }

    val animalesActivos by animalesActivosFlow.collectAsState(initial = emptyList())
    val animalesArchivados by animalesArchivadosFlow.collectAsState(initial = emptyList())

    val rangos by container.ganadoRepository.rangosPeso().collectAsState(initial = emptyList())
    var mostrarScanner by remember { mutableStateOf(false) }
    var mostrarFiltros by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val lanzadorPermisoScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mostrarScanner = true
        }
    }

    fun iniciarEscaneo() {
        val yaConcedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (yaConcedido) {
            mostrarScanner = true
        } else {
            lanzadorPermisoScanner.launch(Manifest.permission.CAMERA)
        }
    }

    if (mostrarScanner) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { mostrarScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            BarcodeScannerScreen(
                onResult = { result ->
                    query = result
                    mostrarScanner = false
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        GanadoListContent(
            query = query,
            onQueryChange = { query = it },
            filters = filters,
            animalesActivos = animalesActivos,
            animalesArchivados = animalesArchivados,
            rangos = rangos,
            isAdmin = isAdmin,
            onAgregarAnimal = onAgregarAnimal,
            onOpenDrawer = onOpenDrawer,
            onAnimalClick = onAnimalClick,
            onEscanearClick = { iniciarEscaneo() },
            onFilterClick = { mostrarFiltros = true }
        )

        if (mostrarFiltros) {
            FilterDialog(
                currentFilters = filters,
                isAdmin = isAdmin,
                onDismiss = { mostrarFiltros = false },
                onApply = { 
                    filters = it
                    mostrarFiltros = false
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GanadoListContent(
    query: String,
    onQueryChange: (String) -> Unit,
    filters: GanadoFilters,
    animalesActivos: List<Animal>,
    animalesArchivados: List<Animal>,
    rangos: List<RangoPeso>,
    isAdmin: Boolean,
    onAgregarAnimal: () -> Unit,
    onOpenDrawer: () -> Unit,
    onAnimalClick: (String) -> Unit,
    onEscanearClick: () -> Unit,
    onFilterClick: () -> Unit
) {
    // Cálculos Activos
    val machosA = animalesActivos.count { it.sexo == Sexo.MACHO }
    val hembrasA = animalesActivos.count { it.sexo == Sexo.HEMBRA }
    val acopio = animalesActivos.count { it.fase == FaseGanado.ACOPIO }
    val desarrollo = animalesActivos.count { it.fase == FaseGanado.DESARROLLO }

    // Cálculos Archivados (Solo Admin)
    val machosH = animalesArchivados.count { it.sexo == Sexo.MACHO }
    val hembrasH = animalesArchivados.count { it.sexo == Sexo.HEMBRA }
    val bajasVenta = animalesArchivados.count { it.estadoInventario == EstadoInventario.VENDIDO }
    val bajasMuerte = animalesArchivados.count { it.estadoInventario == EstadoInventario.MUERTO }

    Scaffold(
        topBar = { 
            HomeTopBar(
                title = "Ganado", 
                onHomeClick = onOpenDrawer,
                actions = {
                    IconButton(onClick = onFilterClick) {
                        BadgedBox(badge = {
                            if (filters != GanadoFilters()) {
                                Badge(containerColor = RanchoAccent)
                            }
                        }) {
                            Icon(Icons.Default.FilterList, "Filtros")
                        }
                    }
                }
            ) 
        },
        bottomBar = {
            AnimatedVisibility(
                visible = isAdmin && query.isBlank(),
                enter = slideInVertically { it } + fadeIn(),
                exit = slideOutVertically { it } + fadeOut()
            ) {
                GanadoSummaryFooter(
                    isAdmin = isAdmin,
                    filters = filters,
                    activos = SummaryData(animalesActivos.size, machosA, hembrasA, acopio, desarrollo),
                    archivados = SummaryData(animalesArchivados.size, machosH, hembrasH, bajasVenta, bajasMuerte)
                )
            }
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            // Buscador
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("Buscar por arete") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    trailingIcon = {
                        if (query.isNotEmpty()) {
                            IconButton(onClick = { onQueryChange("") }) { Icon(Icons.Default.Clear, null) }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(12.dp)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onEscanearClick,
                    modifier = Modifier.size(width = 80.dp, height = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ViewWeek, null)
                        Text("Escanear", fontSize = 10.sp)
                    }
                }
            }

            
            Spacer(Modifier.height(12.dp))
            Button(onClick = onAgregarAnimal, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                Icon(Icons.Default.Add, null); Spacer(Modifier.width(8.dp)); Text("Registrar Nuevo Animal")
            }
            Spacer(Modifier.height(16.dp))

            if (animalesActivos.isEmpty() && animalesArchivados.isEmpty() && query.isNotEmpty()) {
                EmptyState(icon = Icons.Default.Search, title = "Sin coincidencias", subtitle = "No hay resultados para \"$query\"")
            }

            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.weight(1f)) {
                // INVENTARIO ACTIVO
                if (animalesActivos.isNotEmpty()) {
                    item { Text("Inventario Activo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary) }
                    items(animalesActivos, key = { "act_${it.numeroArete}" }) { animal ->
                        val rango = rangos.firstOrNull { animal.pesoEntrada in it.limiteInferior..it.limiteSuperior }
                        AnimalRow(animal = animal, rangoLabel = rango?.nombreRango ?: "Sin clasificar", onClick = { onAnimalClick(animal.numeroArete) })
                    }
                }

                // INVENTARIO ARCHIVADO (ADMIN ONLY)
                if (isAdmin && animalesArchivados.isNotEmpty()) {
                    item {
                        Column {
                            Spacer(Modifier.height(16.dp))
                            HorizontalDivider(thickness = 2.dp, color = RanchoPrimary.copy(alpha = 0.1f))
                            Spacer(Modifier.height(16.dp))
                            Text("Inventario Archivado", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RanchoTextSecondary)
                        }
                    }
                    items(animalesArchivados, key = { "arc_${it.numeroArete}" }) { animal ->
                        val rango = rangos.firstOrNull { animal.pesoEntrada in it.limiteInferior..it.limiteSuperior }
                        AnimalRow(animal = animal, rangoLabel = rango?.nombreRango ?: "Sin clasificar", isArchived = true, onClick = { onAnimalClick(animal.numeroArete) })
                    }
                }
            }
        }
    }
}

private data class SummaryData(val total: Int, val machos: Int, val hembras: Int, val c1: Int, val c2: Int)

@Composable
private fun GanadoSummaryFooter(isAdmin: Boolean, filters: GanadoFilters, activos: SummaryData, archivados: SummaryData) {
    var seccionExpandida by remember { mutableStateOf<String?>(null) } // "activos" o "archivados"
    
    // Si el filtro es "Activo", forzar el colapso de archivados
    if (filters.tipo == "Activo" && seccionExpandida == "archivados") {
        seccionExpandida = null
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 8.dp,
        shadowElevation = 12.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(modifier = Modifier.navigationBarsPadding().padding(horizontal = 16.dp, vertical = 12.dp)) {
            if (filters.tipo != "Archivado") {
                SummaryAccordion(
                    title = "Resumen de Inventario Activo",
                    data = activos,
                    isExpanded = seccionExpandida == "activos",
                    onToggle = { 
                        seccionExpandida = if (seccionExpandida == "activos") null else "activos"
                    },
                    labels = Pair("Acopio", "Desarrollo"),
                    icons = Pair(Icons.AutoMirrored.Filled.Input, Icons.AutoMirrored.Filled.TrendingUp)
                )
            }

            if (isAdmin && filters.tipo != "Activo") {
                if (filters.tipo != "Archivado") Spacer(Modifier.height(8.dp))
                SummaryAccordion(
                    title = "Resumen de Inventario Archivado",
                    data = archivados,
                    isExpanded = seccionExpandida == "archivados",
                    onToggle = { 
                        seccionExpandida = if (seccionExpandida == "archivados") null else "archivados"
                    },
                    labels = Pair("Ventas", "Muertes"),
                    icons = Pair(Icons.Default.Sell, Icons.Default.HeartBroken),
                    accentColor = RanchoTextSecondary
                )
            }
        }
    }
}

@Composable
private fun SummaryAccordion(
    title: String,
    data: SummaryData,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    labels: Pair<String, String>,
    icons: Pair<ImageVector, ImageVector>,
    accentColor: Color = RanchoPrimary
) {
    Card(
        onClick = onToggle,
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.padding(horizontal = 4.dp)) {
            Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.ExtraBold, color = accentColor)
                Icon(if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore, null, tint = accentColor)
            }
            AnimatedVisibility(visible = isExpanded, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Row(modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SummaryBox(modifier = Modifier.weight(1f), title = "Por Sexo", items = listOf(
                        SummaryItemData("Machos", data.machos.toString(), Icons.Default.Male, Color(0xFF2196F3)),
                        SummaryItemData("Hembras", data.hembras.toString(), Icons.Default.Female, Color(0xFFE91E63))
                    ), totalValue = (data.machos + data.hembras).toString())
                    SummaryBox(modifier = Modifier.weight(1f), title = "Causas", items = listOf(
                        SummaryItemData(labels.first, data.c1.toString(), icons.first, accentColor),
                        SummaryItemData(labels.second, data.c2.toString(), icons.second, RanchoError)
                    ), totalValue = (data.c1 + data.c2).toString())
                }
            }
        }
    }
}

private data class SummaryItemData(val label: String, val value: String, val icon: ImageVector, val color: Color)

@Composable
private fun SummaryBox(modifier: Modifier = Modifier, title: String, items: List<SummaryItemData>, totalValue: String? = null) {
    Column(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f), RoundedCornerShape(16.dp)).padding(12.dp)) {
        Text(title, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = RanchoTextSecondary)
        Spacer(Modifier.height(8.dp))
        items.forEach { item ->
            Row(Modifier.fillMaxWidth().padding(vertical = 2.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(item.icon, null, modifier = Modifier.size(14.dp), tint = item.color)
                    Spacer(Modifier.width(6.dp)); Text(item.label, style = MaterialTheme.typography.bodySmall)
                }
                Text(item.value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold)
            }
        }
        if (totalValue != null) {
            HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), thickness = 1.dp, color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Total", style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                Text(totalValue, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Black, color = RanchoPrimary)
            }
        }
    }
}

@Composable
private fun AnimalRow(animal: Animal, rangoLabel: String, isArchived: Boolean = false, onClick: () -> Unit) {
    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = if (isArchived) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f) else MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = if (isArchived) 0.dp else 2.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                AreteTag(animal.numeroArete); Spacer(Modifier.width(8.dp))
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = RoundedCornerShape(50)) {
                    Text(text = rangoLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                }
                if (animal.estadoAprobacion == EstadoAprobacion.PENDIENTE) {
                    Spacer(Modifier.width(8.dp))
                    Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = RoundedCornerShape(50)) {
                        Text(text = "Pendiente", modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(90.dp).clip(RoundedCornerShape(12.dp)).background(MaterialTheme.colorScheme.surface), contentAlignment = Alignment.Center) {
                    if (animal.fotografiaLocal != null) {
                        AsyncImage(model = animal.fotografiaLocal, contentDescription = null, modifier = Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                    } else {
                        Icon(imageVector = ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder), contentDescription = null, tint = if (isArchived) RanchoTextSecondary.copy(alpha = 0.3f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), modifier = Modifier.size(64.dp))
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = "${if (animal.sexo == Sexo.MACHO) "Macho" else "Hembra"} · ${animal.color} ", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = if (isArchived) RanchoTextSecondary else MaterialTheme.colorScheme.onSurface)
                    Text(text = "Peso: ${animal.pesoEntrada.toInt()} kg", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    val dias = ((System.currentTimeMillis() - animal.fechaEntrada) / (1000 * 60 * 60 * 24)).coerceAtLeast(0)
                    Text(text = "Estancia: $dias días", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(8.dp))
                    if (isArchived) {
                        val (causa, color) = when {
                            animal.estadoInventario == EstadoInventario.MUERTO -> "Muerte" to RanchoError
                            animal.tipoBaja == TipoBaja.VENTA -> "Venta" to RanchoSuccess
                            else -> "Archivado" to RanchoTextSecondary
                        }
                        Surface(color = color.copy(alpha = 0.1f), shape = RoundedCornerShape(50)) {
                            Text(text = causa, modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp), style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = color)
                        }
                    } else {
                        EtapaBadge(animal.fase)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDialog(
    currentFilters: GanadoFilters,
    isAdmin: Boolean,
    onDismiss: () -> Unit,
    onApply: (GanadoFilters) -> Unit
) {
    var sexo by remember { mutableStateOf(currentFilters.sexo) }
    var fase by remember { mutableStateOf(currentFilters.fase) }
    var color by remember { mutableStateOf(currentFilters.color) }
    var tipo by remember { mutableStateOf(currentFilters.tipo) }
    var tipoBaja by remember { mutableStateOf(currentFilters.tipoBaja) }

    val opcionesColor = listOf("Todos", "Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { 
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterAlt, null, tint = RanchoPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Filtros de Ganado")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                // Filtro: Sexo
                FilterSegment(label = "Sexo", selected = sexo, options = listOf("Todos", "Macho", "Hembra")) { sexo = it }
                
                // Filtro: Fase (Solo para activos/todos)
                if (tipo != "Archivado") {
                    FilterSegment(label = "Fase", selected = fase, options = listOf("Todos", "Acopio", "Desarrollo")) { fase = it }
                }

                // Filtro: Color
                Text("Color", style = MaterialTheme.typography.labelMedium, color = RanchoTextSecondary)
                ScrollableRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    opcionesColor.forEach { c ->
                        FilterChip(
                            selected = color == c,
                            onClick = { color = c },
                            label = { Text(c) }
                        )
                    }
                }

                if (isAdmin) {
                    HorizontalDivider(Modifier.padding(vertical = 4.dp))
                    // Filtro: Tipo
                    FilterSegment(label = "Tipo de Inventario", selected = tipo, options = listOf("Activo", "Archivado", "Todos")) { tipo = it }
                    
                    // Filtro: Tipo de Baja
                    if (tipo != "Activo") {
                        FilterSegment(label = "Causa de Baja", selected = tipoBaja, options = listOf("Todos", "Venta", "Muerte")) { tipoBaja = it }
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onApply(GanadoFilters(sexo, fase, color, tipo, tipoBaja)) }) {
                Text("Aplicar Filtros")
            }
        },
        dismissButton = {
            TextButton(onClick = { onApply(GanadoFilters()) }) {
                Text("Restablecer")
            }
        }
    )
}

@Composable
private fun FilterSegment(label: String, selected: String, options: List<String>, onSelect: (String) -> Unit) {
    Column {
        Text(label, style = MaterialTheme.typography.labelMedium, color = RanchoTextSecondary)
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            options.forEach { opt ->
                FilterChip(
                    modifier = Modifier.weight(1f),
                    selected = selected == opt,
                    onClick = { onSelect(opt) },
                    label = { Text(opt, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth()) }
                )
            }
        }
    }
}

@Composable
private fun ScrollableRow(horizontalArrangement: Arrangement.Horizontal, content: @Composable RowScope.() -> Unit) {
    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = horizontalArrangement, contentPadding = PaddingValues(horizontal = 0.dp)) {
        item { Row(content = content) }
    }
}

@Preview(showBackground = true)
@Composable
fun GanadoListPreview() {
    MaterialTheme {
        GanadoListContent(
            query = "",
            onQueryChange = {},
            filters = GanadoFilters(),
            animalesActivos = emptyList(),
            animalesArchivados = emptyList(),
            rangos = emptyList(),
            isAdmin = true,
            onAgregarAnimal = {},
            onOpenDrawer = {},
            onAnimalClick = {},
            onEscanearClick = {},
            onFilterClick = {}
        )
    }
}
