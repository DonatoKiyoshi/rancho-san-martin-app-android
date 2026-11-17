package com.rancho.gestion.ui.reportes

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.unit.sp
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.finanzas.categoriaLabel
import com.rancho.gestion.ui.finanzas.getCategoryStyle
import com.rancho.gestion.ui.theme.RanchoAccent
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.ui.theme.SanMartinOrange
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.ExcelGenerator
import com.rancho.gestion.util.FiltroFechas
import com.rancho.gestion.util.PdfGenerator
import com.rancho.gestion.util.Periodo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReportesFinanzasScreen(container: AppContainer, onBack: () -> Unit) {
    var filtroFechas by remember { mutableStateOf<FiltroFechas>(FiltroFechas.Ultimo(Periodo.MENSUAL)) }
    var categoriasFiltro by remember { mutableStateOf<Set<CategoriaEgreso>>(emptySet()) }
    var incluirDescripcion by remember { mutableStateOf(true) }
    var mostrarFiltros by remember { mutableStateOf(false) }
    var exportDialogVisible by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var pendingExportFile by remember { mutableStateOf<Pair<File, String>?>(null) }
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

    val (desde, hasta) = remember(filtroFechas) {
        when (val f = filtroFechas) {
            is FiltroFechas.Ultimo -> DateUtils.rangoPara(f.periodo)
            is FiltroFechas.EspecificoDia -> DateUtils.rangoDiaEspecifico(f.anio, f.mes, f.dia)
            is FiltroFechas.EspecificoSemana -> DateUtils.rangoSemanaEspecifica(f.anio, f.mes, f.semana)
            is FiltroFechas.EspecificoMes -> DateUtils.rangoMesEspecifico(f.anio, f.mes)
            is FiltroFechas.EspecificoAnio -> DateUtils.rangoAnioEspecifico(f.anio)
        }
    }

    val egresos by container.finanzasRepository.porRango(desde, hasta).collectAsState(initial = emptyList())
    val todosLosEgresos by container.finanzasRepository.porRango(0, Long.MAX_VALUE).collectAsState(initial = emptyList())

    val availableYears = remember(todosLosEgresos) {
        val cal = Calendar.getInstance()
        val years = todosLosEgresos.map { egreso ->
            cal.timeInMillis = egreso.fechaEgreso
            cal.get(Calendar.YEAR)
        }.toSet()
        if (years.isEmpty()) {
            listOf(cal.get(Calendar.YEAR))
        } else {
            years.sortedDescending()
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
        val sdf = SimpleDateFormat("ddMMM-yyyy", Locale.forLanguageTag("es-MX"))
        val filenameBase = "RanchoSanMartin_ReporteFinanzas_${sdf.format(Date(desde))}-${sdf.format(Date(hasta))}".replace(" ", "")

        val filtroCategoriasTxt = if (categoriasFiltro.isEmpty()) "Todas" else categoriasFiltro.joinToString(", ") { categoriaLabel(it) }
        val filtrados = egresos.filter { categoriasFiltro.isEmpty() || categoriasFiltro.contains(it.categoria) }

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
                    val chooser = android.content.Intent.createChooser(intent, "Compartir Reporte de Finanzas").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    android.util.Log.e("ReportesFinanzas", "Error al compartir archivo: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        ExportarFinanzasDialog(
            totalRegistros = filtrados.size,
            onDismiss = { exportDialogVisible = false },
            onExportPdf = { guardarSAF ->
                exportDialogVisible = false
                scope.launch {
                    val reportDir = File(context.cacheDir, "reportes").apply { if (!exists()) mkdirs() }
                    val fileName = "$filenameBase.pdf"
                    val file = File(reportDir, fileName)
                    val success = withContext(Dispatchers.IO) {
                        PdfGenerator.generatePdf(
                            context = context,
                            file = file,
                            egresos = filtrados,
                            desde = desde,
                            hasta = hasta,
                            filtroCategorias = filtroCategoriasTxt,
                            incluirDescripcion = incluirDescripcion
                        )
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
                        ExcelGenerator.generateExcel(
                            context = context,
                            file = file,
                            egresos = filtrados,
                            desde = desde,
                            hasta = hasta,
                            filtroCategorias = filtroCategoriasTxt,
                            incluirDescripcion = incluirDescripcion
                        )
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

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")) }
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
                        Icon(Icons.Default.Share, contentDescription = "Compartir Reporte", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onFilterClick) {
                        BadgedBox(badge = {
                            if (!isDefaultFilter) {
                                Badge(containerColor = RanchoAccent)
                            }
                        }) {
                            Icon(Icons.Default.FilterList, contentDescription = "Filtros", tint = MaterialTheme.colorScheme.onBackground)
                        }
                    }
                }
            ) 
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 16.dp)
        ) {
            
            if (!isDefaultFilter) {
                Spacer(Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Filtros activos", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.weight(1f))
                    TextButton(onClick = onClearFilters) {
                        Text("Restablecer", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                }
            }

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Tabla de Movimientos", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Tab(
                    selected = pagerState.currentPage == 1,
                    onClick = { scope.launch { pagerState.animateScrollToPage(1) } },
                    text = { Text("Gráficos y Resumen", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
                    selectedContentColor = MaterialTheme.colorScheme.primary,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant
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
private fun ExportarFinanzasDialog(
    totalRegistros: Int,
    onDismiss: () -> Unit,
    onExportPdf: (guardarSAF: Boolean) -> Unit,
    onExportExcel: (guardarSAF: Boolean) -> Unit
) {
    androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.35f))
        ) {
            Column(
                modifier = Modifier
                    .padding(22.dp)
                    .fillMaxWidth()
            ) {
                // Header Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.primaryContainer),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Exportar Informe",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Finanzas",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(36.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Cerrar",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Card PDF
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFFFEBEE)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_pdf),
                                    contentDescription = "PDF",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Documento PDF",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "$totalRegistros egresos registrados",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onExportPdf(true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { onExportPdf(false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SanMartinOrange),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartir", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Card Excel
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(Color(0xFFE8F5E9)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_excel),
                                    contentDescription = "Excel",
                                    tint = Color.Unspecified,
                                    modifier = Modifier.size(28.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Libro de Excel (.xlsx)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Hoja con tabla completa y resúmenes",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            OutlinedButton(
                                onClick = { onExportExcel(true) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Save,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Guardar", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                            }

                            Button(
                                onClick = { onExportExcel(false) },
                                modifier = Modifier.weight(1f),
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = SanMartinOrange),
                                contentPadding = PaddingValues(vertical = 10.dp, horizontal = 12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Share,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Compartir", fontWeight = FontWeight.Bold, maxLines = 1, fontSize = 13.sp)
                            }
                        }
                    }
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
    val widthFecha = 78.dp

    ElevatedCard(
        modifier = Modifier.fillMaxSize(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Fecha",
                    modifier = Modifier.width(widthFecha),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Categoría",
                    modifier = Modifier.weight(1.1f),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Monto",
                    modifier = Modifier.weight(1.1f),
                    fontWeight = FontWeight.ExtraBold,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                if (incluirDescripcion) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = "Descripción",
                        modifier = Modifier.weight(1.3f),
                        fontWeight = FontWeight.ExtraBold,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))

            LazyColumn(modifier = Modifier.weight(1f)) {
                itemsIndexed(filtrados, key = { _, e -> e.idEgreso }) { index, egreso ->
                    val isEven = index % 2 == 0
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(if (isEven) Color.Transparent else MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.15f))
                            .padding(horizontal = 12.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = DateUtils.formatDay(egreso.fechaEgreso),
                            modifier = Modifier.width(widthFecha),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = categoriaLabel(egreso.categoria),
                            modifier = Modifier.weight(1.1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = currency.format(egreso.montoTotal),
                            modifier = Modifier.weight(1.1f),
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        if (incluirDescripcion) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = egreso.descripcion.ifBlank { "-" },
                                modifier = Modifier.weight(1.3f),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (index < filtrados.lastIndex) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f))
                    }
                }
                
                if (filtrados.isEmpty()) {
                    item {
                        Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                            Text("No se encontraron registros para este periodo.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            HorizontalDivider(thickness = 2.dp, color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.primaryContainer)
                    .padding(horizontal = 12.dp, vertical = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Total:",
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = currency.format(total),
                    fontWeight = FontWeight.Black,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
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
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(56.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(Icons.Default.AccountBalanceWallet, null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                        }
                        Spacer(Modifier.width(16.dp))
                        Column {
                            Text("Total de egresos", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(currency.format(animatedTotal.value), style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
                            Text("Del ${DateUtils.formatDay(desde)} al ${DateUtils.formatDay(hasta)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                    elevation = CardDefaults.elevatedCardElevation(2.dp)
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text(if (isDona) "Distribución por categoría" else "Por categoría", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Text(if (isDona) "Porcentaje del total de gastos" else "Comparativa de gastos en el periodo", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                            }
                            Spacer(Modifier.width(8.dp))
                            Row(Modifier.clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))) {
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (isDona) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { isDona = true }.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Dona", color = if (isDona) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
                                Box(
                                    Modifier.clip(RoundedCornerShape(50)).background(if (!isDona) MaterialTheme.colorScheme.primary else Color.Transparent).clickable { isDona = false }.padding(horizontal = 12.dp, vertical = 6.dp)
                                ) { Text("Barras", color = if (!isDona) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold) }
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
            Text(currency.format(animatedTotal), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Black, color = MaterialTheme.colorScheme.primary)
            Text("Total", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
    
    Spacer(Modifier.height(24.dp))
    
    data.forEachIndexed { index, (cat, amount) ->
        val pct = if (total > 0) (amount / total * 100) else 0.0
        val style = getCategoryStyle(cat)
        Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(16.dp).clip(CircleShape).background(style.accentColor))
            Spacer(Modifier.width(12.dp))
            Text(categoriaLabel(cat), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.weight(1f))
            Column(horizontalAlignment = Alignment.End) {
                Text(currency.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Text(String.format(Locale.US, "%.1f%%", pct), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                    Text(categoriaLabel(cat), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    Text(currency.format(amount), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
                Spacer(Modifier.height(6.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.weight(1f).height(8.dp).clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))) {
                        Box(Modifier.fillMaxWidth(animatedWidth.value).fillMaxHeight().clip(RoundedCornerShape(50)).background(style.accentColor))
                    }
                    Spacer(Modifier.width(12.dp))
                    Text(String.format(Locale.US, "%.1f%%", pct), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
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
    var tipoConsulta by remember { mutableStateOf(if (currentFiltroFechas is FiltroFechas.Ultimo) "Ultimo" else "Especifico") }
    var periodoUltimo by remember {
        mutableStateOf(if (currentFiltroFechas is FiltroFechas.Ultimo) currentFiltroFechas.periodo else Periodo.MENSUAL)
    }

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
            else -> if (availableYears.contains(cal.get(Calendar.YEAR))) cal.get(Calendar.YEAR) else availableYears.firstOrNull() ?: cal.get(Calendar.YEAR)
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

    LaunchedEffect(availableYears) {
        if (availableYears.isNotEmpty() && selAnio !in availableYears) {
            selAnio = availableYears.first()
        }
    }

    val meses = listOf("Enero", "Febrero", "Marzo", "Abril", "Mayo", "Junio", "Julio", "Agosto", "Septiembre", "Octubre", "Noviembre", "Diciembre")
    val semanas = listOf("Primera semana", "Segunda semana", "Tercera semana", "Cuarta semana", "Quinta semana")

    val diasEnMes = remember(selAnio, selMes) {
        val c = Calendar.getInstance()
        c.set(selAnio, selMes, 1)
        c.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    if (selDia > diasEnMes) selDia = diasEnMes

    var mesMenuAbierto by remember { mutableStateOf(false) }
    var anioMenuAbierto by remember { mutableStateOf(false) }
    var diaMenuAbierto by remember { mutableStateOf(false) }
    var semanaMenuAbierto by remember { mutableStateOf(false) }

    var categorias by remember { mutableStateOf(currentCategorias) }
    var incluirDesc by remember { mutableStateOf(currentIncluirDescripcion) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterAlt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Filtros de Finanzas", color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                Text("1. Periodo y Fechas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RadioButton(selected = tipoConsulta == "Ultimo", onClick = { tipoConsulta = "Ultimo" })
                    Text("Último", modifier = Modifier.clickable { tipoConsulta = "Ultimo" }, color = MaterialTheme.colorScheme.onSurface)
                    Spacer(Modifier.width(16.dp))
                    RadioButton(selected = tipoConsulta == "Especifico", onClick = { tipoConsulta = "Especifico" })
                    Text("Específico", modifier = Modifier.clickable { tipoConsulta = "Especifico" }, color = MaterialTheme.colorScheme.onSurface)
                }

                if (tipoConsulta == "Ultimo") {
                    Column {
                        Text("Periodo", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Spacer(Modifier.height(4.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Periodo.entries.forEach { p ->
                                FilterChip(
                                    selected = periodoUltimo == p,
                                    onClick = { periodoUltimo = p },
                                    label = { Text(periodoLabel(p), fontSize = 11.sp) }
                                )
                            }
                        }
                    }
                } else {
                    Column {
                        Text("Consultar por", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Dia", onClick = { tipoEspecifico = "Dia" })
                            Text("Día específico", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Dia" }, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Semana", onClick = { tipoEspecifico = "Semana" })
                            Text("Semana específica", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Semana" }, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Mes", onClick = { tipoEspecifico = "Mes" })
                            Text("Mes específico", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Mes" }, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(selected = tipoEspecifico == "Anio", onClick = { tipoEspecifico = "Anio" })
                            Text("Año completo", style = MaterialTheme.typography.bodySmall, modifier = Modifier.clickable { tipoEspecifico = "Anio" }, color = MaterialTheme.colorScheme.onSurface)
                        }

                        Spacer(Modifier.height(8.dp))

                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
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
                            Spacer(Modifier.height(6.dp))
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
                            Spacer(Modifier.height(6.dp))
                            ExposedDropdownMenuBox(
                                expanded = diaMenuAbierto,
                                onExpandedChange = { diaMenuAbierto = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = "Día $selDia", onValueChange = {}, readOnly = true,
                                    label = { Text("Día") },
                                    modifier = Modifier.menuAnchor(type = ExposedDropdownMenuAnchorType.PrimaryEditable, enabled = true),
                                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = diaMenuAbierto) },
                                    textStyle = MaterialTheme.typography.bodySmall
                                )
                                ExposedDropdownMenu(expanded = diaMenuAbierto, onDismissRequest = { diaMenuAbierto = false }) {
                                    (1..diasEnMes).forEach { d ->
                                        DropdownMenuItem(text = { Text("Día $d") }, onClick = { selDia = d; diaMenuAbierto = false })
                                    }
                                }
                            }
                        }

                        if (tipoEspecifico == "Semana") {
                            Spacer(Modifier.height(6.dp))
                            ExposedDropdownMenuBox(
                                expanded = semanaMenuAbierto,
                                onExpandedChange = { semanaMenuAbierto = it },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedTextField(
                                    value = semanas.getOrElse(selSemana - 1) { "Semana $selSemana" }, onValueChange = {}, readOnly = true,
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                Text("2. Categorías de Egreso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FilterChip(
                    selected = categorias.isEmpty(),
                    onClick = { categorias = emptySet() },
                    label = { Text("Todas las categorías", fontSize = 12.sp) }
                )
                Row(
                    modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    CategoriaEgreso.entries.forEach { cat ->
                        FilterChip(
                            selected = categorias.contains(cat),
                            onClick = {
                                val set = categorias.toMutableSet()
                                if (set.contains(cat)) set.remove(cat) else set.add(cat)
                                if (set.size == CategoriaEgreso.entries.size) categorias = emptySet() else categorias = set
                            },
                            label = { Text(categoriaLabel(cat), fontSize = 11.sp) }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val filtroFec = if (tipoConsulta == "Ultimo") {
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
                    onApply(filtroFec, categorias, incluirDesc)
                }
            ) {
                Text("Aplicar Filtros")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onApply(FiltroFechas.Ultimo(Periodo.MENSUAL), emptySet(), true)
                }
            ) {
                Text("Restablecer")
            }
        }
    )
}
