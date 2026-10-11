package com.rancho.gestion.ui.reportes

import androidx.compose.foundation.background
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.slideInVertically
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.Canvas
import androidx.compose.animation.core.*

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.finanzas.categoriaLabel
import com.rancho.gestion.ui.finanzas.getCategoryStyle
import com.rancho.gestion.ui.reportes.periodoLabel
import com.rancho.gestion.ui.theme.RanchoAccent
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.PdfGenerator
import com.rancho.gestion.util.ExcelGenerator
import com.rancho.gestion.util.Periodo
import java.text.NumberFormat
import java.util.Calendar
import java.util.Locale
import java.io.File
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat

// Clases para el nuevo filtro de fechas
sealed class FiltroFechas {
    data class Ultimo(val periodo: Periodo) : FiltroFechas()
    data class EspecificoDia(val anio: Int, val mes: Int, val dia: Int) : FiltroFechas()
    data class EspecificoSemana(val anio: Int, val mes: Int, val semana: Int) : FiltroFechas()
    data class EspecificoMes(val anio: Int, val mes: Int) : FiltroFechas()
    data class EspecificoAnio(val anio: Int) : FiltroFechas()
}

@Composable
fun ReportesFinanzasScreen(container: AppContainer, onBack: () -> Unit) {
    var filtroFechas by remember { mutableStateOf<FiltroFechas>(FiltroFechas.Ultimo(Periodo.MENSUAL)) }
    var categoriasFiltro by remember { mutableStateOf<Set<CategoriaEgreso>>(emptySet()) } 
    var mostrarFiltros by remember { mutableStateOf(false) }
    var incluirDescripcion by remember { mutableStateOf(true) }

    val (desde, hasta) = remember(filtroFechas) { 
        when (val f = filtroFechas) {
            is FiltroFechas.Ultimo -> DateUtils.rangoPara(f.periodo)
            is FiltroFechas.EspecificoDia -> DateUtils.rangoDiaEspecifico(f.anio, f.mes, f.dia)
            is FiltroFechas.EspecificoSemana -> DateUtils.rangoSemanaEspecifica(f.anio, f.mes, f.semana)
            is FiltroFechas.EspecificoMes -> DateUtils.rangoMesEspecifico(f.anio, f.mes)
            is FiltroFechas.EspecificoAnio -> DateUtils.rangoAnioEspecifico(f.anio)
        }
    }
    
    val egresos by produceState(initialValue = emptyList<Egreso>(), container, desde, hasta) {
        value = container.finanzasRepository.porRangoOnce(desde, hasta)
    }

    val primeraFechaEgreso by container.finanzasRepository.getPrimeraFechaEgreso().collectAsState(initial = null)

    var exportDialogVisible by remember { mutableStateOf(false) }
    
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingExportFile by remember { mutableStateOf<Pair<File, String>?>(null) } // File, filenameToSave
    val safLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        if (uri != null && pendingExportFile != null) {
            val (file, _) = pendingExportFile!!
            scope.launch(Dispatchers.IO) {
                try {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        file.inputStream().copyTo(output)
                    }
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Archivo guardado exitosamente", android.widget.Toast.LENGTH_LONG).show()
                    }
                } catch (e: Exception) {
                    android.util.Log.e("ReportesFinanzas", "Error al guardar archivo SAF: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Error al guardar archivo: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    ReportesFinanzasContent(
        filtroFechas = filtroFechas,
        categoriasFiltro = categoriasFiltro,
        incluirDescripcion = incluirDescripcion,
        egresos = egresos,
        desde = desde,
        hasta = hasta,
        onBack = onBack,
        onFilterClick = { mostrarFiltros = true },
        onShareClick = { exportDialogVisible = true },
        onClearFilters = {
            filtroFechas = FiltroFechas.Ultimo(Periodo.MENSUAL)
            categoriasFiltro = emptySet()
            incluirDescripcion = true
        }
    )

    if (exportDialogVisible) {
        val sdf = SimpleDateFormat("ddMMM-yyyy", Locale("es", "MX"))
        val filenameBase = "RanchoSanMartin_ReporteFinanciero_${sdf.format(java.util.Date(desde))}-${sdf.format(java.util.Date(hasta))}".replace(" ", "")
        
        val catsStr = if (categoriasFiltro.isEmpty()) "Todas" else categoriasFiltro.joinToString(", ") { categoriaLabel(it) }

        fun shareFile(file: File, mimeType: String) {
            scope.launch(Dispatchers.Main) {
                try {
                    val authority = "${context.packageName}.fileprovider"
                    val uri = androidx.core.content.FileProvider.getUriForFile(context, authority, file)
                    
                    val intent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
                        type = mimeType
                        putExtra(android.content.Intent.EXTRA_STREAM, uri)
                        clipData = android.content.ClipData.newRawUri("", uri)
                        addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    }
                    val chooser = android.content.Intent.createChooser(intent, "Compartir Reporte").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    android.util.Log.e("ReportesFinanzas", "Error al compartir archivo: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        val paginasPdf = if (egresos.isEmpty()) 2 else if (egresos.size <= 15) 2 else (1 + kotlin.math.ceil((egresos.size - 15) / 24.0).toInt() + 1)

        ExportarDialog(
            totalPaginasPdf = paginasPdf,
            onDismiss = { exportDialogVisible = false },
            onExportPdf = { guardarSAF ->
                exportDialogVisible = false
                scope.launch {
                    val reportDir = File(context.cacheDir, "reportes").apply { if (!exists()) mkdirs() }
                    val fileName = "$filenameBase.pdf"
                    val file = File(reportDir, fileName)
                    val success = withContext(Dispatchers.IO) {
                        PdfGenerator.generatePdf(context, file, egresos, desde, hasta, catsStr, incluirDescripcion)
                    }
                    if (success) {
                        if (guardarSAF) {
                            pendingExportFile = Pair(file, fileName)
                            safLauncher.launch(fileName)
                        } else {
                            shareFile(file, "application/pdf")
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Error al generar PDF", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            },
            onExportExcel = { guardarSAF ->
                exportDialogVisible = false
                scope.launch {
                    val reportDir = File(context.cacheDir, "reportes").apply { if (!exists()) mkdirs() }
                    val fileName = "$filenameBase.xlsx"
                    val file = File(reportDir, fileName)
                    val success = withContext(Dispatchers.IO) {
                        ExcelGenerator.generateExcel(context, file, egresos, desde, hasta, catsStr, incluirDescripcion)
                    }
                    if (success) {
                        if (guardarSAF) {
                            pendingExportFile = Pair(file, fileName)
                            safLauncher.launch(fileName)
                        } else {
                            shareFile(file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
                        }
                    } else {
                        android.widget.Toast.makeText(context, "Error al generar Excel", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    if (mostrarFiltros) {
        val minYear = remember(primeraFechaEgreso) {
            primeraFechaEgreso?.let {
                val cal = Calendar.getInstance()
                 cal.timeInMillis = it
                cal.get(Calendar.YEAR)
            } ?: Calendar.getInstance().get(Calendar.YEAR)
        }
        val currentYear = Calendar.getInstance().get(Calendar.YEAR)
        val availableYears = (currentYear downTo minYear).toList()

        FinanzasFilterDialog(
            currentFiltroFechas = filtroFechas,
            currentCategorias = categoriasFiltro,
            currentIncluirDescripcion = incluirDescripcion,
            availableYears = availableYears,
            onDismiss = { mostrarFiltros = false },
            onApply = { newFiltroFechas, newCategorias, newIncluirDesc ->
                filtroFechas = newFiltroFechas
                categoriasFiltro = newCategorias
                incluirDescripcion = newIncluirDesc
                mostrarFiltros = false
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
private fun ReportesFinanzasContent(
    filtroFechas: FiltroFechas,
    categoriasFiltro: Set<CategoriaEgreso>,
    incluirDescripcion: Boolean,
    egresos: List<Egreso>,
    desde: Long,
    hasta: Long,
    onBack: () -> Unit,
    onFilterClick: () -> Unit,
    onShareClick: () -> Unit,
    onClearFilters: () -> Unit
) {
    val currency = remember { NumberFormat.getCurrencyInstance(Locale("es", "MX")) }
    val filtrados = remember(egresos, categoriasFiltro) {
        egresos.filter { categoriasFiltro.isEmpty() || categoriasFiltro.contains(it.categoria) }
    }
    val total = filtrados.sumOf { it.montoTotal }

    val isDefaultFilter = filtroFechas == FiltroFechas.Ultimo(Periodo.MENSUAL) && categoriasFiltro.isEmpty()

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = { 
            BackTopBar(
                title = "Reportes de Finanzas", 
                onBack = onBack,
                actions = {
                    IconButton(onClick = onShareClick) {
                        Icon(Icons.Default.Share, contentDescription = "Compartir")
                    }
                    IconButton(onClick = onFilterClick) {
                        BadgedBox(badge = {
                            if (!isDefaultFilter) {
                                Badge(containerColor = RanchoAccent)
                            }
                        }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtros")
                        }
                    }
                }
            ) 
        }
    ) { padding ->
        Column(Modifier.padding(padding).padding(horizontal = 16.dp)) {
            
            if (!isDefaultFilter) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtros activos", style = MaterialTheme.typography.labelSmall, color = RanchoTextSecondary)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClearFilters) {
                        Text("Restablecer", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                divider = {}
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Tabla de Movimientos", fontWeight = FontWeight.Bold) },
                    selectedContentColor = RanchoPrimary,
                    unselectedContentColor = RanchoTextSecondary
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Gráficos y Resumen", fontWeight = FontWeight.Bold) },
                    selectedContentColor = RanchoPrimary,
                    unselectedContentColor = RanchoTextSecondary
                )
            }
            Spacer(Modifier.height(16.dp))

            HorizontalPager(state = pagerState, modifier = Modifier.weight(1f)) { page ->
                if (page == 0) {
                    ReportesFinanzasTable(filtrados, total, currency, incluirDescripcion)
                } else {
                    ReportesFinanzasCharts(
                        filtrados = filtrados, 
                        total = total, 
                        currency = currency, 
                        desde = desde, 
                        hasta = hasta, 
                        isVisible = pagerState.currentPage == 1
                    )
                }
            }
        }
    }
}

@Composable
private fun ReportesFinanzasTable(
    filtrados: List<Egreso>,
    total: Double,
    currency: NumberFormat,
    incluirDescripcion: Boolean
) {
    // TABLA
    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            // Header de Tabla
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFF8FAFC))
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Fecha", modifier = Modifier.weight(1f), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                Text("Categoría", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                Text("Monto", modifier = Modifier.weight(1.2f), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                if (incluirDescripcion) {
                    Text("Descripción", modifier = Modifier.weight(1.5f), fontWeight = FontWeight.ExtraBold, style = MaterialTheme.typography.labelMedium)
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            // Filas de Tabla
            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(filtrados, key = { _, e -> e.idEgreso }) { index, egreso ->
                    val isEven = index % 2 == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isEven) Color.Transparent else Color(0xFFF1F5F9).copy(alpha = 0.5f))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(DateUtils.formatDay(egreso.fechaEgreso), modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                        Text(categoriaLabel(egreso.categoria), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(currency.format(egreso.montoTotal), modifier = Modifier.weight(1.2f), style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                        if (incluirDescripcion) {
                            Text(egreso.descripcion, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodySmall, maxLines = 2)
                        }
                    }
                    if (index < filtrados.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
                
                if (filtrados.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No se encontraron registros para este periodo.", color = MaterialTheme.colorScheme.outline)
                        }
                    }
                }
            }

            // Footer Total
            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFE2E8F0)) // Gris destacado
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Total:",
                    modifier = Modifier.weight(2.2f),
                    textAlign = TextAlign.End,
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = RanchoPrimary
                )
                Spacer(Modifier.width(16.dp))
                Text(
                    currency.format(total),
                    modifier = Modifier.weight(if (incluirDescripcion) 2.7f else 1.2f),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = RanchoPrimary
                )
            }
        }
    }
}


@Composable
private fun ReportesFinanzasCharts(
    filtrados: List<Egreso>,
    total: Double,
    currency: NumberFormat,
    desde: Long,
    hasta: Long,
    isVisible: Boolean
) {
    var showTotal by remember { mutableStateOf(false) }
    var showChart by remember { mutableStateOf(false) }
    
    LaunchedEffect(isVisible) {
        if (isVisible) {
            delay(100)
            showTotal = true
            delay(200)
            showChart = true
        } else {
            showTotal = false
            showChart = false
        }
    }

    val animatedTotal = remember { Animatable(0f) }
    LaunchedEffect(total, isVisible) {
        if (isVisible) {
            animatedTotal.animateTo(total.toFloat(), tween(800, easing = FastOutSlowInEasing))
        } else {
            animatedTotal.snapTo(0f)
        }
    }

    val dataPorCategoria = remember(filtrados) {
        CategoriaEgreso.values().map { cat ->
            cat to filtrados.filter { it.categoria == cat }.sumOf { it.montoTotal }
        }.filter { it.second > 0 }.sortedByDescending { it.second }
    }

    var isDona by remember { mutableStateOf(false) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            AnimatedVisibility(visible = showTotal, enter = slideInVertically { 50 } + fadeIn(tween(300))) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(Color(0xFFF1F5F9)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = RanchoPrimary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Total de egresos", style = MaterialTheme.typography.bodyMedium, color = RanchoTextSecondary)
                            Text(currency.format(animatedTotal.value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = RanchoPrimary)
                            Text("Del ${DateUtils.formatDay(desde)} al ${DateUtils.formatDay(hasta)}", style = MaterialTheme.typography.bodySmall, color = RanchoTextSecondary)
                        }
                    }
                }
            }
        }

        item {
            AnimatedVisibility(visible = showChart, enter = slideInVertically { 50 } + fadeIn(tween(300))) {
                ElevatedCard(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surface),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (isDona) "Distribución por categoría" else "Por categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                                Text(if (isDona) "Porcentaje del total de gastos" else "Comparativa de gastos en el periodo", style = MaterialTheme.typography.bodySmall, color = RanchoTextSecondary, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            // Toggle Button
                            Row(Modifier.clip(RoundedCornerShape(50)).background(Color(0xFFF1F5F9))) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (isDona) RanchoPrimary else Color.Transparent).clickable { isDona = true }.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Dona", color = if (isDona) Color.White else RanchoTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (!isDona) RanchoPrimary else Color.Transparent).clickable { isDona = false }.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Barras", color = if (!isDona) Color.White else RanchoTextSecondary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                            }
                        }

                        Spacer(Modifier.height(24.dp))

                        if (isDona) {
                            DonutChartSection(dataPorCategoria, total, currency, animatedTotal.value)
                        } else {
                            BarChartSection(dataPorCategoria, total, currency)
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DonutChartSection(data: List<Pair<CategoriaEgreso, Double>>, total: Double, currency: NumberFormat, animatedTotal: Float) {
    val animatedSweep = remember { Animatable(0f) }
    LaunchedEffect(data) {
        animatedSweep.animateTo(360f, tween(1000, easing = FastOutSlowInEasing))
    }

    Box(Modifier.fillMaxWidth().height(220.dp), contentAlignment = Alignment.Center) {
        Canvas(Modifier.size(200.dp)) {
            var startAngle = -90f
            val sweepFactor = animatedSweep.value / 360f
            data.forEach { (cat, amount) ->
                val sweep = ((amount / total) * 360f).toFloat() * sweepFactor
                drawArc(
                    color = getCategoryStyle(cat).accentColor,
                    startAngle = startAngle,
                    sweepAngle = sweep,
                    useCenter = false,
                    style = Stroke(width = 40.dp.toPx(), cap = StrokeCap.Butt)
                )
                startAngle += sweep
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(currency.format(animatedTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = RanchoPrimary)
            Text("Total", style = MaterialTheme.typography.bodySmall, color = RanchoTextSecondary)
        }
    }
    
    Spacer(Modifier.height(24.dp))
    
    data.forEachIndexed { index, (cat, amount) ->
        val pct = if (total > 0) (amount / total * 100) else 0.0
        val style = getCategoryStyle(cat)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(style.accentColor))
            Spacer(Modifier.width(12.dp))
            Text(categoriaLabel(cat), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(currency.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                Text(String.format(Locale.US, "%.1f%%", pct), style = MaterialTheme.typography.bodySmall, color = RanchoTextSecondary)
            }
        }
        if (index < data.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@Composable
fun BarChartSection(data: List<Pair<CategoriaEgreso, Double>>, total: Double, currency: NumberFormat) {
    data.forEachIndexed { index, (cat, amount) ->
        val pct = if (total > 0) (amount / total * 100) else 0.0
        val pctFloat = if (total > 0) (amount / total).toFloat() else 0f
        val style = getCategoryStyle(cat)
        
        val animatedWidth = remember { Animatable(0f) }
        LaunchedEffect(data) {
            delay(index * 150L)
            animatedWidth.animateTo(pctFloat, tween(700, easing = FastOutSlowInEasing))
        }

        Row(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(40.dp).clip(CircleShape).background(style.accentColor), contentAlignment = Alignment.Center) {
                Icon(ImageVector.vectorResource(id = style.iconRes), null, tint = Color.White, modifier = Modifier.size(20.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(categoriaLabel(cat), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                    Text(currency.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(Color(0xFFF1F5F9))) {
                        Box(Modifier.fillMaxWidth(animatedWidth.value).fillMaxHeight().clip(RoundedCornerShape(50)).background(style.accentColor))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(String.format(Locale.US, "%.1f%%", pct), style = MaterialTheme.typography.bodySmall, color = RanchoTextSecondary, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
                }
            }
        }
        if (index < data.lastIndex) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FinanzasFilterDialog(
    currentFiltroFechas: FiltroFechas,
    currentCategorias: Set<CategoriaEgreso>,
    currentIncluirDescripcion: Boolean,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onApply: (FiltroFechas, Set<CategoriaEgreso>, Boolean) -> Unit
) {
    var incluirDescripcion by remember { mutableStateOf(currentIncluirDescripcion) }
    var tipoConsulta by remember { mutableStateOf(if (currentFiltroFechas is FiltroFechas.Ultimo) "Ultimo" else "Especifico") }
    
    // Variables para "Ultimo"
    var periodoUltimo by remember { 
        mutableStateOf(if (currentFiltroFechas is FiltroFechas.Ultimo) currentFiltroFechas.periodo else Periodo.MENSUAL) 
    }

    // Variables para "Especifico"
    var tipoEspecifico by remember {
        mutableStateOf(when (currentFiltroFechas) {
            is FiltroFechas.EspecificoDia -> "Dia"
            is FiltroFechas.EspecificoSemana -> "Semana"
            is FiltroFechas.EspecificoMes -> "Mes"
            is FiltroFechas.EspecificoAnio -> "Anio"
            else -> "Dia"
        })
    }
    
    val cal = Calendar.getInstance()
    var selAnio by remember { mutableStateOf(
        when(currentFiltroFechas) {
            is FiltroFechas.EspecificoDia -> currentFiltroFechas.anio
            is FiltroFechas.EspecificoSemana -> currentFiltroFechas.anio
            is FiltroFechas.EspecificoMes -> currentFiltroFechas.anio
            is FiltroFechas.EspecificoAnio -> currentFiltroFechas.anio
            else -> cal.get(Calendar.YEAR)
        }
    )}
    var selMes by remember { mutableStateOf(
        when(currentFiltroFechas) {
            is FiltroFechas.EspecificoDia -> currentFiltroFechas.mes
            is FiltroFechas.EspecificoSemana -> currentFiltroFechas.mes
            is FiltroFechas.EspecificoMes -> currentFiltroFechas.mes
            else -> cal.get(Calendar.MONTH)
        }
    )}
    var selDia by remember { mutableStateOf(
        if (currentFiltroFechas is FiltroFechas.EspecificoDia) currentFiltroFechas.dia else cal.get(Calendar.DAY_OF_MONTH)
    )}
    var selSemana by remember { mutableStateOf(
        if (currentFiltroFechas is FiltroFechas.EspecificoSemana) currentFiltroFechas.semana else cal.get(Calendar.WEEK_OF_MONTH)
    )}

    var categorias by remember { mutableStateOf(currentCategorias) }

    val todasLasCategorias = CategoriaEgreso.values().toList()
    val meses = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    val semanas = listOf("Primera semana", "Segunda semana", "Tercera semana", "Cuarta semana", "Quinta semana")

    val (diasEnMes) = remember(selAnio, selMes) {
        val c = Calendar.getInstance()
        c.set(selAnio, selMes, 1)
        listOf(c.getActualMaximum(Calendar.DAY_OF_MONTH))
    }
    if (selDia > diasEnMes) selDia = diasEnMes

    var mesMenuAbierto by remember { mutableStateOf(false) }
    var anioMenuAbierto by remember { mutableStateOf(false) }
    var diaMenuAbierto by remember { mutableStateOf(false) }
    var semanaMenuAbierto by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterAlt, null, tint = RanchoPrimary)
                Spacer(Modifier.width(8.dp))
                Text("Filtros de Finanzas")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp), modifier = Modifier.verticalScroll(rememberScrollState())) {
                
                Text("Periodo de consulta", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = tipoConsulta == "Ultimo", onClick = { tipoConsulta = "Ultimo" })
                    Text("Último", modifier = Modifier.clickable { tipoConsulta = "Ultimo" })
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = tipoConsulta == "Especifico", onClick = { tipoConsulta = "Especifico" })
                    Text("Específico", modifier = Modifier.clickable { tipoConsulta = "Especifico" })
                }

            // Filtro: Periodo (Si es Ultimo)
            if (tipoConsulta == "Ultimo") {
                Column {
                    Text("Periodo", style = MaterialTheme.typography.labelMedium, color = RanchoTextSecondary)
                    Spacer(Modifier.height(4.dp))
                    androidx.compose.foundation.lazy.LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val pList = Periodo.values().toList()
                        items(count = pList.size, key = { pList[it].name }) { idx ->
                            val p = pList[idx]
                            FilterChip(
                                selected = periodoUltimo == p,
                                onClick = { periodoUltimo = p },
                                label = { Text(periodoLabel(p)) }
                            )
                        }
                    }
                }
            } else {
                    Column {
                        Text("Consultar por", style = MaterialTheme.typography.labelMedium, color = RanchoTextSecondary)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Dia", onClick = { tipoEspecifico = "Dia" })
                            Text("Día específico", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Dia" })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Semana", onClick = { tipoEspecifico = "Semana" })
                            Text("Semana específica", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Semana" })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Mes", onClick = { tipoEspecifico = "Mes" })
                            Text("Mes específico", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Mes" })
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Anio", onClick = { tipoEspecifico = "Anio" })
                            Text("Año completo", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Anio" })
                        }
                        
                        Spacer(Modifier.height(12.dp))
                        
                        // Selectores según el tipoEspecifico
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            // Año (siempre visible en específico)
                            ExposedDropdownMenuBox(
                                expanded = anioMenuAbierto, 
                                onExpandedChange = { anioMenuAbierto = it }, 
                                modifier = Modifier.weight(1f)
                            ) {
                                OutlinedTextField(
                                    value = selAnio.toString(), onValueChange = {}, readOnly = true,
                                    label = { Text("Año") }, 
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = anioMenuAbierto) },
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(expanded = anioMenuAbierto, onDismissRequest = { anioMenuAbierto = false }) {
                                    availableYears.forEach { yr ->
                                        DropdownMenuItem(text = { Text(yr.toString()) }, onClick = { selAnio = yr; anioMenuAbierto = false })
                                    }
                                }
                            }
                        }
                        
                        if (tipoEspecifico != "Anio") {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = mesMenuAbierto, 
                                    onExpandedChange = { mesMenuAbierto = it }, 
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = meses[selMes], onValueChange = {}, readOnly = true,
                                        label = { Text("Mes") }, 
                                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = mesMenuAbierto) },
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    ExposedDropdownMenu(expanded = mesMenuAbierto, onDismissRequest = { mesMenuAbierto = false }) {
                                        meses.forEachIndexed { i, m ->
                                            DropdownMenuItem(text = { Text(m) }, onClick = { selMes = i; mesMenuAbierto = false })
                                        }
                                    }
                                }
                            }
                        }
                        
                        if (tipoEspecifico == "Dia") {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = diaMenuAbierto, 
                                    onExpandedChange = { diaMenuAbierto = it }, 
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = selDia.toString(), onValueChange = {}, readOnly = true,
                                        label = { Text("Día") }, 
                                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = diaMenuAbierto) },
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    ExposedDropdownMenu(expanded = diaMenuAbierto, onDismissRequest = { diaMenuAbierto = false }) {
                                        (1..diasEnMes).forEach { d ->
                                            DropdownMenuItem(text = { Text(d.toString()) }, onClick = { selDia = d; diaMenuAbierto = false })
                                        }
                                    }
                                }
                            }
                        } else if (tipoEspecifico == "Semana") {
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                ExposedDropdownMenuBox(
                                    expanded = semanaMenuAbierto, 
                                    onExpandedChange = { semanaMenuAbierto = it }, 
                                    modifier = Modifier.weight(1f)
                                ) {
                                    OutlinedTextField(
                                        value = semanas.getOrElse(selSemana - 1) { "Otra" }, onValueChange = {}, readOnly = true,
                                        label = { Text("Semana") }, 
                                        modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = semanaMenuAbierto) },
                                        textStyle = MaterialTheme.typography.bodySmall
                                    )
                                    ExposedDropdownMenu(expanded = semanaMenuAbierto, onDismissRequest = { semanaMenuAbierto = false }) {
                                        semanas.forEachIndexed { i, s ->
                                            DropdownMenuItem(text = { Text(s) }, onClick = { selSemana = i + 1; semanaMenuAbierto = false })
                                        }
                                    }
                                }
                            }
                        }

                    }
                }

                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                Text("Categorías", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                
                // Opción "Todas"
                FilterChip(
                    selected = categorias.isEmpty(),
                    onClick = { categorias = emptySet() },
                    label = { Text("Todas las categorías") }
                )
                
                // Malla de opciones múltiples
                @OptIn(ExperimentalLayoutApi::class)
                FlowRow(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    todasLasCategorias.forEach { c ->
                        FilterChip(
                            selected = categorias.contains(c),
                            onClick = {
                                val nuevas = categorias.toMutableSet()
                                if (nuevas.contains(c)) nuevas.remove(c) else nuevas.add(c)
                                categorias = nuevas
                            },
                            label = { Text(categoriaLabel(c)) }
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
                HorizontalDivider()
                Spacer(Modifier.height(8.dp))
                
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(checked = incluirDescripcion, onCheckedChange = { incluirDescripcion = it })
                    Text("Incluir Descripción", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            Button(onClick = {
                val filtro = if (tipoConsulta == "Ultimo") {
                    FiltroFechas.Ultimo(periodoUltimo)
                } else {
                    when (tipoEspecifico) {
                        "Dia" -> FiltroFechas.EspecificoDia(selAnio, selMes, selDia)
                        "Semana" -> FiltroFechas.EspecificoSemana(selAnio, selMes, selSemana)
                        "Mes" -> FiltroFechas.EspecificoMes(selAnio, selMes)
                        "Anio" -> FiltroFechas.EspecificoAnio(selAnio)
                        else -> FiltroFechas.Ultimo(Periodo.MENSUAL)
                    }
                }
                onApply(filtro, categorias, incluirDescripcion) 
            }) { Text("Aplicar Filtros") }
        },
        dismissButton = {
            TextButton(onClick = { onApply(FiltroFechas.Ultimo(Periodo.MENSUAL), emptySet(), true) }) { Text("Restablecer") }
        }
    )
}

@Preview(showBackground = true)
@Composable
fun ReportesFinanzasPreview() {
    MaterialTheme {
        ReportesFinanzasContent(
            filtroFechas = FiltroFechas.Ultimo(Periodo.MENSUAL),
            categoriasFiltro = emptySet(),
            incluirDescripcion = true,
            egresos = listOf(
                Egreso(idEgreso = 1, categoria = CategoriaEgreso.ALIMENTO, montoTotal = 5000.0, fechaEgreso = System.currentTimeMillis(), idUsuario = 1, descripcion = "Compra de víveres")
            ),
            desde = System.currentTimeMillis(),
            hasta = System.currentTimeMillis(),
            onBack = {},
            onFilterClick = {},
            onShareClick = {},
            onClearFilters = {}
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExportarDialog(
    totalPaginasPdf: Int,
    onDismiss: () -> Unit,
    onExportPdf: (guardarSAF: Boolean) -> Unit,
    onExportExcel: (guardarSAF: Boolean) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                "Exportar informe financiero",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                // PDF Option Card
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_pdf),
                                contentDescription = "PDF",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Documento PDF", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "$totalPaginasPdf ${if (totalPaginasPdf == 1) "página" else "páginas"}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onExportPdf(true) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Guardar", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { onExportPdf(false) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }

                // Excel Option Card
                ElevatedCard(
                    shape = RoundedCornerShape(12.dp),
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = ImageVector.vectorResource(id = R.drawable.ic_excel),
                                contentDescription = "Excel",
                                tint = Color.Unspecified,
                                modifier = Modifier.size(40.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text("Libro de Excel (.xlsx)", fontWeight = FontWeight.Bold, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "3 hojas",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.height(14.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onExportExcel(true) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Guardar", maxLines = 1, softWrap = false)
                            }
                            Button(
                                onClick = { onExportExcel(false) },
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Compartir", maxLines = 1, softWrap = false)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancelar") }
        }
    )
}

