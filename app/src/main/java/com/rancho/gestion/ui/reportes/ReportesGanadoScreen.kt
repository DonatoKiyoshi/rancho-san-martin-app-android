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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Female
import androidx.compose.material.icons.filled.FilterAlt
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Male
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Scale
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.InfoDialog
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.ui.theme.SanMartinOrange
import com.rancho.gestion.util.DateUtils
import com.rancho.gestion.util.ExcelGanadoGenerator
import com.rancho.gestion.util.FiltroFechas
import com.rancho.gestion.util.PdfGanadoGenerator
import com.rancho.gestion.util.Periodo
import com.rancho.gestion.util.RangoStat
import com.rancho.gestion.util.ReporteGanadoDataCalculator
import com.rancho.gestion.util.ReporteGanadoExportDataBuilder
import com.rancho.gestion.util.ReporteGanadoStats
import com.rancho.gestion.util.SexoStat
import com.rancho.gestion.util.TipoAreteStat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

// Data model para cada fila del reporte
data class ReporteGanadoRow(
    val numeroArete: String,
    val tipoArete: String,
    val fechaEntradaTexto: String,
    val color: String,
    val sexo: String,
    val estado: String, // "Activo", "Vendido", "Muerte"
    val pesoEntrada: Double,
    val pesoEntradaTexto: String,
    val pesoSalida: Double?,
    val pesoSalidaTexto: String,
    val rangoPeso: String,
    val precioCompra: Double,
    val precioCompraTexto: String,
    val precioVenta: Double?,
    val precioVentaTexto: String,
    val proveedor: String,
    val comprador: String,
    val fechaSalidaTexto: String,
    val fotografiaLocal: String?
)

fun periodoLabel(p: Periodo): String = when (p) {
    Periodo.DIARIO -> "Diario"
    Periodo.SEMANAL -> "Semanal"
    Periodo.MENSUAL -> "Mensual"
    Periodo.ANUAL -> "Anual"
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReportesGanadoScreen(
    container: AppContainer,
    onBack: () -> Unit,
    initialPeriodo: String = "MENSUAL",
    initialEstado: String = "TODOS"
) {
    val defaultFiltroFechas = remember(initialPeriodo) {
        if (initialPeriodo.uppercase() == "ANUAL") FiltroFechas.Ultimo(Periodo.ANUAL)
        else FiltroFechas.Ultimo(Periodo.MENSUAL)
    }
    val defaultEstados = remember(initialEstado) {
        if (initialEstado.uppercase() == "ACTIVO") setOf(EstadoInventario.ACTIVO)
        else emptySet()
    }

    var filtroFechas by remember(initialPeriodo) { mutableStateOf<FiltroFechas>(defaultFiltroFechas) }
    var tipoAreteFiltro by remember { mutableStateOf("AMBOS") }
    var coloresFiltro by remember { mutableStateOf<Set<String>>(emptySet()) }
    var estadosFiltro by remember(initialEstado) { mutableStateOf<Set<EstadoInventario>>(defaultEstados) }
    var rangosPesoFiltroIds by remember { mutableStateOf<Set<String>>(emptySet()) }

    var mostrarModalFiltros by remember { mutableStateOf(false) }
    var exportDialogVisible by remember { mutableStateOf(false) }

    var fotoSeleccionadaArete by remember { mutableStateOf<String?>(null) }
    var fotoSeleccionadaUrl by remember { mutableStateOf<String?>(null) }

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
                    android.util.Log.e("ReportesGanado", "Error al guardar archivo SAF: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Error al guardar archivo: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    val activos by container.ganadoRepository.activos().collectAsState(initial = emptyList())
    val archivados by container.ganadoRepository.archivados().collectAsState(initial = emptyList())
    val rangos by container.ganadoRepository.rangosPeso().collectAsState(initial = emptyList())
    val ventas by container.ventaRepository.getVentas().collectAsState(initial = emptyList())
    val detallesVenta by container.ventaRepository.getTodosLosDetalles().collectAsState(initial = emptyList())

    val (desde, hasta) = remember(filtroFechas) {
        when (val f = filtroFechas) {
            is FiltroFechas.Ultimo -> DateUtils.rangoPara(f.periodo)
            is FiltroFechas.EspecificoDia -> DateUtils.rangoDiaEspecifico(f.anio, f.mes, f.dia)
            is FiltroFechas.EspecificoSemana -> DateUtils.rangoSemanaEspecifica(f.anio, f.mes, f.semana)
            is FiltroFechas.EspecificoMes -> DateUtils.rangoMesEspecifico(f.anio, f.mes)
            is FiltroFechas.EspecificoAnio -> DateUtils.rangoAnioEspecifico(f.anio)
        }
    }

    val ventasMap = remember(ventas) { ventas.associateBy { it.idVenta } }
    val detallesMap = remember(detallesVenta) { detallesVenta.associateBy { it.numeroArete } }
    val todosLosAnimales = remember(activos, archivados) { activos + archivados }

    val availableYears = remember(todosLosAnimales) {
        val cal = Calendar.getInstance()
        val years = todosLosAnimales.flatMap { animal ->
            val list = mutableListOf<Int>()
            cal.timeInMillis = animal.fechaEntrada
            list.add(cal.get(Calendar.YEAR))
            animal.fechaBaja?.let { fecha ->
                cal.timeInMillis = fecha
                list.add(cal.get(Calendar.YEAR))
            }
            list
        }.toSet()
        if (years.isEmpty()) {
            listOf(cal.get(Calendar.YEAR))
        } else {
            years.sortedDescending()
        }
    }

    val hayFiltrosActivos = remember(filtroFechas, tipoAreteFiltro, coloresFiltro, estadosFiltro, rangosPesoFiltroIds) {
        filtroFechas !is FiltroFechas.Ultimo ||
        (filtroFechas as FiltroFechas.Ultimo).periodo != Periodo.MENSUAL ||
        tipoAreteFiltro != "AMBOS" ||
        coloresFiltro.isNotEmpty() ||
        estadosFiltro.isNotEmpty() ||
        rangosPesoFiltroIds.isNotEmpty()
    }

    val animalesFiltrados = remember(
        todosLosAnimales,
        desde,
        hasta,
        tipoAreteFiltro,
        coloresFiltro,
        estadosFiltro,
        rangosPesoFiltroIds,
        rangos
    ) {
        todosLosAnimales.filter { animal ->
            val fechaRelevante = if (animal.estadoInventario == EstadoInventario.ACTIVO) {
                animal.fechaEntrada
            } else {
                animal.fechaBaja ?: animal.fechaEntrada
            }
            val pasaFecha = (fechaRelevante in desde..hasta) || (animal.fechaEntrada in desde..hasta)

            val pasaTipoArete = when (tipoAreteFiltro) {
                "SADER" -> animal.tipoArete == TipoArete.SADER
                "OTRO" -> animal.tipoArete == TipoArete.OTRO
                else -> true
            }

            val pasaColor = coloresFiltro.isEmpty() || coloresFiltro.contains(animal.color)
            val pasaEstado = estadosFiltro.isEmpty() || estadosFiltro.contains(animal.estadoInventario)

            val pasaRangoPeso = if (rangosPesoFiltroIds.isEmpty()) {
                true
            } else {
                val rangosSel = rangos.filter { rangosPesoFiltroIds.contains(it.idRangoPeso) }
                rangosSel.any { rango ->
                    animal.pesoEntrada >= rango.limiteInferior && animal.pesoEntrada <= rango.limiteSuperior
                }
            }

            pasaFecha && pasaTipoArete && pasaColor && pasaEstado && pasaRangoPeso
        }
    }

    val stats = remember(animalesFiltrados, rangos) {
        ReporteGanadoDataCalculator.calcular(animalesFiltrados, rangos)
    }

    val tablaRows = remember(animalesFiltrados, detallesMap, ventasMap, rangos) {
        animalesFiltrados.map { animal ->
            val detalle = detallesMap[animal.numeroArete]
            val venta = detalle?.let { ventasMap[it.idVenta] }

            val rangoMatched = rangos.firstOrNull {
                animal.pesoEntrada >= it.limiteInferior && animal.pesoEntrada <= it.limiteSuperior
            }

            val (pesoSalida, pesoSalidaTxt) = when (animal.estadoInventario) {
                EstadoInventario.VENDIDO -> {
                    val p = detalle?.pesoSalida
                    p to (p?.let { "${it.toInt()} kg" } ?: "N/A")
                }
                else -> null to "N/A"
            }

            val (precioVenta, precioVentaTxt) = when (animal.estadoInventario) {
                EstadoInventario.VENDIDO -> {
                    val sub = detalle?.subtotal
                    sub to (sub?.let { "$${String.format(Locale.getDefault(), "%,.2f", it)}" } ?: "N/A")
                }
                else -> null to "N/A"
            }

            val compradorTxt = if (animal.estadoInventario == EstadoInventario.VENDIDO) {
                venta?.comprador?.ifBlank { "N/A" } ?: "N/A"
            } else "N/A"

            val fechaSalidaTxt = when (animal.estadoInventario) {
                EstadoInventario.VENDIDO -> venta?.let { DateUtils.formatDay(it.fechaVenta) } ?: (animal.fechaBaja?.let { DateUtils.formatDay(it) } ?: "N/A")
                EstadoInventario.MUERTO -> animal.fechaBaja?.let { DateUtils.formatDay(it) } ?: "N/A"
                else -> "N/A"
            }

            ReporteGanadoRow(
                numeroArete = animal.numeroArete,
                tipoArete = if (animal.tipoArete == TipoArete.SADER) "SADER" else "GENÉRICO",
                fechaEntradaTexto = DateUtils.formatDay(animal.fechaEntrada),
                color = animal.color.ifBlank { "N/A" },
                sexo = if (animal.sexo == Sexo.MACHO) "Macho" else "Hembra",
                estado = when (animal.estadoInventario) {
                    EstadoInventario.ACTIVO -> "Activo"
                    EstadoInventario.VENDIDO -> "Vendido"
                    EstadoInventario.MUERTO -> "Muerte"
                },
                pesoEntrada = animal.pesoEntrada,
                pesoEntradaTexto = "${animal.pesoEntrada.toInt()} kg",
                pesoSalida = pesoSalida,
                pesoSalidaTexto = pesoSalidaTxt,
                rangoPeso = rangoMatched?.nombreRango ?: "N/A",
                precioCompra = animal.precioCompra,
                precioCompraTexto = "$${String.format(Locale.getDefault(), "%,.2f", animal.precioCompra)}",
                precioVenta = precioVenta,
                precioVentaTexto = precioVentaTxt,
                proveedor = animal.proveedor.ifBlank { "N/A" },
                comprador = compradorTxt,
                fechaSalidaTexto = fechaSalidaTxt,
                fotografiaLocal = animal.fotografiaLocal
            )
        }
    }

    ReportesGanadoContent(
        stats = stats,
        tablaRows = tablaRows,
        hayFiltrosActivos = hayFiltrosActivos,
        onOpenFiltros = { mostrarModalFiltros = true },
        onOpenExport = { exportDialogVisible = true },
        onResetFiltros = {
            filtroFechas = FiltroFechas.Ultimo(Periodo.MENSUAL)
            tipoAreteFiltro = "AMBOS"
            coloresFiltro = emptySet()
            estadosFiltro = emptySet()
            rangosPesoFiltroIds = emptySet()
        },
        onShowFoto = { arete, fotoUrl ->
            fotoSeleccionadaArete = arete
            fotoSeleccionadaUrl = fotoUrl
        },
        onBack = onBack
    )

    if (exportDialogVisible) {
        val locale = remember { Locale.getDefault() }
        val sdf = SimpleDateFormat("ddMMM-yyyy", Locale.forLanguageTag("es-MX"))
        val filenameBase = "RanchoSanMartin_ReporteGanado_${sdf.format(Date(desde))}-${sdf.format(Date(hasta))}".replace(" ", "")

        val periodoTxtStr = "Del ${DateUtils.formatDay(desde)} al ${DateUtils.formatDay(hasta)}"
        val filtrosTxtStr = buildString {
            append("tipo de arete: ${tipoAreteFiltro.lowercase(locale)}")
            if (coloresFiltro.isNotEmpty()) append(", color: ${coloresFiltro.joinToString(", ").lowercase(locale)}") else append(", color: todos")
            if (estadosFiltro.isNotEmpty()) append(", estado: ${estadosFiltro.joinToString(", ") { it.name.lowercase(locale) }}") else append(", estado: todos")
            if (rangosPesoFiltroIds.isNotEmpty()) {
                val rNames = rangos.filter { it.idRangoPeso in rangosPesoFiltroIds }.joinToString(", ") { it.nombreRango.lowercase(locale) }
                append(", rango de peso: $rNames")
            } else {
                append(", rango de peso: todos")
            }
        }

        val exportData = remember(stats, tablaRows, rangos, periodoTxtStr, filtrosTxtStr) {
            ReporteGanadoExportDataBuilder.preparar(
                nombreRancho = "Rancho San Martín",
                periodoTxt = periodoTxtStr,
                filtrosAplicadosTxt = filtrosTxtStr,
                stats = stats,
                tablaRows = tablaRows,
                rangos = rangos
            )
        }

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
                    val chooser = android.content.Intent.createChooser(intent, "Compartir Reporte de Ganado").apply {
                        addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    context.startActivity(chooser)
                } catch (e: Exception) {
                    android.util.Log.e("ReportesGanado", "Error al compartir archivo: ${e.message}", e)
                    android.widget.Toast.makeText(context, "Error al compartir: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }

        val paginasPdfEstimadas = if (tablaRows.isEmpty()) 2 else if (tablaRows.size <= 18) 2 else (1 + kotlin.math.ceil((tablaRows.size - 18) / 22.0).toInt() + 1)

        ExportarGanadoDialog(
            totalPaginasPdf = paginasPdfEstimadas,
            totalRegistros = tablaRows.size,
            onDismiss = { exportDialogVisible = false },
            onExportPdf = { guardarSAF ->
                exportDialogVisible = false
                scope.launch {
                    val reportDir = File(context.cacheDir, "reportes").apply { if (!exists()) mkdirs() }
                    val fileName = "$filenameBase.pdf"
                    val file = File(reportDir, fileName)
                    val success = withContext(Dispatchers.IO) {
                        PdfGanadoGenerator.generatePdf(context, file, exportData)
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
                        ExcelGanadoGenerator.generateExcel(context, file, exportData)
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

    if (mostrarModalFiltros) {
        GanadoFilterDialog(
            currentFiltroFechas = filtroFechas,
            currentTipoArete = tipoAreteFiltro,
            currentColores = coloresFiltro,
            currentEstados = estadosFiltro,
            currentRangosPesoIds = rangosPesoFiltroIds,
            rangosDisponibles = rangos,
            availableYears = availableYears,
            onDismiss = { mostrarModalFiltros = false },
            onApply = { newFechas, newTipoArete, newColores, newEstados, newRangosIds ->
                filtroFechas = newFechas
                tipoAreteFiltro = newTipoArete
                coloresFiltro = newColores
                estadosFiltro = newEstados
                rangosPesoFiltroIds = newRangosIds
                mostrarModalFiltros = false
            }
        )
    }

    fotoSeleccionadaArete?.let { arete ->
        val url = fotoSeleccionadaUrl
        if (!url.isNullOrBlank()) {
            AlertDialog(
                onDismissRequest = { fotoSeleccionadaArete = null },
                title = {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Fotografía del Animal", fontWeight = FontWeight.Bold)
                        IconButton(onClick = { fotoSeleccionadaArete = null }) {
                            Icon(Icons.Default.Close, contentDescription = "Cerrar")
                        }
                    }
                },
                text = {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Número de Arete: $arete", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.height(12.dp))
                        Card(
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(260.dp)
                        ) {
                            AsyncImage(
                                model = url,
                                contentDescription = "Fotografía animal $arete",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = { fotoSeleccionadaArete = null },
                        colors = ButtonDefaults.buttonColors(containerColor = SanMartinOrange)
                    ) {
                        Text("Cerrar")
                    }
                }
            )
        } else {
            InfoDialog(
                title = "Sin Fotografía",
                message = "El animal con arete $arete no cuenta con una fotografía registrada en el sistema.",
                onDismiss = { fotoSeleccionadaArete = null }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ReportesGanadoContent(
    stats: ReporteGanadoStats,
    tablaRows: List<ReporteGanadoRow>,
    hayFiltrosActivos: Boolean,
    onOpenFiltros: () -> Unit,
    onOpenExport: () -> Unit,
    onResetFiltros: () -> Unit,
    onShowFoto: (String, String?) -> Unit,
    onBack: () -> Unit
) {
    val totalPesoEntrada = remember(tablaRows) { tablaRows.sumOf { it.pesoEntrada } }
    val totalPesoSalida = remember(tablaRows) { tablaRows.mapNotNull { it.pesoSalida }.sum() }
    val totalPrecioCompra = remember(tablaRows) { tablaRows.sumOf { it.precioCompra } }
    val totalPrecioVenta = remember(tablaRows) { tablaRows.mapNotNull { it.precioVenta }.sum() }

    val locale = remember { Locale.getDefault() }
    val totalPesoEntradaTxt = remember(totalPesoEntrada, locale) { "${String.format(locale, "%,.0f", totalPesoEntrada)} kg" }
    val totalPesoSalidaTxt = remember(tablaRows, totalPesoSalida, locale) {
        if (tablaRows.any { it.pesoSalida != null }) "${String.format(locale, "%,.0f", totalPesoSalida)} kg" else "N/A"
    }
    val totalPrecioCompraTxt = remember(totalPrecioCompra, locale) { "$${String.format(locale, "%,.2f", totalPrecioCompra)}" }
    val totalPrecioVentaTxt = remember(tablaRows, totalPrecioVenta, locale) {
        if (tablaRows.any { it.precioVenta != null }) "$${String.format(locale, "%,.2f", totalPrecioVenta)}" else "N/A"
    }

    val pagerState = rememberPagerState(pageCount = { 2 })
    val scope = rememberCoroutineScope()

    Scaffold(
        topBar = {
            BackTopBar(
                title = "Reportes de Ganado",
                onBack = onBack,
                actions = {
                    IconButton(onClick = onOpenExport) {
                        Icon(Icons.Default.Share, contentDescription = "Exportar Reporte", tint = MaterialTheme.colorScheme.onBackground)
                    }
                    IconButton(onClick = onOpenFiltros) {
                        Box(contentAlignment = Alignment.TopEnd) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = "Filtros",
                                tint = if (hayFiltrosActivos) SanMartinOrange else MaterialTheme.colorScheme.onBackground
                            )
                            if (hayFiltrosActivos) {
                                Box(
                                    modifier = Modifier
                                        .size(9.dp)
                                        .background(SanMartinOrange, CircleShape)
                                )
                            }
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
        ) {
            if (hayFiltrosActivos) {
                Spacer(Modifier.height(8.dp))
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.FilterAlt, contentDescription = null, tint = SanMartinOrange, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Filtros activos aplicados", fontSize = 12.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                        }
                        TextButton(
                            onClick = onResetFiltros,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                        ) {
                            Text("Restablecer", fontSize = 11.sp, color = SanMartinOrange, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            Spacer(Modifier.height(8.dp))

            TabRow(
                selectedTabIndex = pagerState.currentPage,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary,
                divider = { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)) }
            ) {
                Tab(
                    selected = pagerState.currentPage == 0,
                    onClick = { scope.launch { pagerState.animateScrollToPage(0) } },
                    text = { Text("Tabla de Registros", fontWeight = FontWeight.Bold, fontSize = 14.sp) },
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

            Spacer(Modifier.height(8.dp))

            HorizontalPager(
                state = pagerState,
                modifier = Modifier.weight(1f)
            ) { page ->
                if (stats.totalAnimales == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                            shape = RoundedCornerShape(16.dp),
                            elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder), contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No hay registros que coincidan", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "Intenta cambiando o restableciendo los filtros seleccionados para ver resultados.",
                                    style = MaterialTheme.typography.bodySmall,
                                    textAlign = TextAlign.Center,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Spacer(Modifier.height(16.dp))
                                Button(
                                    onClick = onResetFiltros,
                                    colors = ButtonDefaults.buttonColors(containerColor = SanMartinOrange)
                                ) {
                                    Text("Restablecer Filtros")
                                }
                            }
                        }
                    }
                } else if (page == 0) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        TablaGanadoReporte(
                            modifier = Modifier.fillMaxSize(),
                            tablaRows = tablaRows,
                            totalPesoEntradaTxt = totalPesoEntradaTxt,
                            totalPesoSalidaTxt = totalPesoSalidaTxt,
                            totalPrecioCompraTxt = totalPrecioCompraTxt,
                            totalPrecioVentaTxt = totalPrecioVentaTxt,
                            onShowFoto = onShowFoto
                        )
                    }
                } else {
                    val isTabActive = pagerState.currentPage == 1

                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .verticalScroll(rememberScrollState())
                            .padding(bottom = 24.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val headerAlpha by animateFloatAsState(
                            targetValue = if (isTabActive) 1f else 0f,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                            label = "headerAlpha"
                        )
                        val headerTranslationY by animateFloatAsState(
                            targetValue = if (isTabActive) 0f else 16f,
                            animationSpec = tween(durationMillis = 280, easing = FastOutSlowInEasing),
                            label = "headerTranslationY"
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .graphicsLayer {
                                    alpha = headerAlpha
                                    translationY = headerTranslationY * density
                                }
                                .padding(horizontal = 16.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "Resumen del inventario",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Text(
                                text = "Visualiza el estado, peso, distribución y composición analítica del hato.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        IndicadoresPrincipalesGrid(stats = stats, isTabActive = isTabActive)
                        GraficoDistribucionRangoPeso(stats = stats, isTabActive = isTabActive)

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Box(modifier = Modifier.weight(1f)) {
                                GraficoDistribucionEstadoDonut(stats = stats, isTabActive = isTabActive)
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                GraficoDistribucionSexoBars(stats = stats, isTabActive = isTabActive)
                            }
                        }

                        ResumenTipoAreteSection(stats = stats, isTabActive = isTabActive)
                    }
                }
            }
        }
    }
}

// Diálogo de Elección de Exportación (PDF o Excel)
@Composable
private fun ExportarGanadoDialog(
    totalPaginasPdf: Int,
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
                                text = "Ganado",
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
                                    text = "$totalRegistros registros · $totalPaginasPdf ${if (totalPaginasPdf == 1) "página" else "páginas"}",
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
                                    text = "4 hojas (Resumen, Registros, Análisis, Catálogos)",
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

// ----------------------------------------------------------------------------------
// SECCIÓN 1: KPIS PRINCIPALES
// ----------------------------------------------------------------------------------
@Composable
private fun IndicadoresPrincipalesGrid(stats: ReporteGanadoStats, isTabActive: Boolean = true) {
    val locale = remember { Locale.getDefault() }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = "Total Animales",
                value = "${stats.totalAnimales}",
                subtitle = "cabezas filtradas",
                icon = ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder),
                accentColor = MaterialTheme.colorScheme.primary,
                index = 0,
                isTabActive = isTabActive,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Peso Total",
                value = "${String.format(locale, "%,.0f", stats.pesoTotal)} kg",
                subtitle = "acumulado entrada",
                icon = Icons.Default.Scale,
                accentColor = MaterialTheme.colorScheme.primary,
                index = 1,
                isTabActive = isTabActive,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = "Peso Promedio",
                value = "${String.format(locale, "%,.1f", stats.pesoPromedio)} kg",
                subtitle = if (stats.sinPesoRegistradoCount > 0) "promedio (${stats.sinPesoRegistradoCount} sin peso)" else "promedio / cabeza",
                icon = Icons.Default.Analytics,
                accentColor = MaterialTheme.colorScheme.primary,
                index = 2,
                isTabActive = isTabActive,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Activos",
                value = "${stats.totalActivos}",
                subtitle = "${String.format(locale, "%.1f", stats.pctActivos)}% del total",
                icon = Icons.Default.CheckCircle,
                accentColor = Color(0xFF34D399),
                index = 3,
                isTabActive = isTabActive,
                modifier = Modifier.weight(1f)
            )
        }

        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            KpiCard(
                title = "Vendidos",
                value = "${stats.totalVendidos}",
                subtitle = "${String.format(locale, "%.1f", stats.pctVendidos)}% del total",
                icon = Icons.Default.AttachMoney,
                accentColor = MaterialTheme.colorScheme.primary,
                isSecondary = true,
                index = 4,
                isTabActive = isTabActive,
                modifier = Modifier.weight(1f)
            )
            KpiCard(
                title = "Muertes / Merma",
                value = "${stats.totalMuertos}",
                subtitle = "${String.format(locale, "%.1f", stats.pctMuertos)}% del total",
                icon = Icons.Default.Warning,
                accentColor = Color(0xFFF87171),
                isSecondary = true,
                index = 5,
                isTabActive = isTabActive,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun KpiCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    isSecondary: Boolean = false,
    index: Int = 0,
    isTabActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val alphaAnim by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = index * 50, easing = FastOutSlowInEasing),
        label = "kpiAlpha_$index"
    )
    val translationYAnim by animateFloatAsState(
        targetValue = if (isTabActive) 0f else 16f,
        animationSpec = tween(durationMillis = 300, delayMillis = index * 50, easing = FastOutSlowInEasing),
        label = "kpiTranslationY_$index"
    )

    Card(
        modifier = modifier.graphicsLayer {
            alpha = alphaAnim
            translationY = translationYAnim * density
        },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        shape = RoundedCornerShape(14.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .background(accentColor.copy(alpha = 0.15f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(15.dp))
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(
                text = value,
                fontSize = 17.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = subtitle,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

// ----------------------------------------------------------------------------------
// SECCIÓN 2: GRÁFICO BARRAS HORIZONTALES (DISTRIBUCIÓN RANGO DE PESO)
// ----------------------------------------------------------------------------------
@Composable
private fun GraficoDistribucionRangoPeso(stats: ReporteGanadoStats, isTabActive: Boolean = true) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "rangoCardAlpha"
    )
    val cardTranslationY by animateFloatAsState(
        targetValue = if (isTabActive) 0f else 16f,
        animationSpec = tween(durationMillis = 300, delayMillis = 150, easing = FastOutSlowInEasing),
        label = "rangoCardTranslationY"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardTranslationY * density
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    text = "Distribución por Rango de Peso",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Spacer(Modifier.height(14.dp))

            if (stats.distribucionRangos.isEmpty()) {
                Text("No hay rangos de peso configurados", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else {
                stats.distribucionRangos.forEachIndexed { idx, item ->
                    key(item.idRangoPeso ?: item.nombreRango) {
                        RangoBarItem(item = item, index = idx, isTabActive = isTabActive)
                    }
                }
            }
        }
    }
}

@Composable
private fun RangoBarItem(item: RangoStat, index: Int = 0, isTabActive: Boolean = true) {
    val locale = remember { Locale.getDefault() }
    val targetFraction = (item.porcentaje / 100.0).toFloat().coerceIn(0f, 1f)

    val animProgress by animateFloatAsState(
        targetValue = if (isTabActive) targetFraction else 0f,
        animationSpec = tween(
            durationMillis = 650,
            delayMillis = index * 70 + 200,
            easing = FastOutSlowInEasing
        ),
        label = "barAnim"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(
            durationMillis = 400,
            delayMillis = index * 70 + 250,
            easing = LinearOutSlowInEasing
        ),
        label = "rangoTextAlpha"
    )

    Column(modifier = Modifier.padding(vertical = 4.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = textAlpha },
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = item.nombreRango,
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (item.idRangoPeso == null) Color(0xFFF87171) else MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "${item.cantidad} cabezas (${String.format(locale, "%.1f", item.porcentaje)}%)",
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fraction = animProgress.coerceIn(0f, 1f))
                    .clip(CircleShape)
                    .background(
                        Brush.horizontalGradient(
                            if (item.idRangoPeso == null)
                                listOf(Color(0xFFE53935), Color(0xFFEF5350))
                            else
                                listOf(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer)
                        )
                    )
            )
        }
    }
}

// ----------------------------------------------------------------------------------
// SECCIÓN 3: GRÁFICO DE DONA (ESTADO)
// ----------------------------------------------------------------------------------
@Composable
private fun GraficoDistribucionEstadoDonut(stats: ReporteGanadoStats, isTabActive: Boolean = true) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 220, easing = FastOutSlowInEasing),
        label = "donutCardAlpha"
    )
    val cardTranslationY by animateFloatAsState(
        targetValue = if (isTabActive) 0f else 16f,
        animationSpec = tween(durationMillis = 300, delayMillis = 220, easing = FastOutSlowInEasing),
        label = "donutCardTranslationY"
    )

    val ringProgress by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 800, delayMillis = 250, easing = FastOutSlowInEasing),
        label = "donutAnim"
    )

    val centerAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 550, easing = LinearOutSlowInEasing),
        label = "donutCenterAlpha"
    )

    val legendAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = 450, easing = LinearOutSlowInEasing),
        label = "donutLegendAlpha"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardTranslationY * density
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.PieChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Estado", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))

            Box(
                modifier = Modifier.size(110.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    var startAngle = -90f
                    val total = stats.totalAnimales.toFloat()

                    if (total > 0f) {
                        stats.distribucionEstado.forEach { item ->
                            val sweepAngle = (item.cantidad.toFloat() / total) * 360f * ringProgress
                            if (sweepAngle > 0f) {
                                drawArc(
                                    color = item.color,
                                    startAngle = startAngle,
                                    sweepAngle = sweepAngle,
                                    useCenter = false,
                                    style = Stroke(width = 18.dp.toPx(), cap = StrokeCap.Butt)
                                )
                                startAngle += sweepAngle
                            }
                        }
                    } else {
                        drawArc(
                            color = Color(0xFFE2E8ED),
                            startAngle = 0f,
                            sweepAngle = 360f,
                            useCenter = false,
                            style = Stroke(width = 18.dp.toPx())
                        )
                    }
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.graphicsLayer { alpha = centerAlpha }
                ) {
                    Text(text = "${stats.totalAnimales}", fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(text = "Total", fontSize = 9.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }

            Spacer(Modifier.height(10.dp))

            val locale = remember { Locale.getDefault() }
            Column(modifier = Modifier.graphicsLayer { alpha = legendAlpha }) {
                stats.distribucionEstado.forEach { item ->
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(modifier = Modifier.size(8.dp).background(item.color, CircleShape))
                            Spacer(Modifier.width(6.dp))
                            Text(item.label, fontSize = 10.sp, color = MaterialTheme.colorScheme.onSurface)
                        }
                        Text("${item.cantidad} (${String.format(locale, "%.0f", item.porcentaje)}%)", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }
        }
    }
}

// ----------------------------------------------------------------------------------
// SECCIÓN 4: GRÁFICO BARRAS VERTICALES (SEXO)
// ----------------------------------------------------------------------------------
@Composable
private fun GraficoDistribucionSexoBars(stats: ReporteGanadoStats, isTabActive: Boolean = true) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 220, easing = FastOutSlowInEasing),
        label = "sexoCardAlpha"
    )
    val cardTranslationY by animateFloatAsState(
        targetValue = if (isTabActive) 0f else 16f,
        animationSpec = tween(durationMillis = 300, delayMillis = 220, easing = FastOutSlowInEasing),
        label = "sexoCardTranslationY"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardTranslationY * density
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Sexo", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
            Spacer(Modifier.height(12.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(110.dp),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.Bottom
            ) {
                stats.distribucionSexo.forEachIndexed { idx, item ->
                    key(item.sexo) {
                        SexoBarItem(item = item, index = idx, isTabActive = isTabActive)
                    }
                }
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun SexoBarItem(item: SexoStat, index: Int = 0, isTabActive: Boolean = true) {
    val targetFraction = if (item.cantidad > 0) (item.porcentaje / 100.0).toFloat().coerceIn(0f, 1f) else 0f

    val barProgress by animateFloatAsState(
        targetValue = if (isTabActive) targetFraction else 0f,
        animationSpec = tween(durationMillis = 600, delayMillis = index * 100 + 250, easing = FastOutSlowInEasing),
        label = "vBarAnim"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 400, delayMillis = index * 100 + 350, easing = LinearOutSlowInEasing),
        label = "sexoTextAlpha"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Bottom,
        modifier = Modifier.fillMaxHeight()
    ) {
        Text(
            text = "${item.cantidad}",
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.graphicsLayer { alpha = textAlpha }
        )
        Spacer(Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .width(32.dp)
                .height(if (item.cantidad == 0) 0.dp else (80 * barProgress.coerceIn(0f, 1f)).dp)
                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                .background(item.color)
        )
        Spacer(Modifier.height(4.dp))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.graphicsLayer { alpha = textAlpha }
        ) {
            Icon(
                if (item.sexo == Sexo.MACHO) Icons.Default.Male else Icons.Default.Female,
                contentDescription = null,
                tint = item.color,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(item.label, fontSize = 10.sp, fontWeight = FontWeight.Medium, color = MaterialTheme.colorScheme.onSurface)
        }
    }
}

// ----------------------------------------------------------------------------------
// SECCIÓN 5: RESUMEN TIPO DE ARETE
// ----------------------------------------------------------------------------------
@Composable
private fun ResumenTipoAreteSection(stats: ReporteGanadoStats, isTabActive: Boolean = true) {
    val cardAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "tipoAreteCardAlpha"
    )
    val cardTranslationY by animateFloatAsState(
        targetValue = if (isTabActive) 0f else 16f,
        animationSpec = tween(durationMillis = 300, delayMillis = 300, easing = FastOutSlowInEasing),
        label = "tipoAreteCardTranslationY"
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .graphicsLayer {
                alpha = cardAlpha
                translationY = cardTranslationY * density
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        shape = RoundedCornerShape(16.dp),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.5.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "Resumen por Tipo de Arete",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(12.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                stats.distribucionTipoArete.forEachIndexed { idx, item ->
                    TipoAreteBarItem(
                        item = item,
                        index = idx,
                        isTabActive = isTabActive,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TipoAreteBarItem(
    item: TipoAreteStat,
    index: Int = 0,
    isTabActive: Boolean = true,
    modifier: Modifier = Modifier
) {
    val locale = remember { Locale.getDefault() }
    val targetFraction = (item.porcentaje / 100.0).toFloat().coerceIn(0f, 1f)

    val itemAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 300, delayMillis = index * 80 + 320, easing = FastOutSlowInEasing),
        label = "tipoAreteItemAlpha_$index"
    )
    val itemTranslationY by animateFloatAsState(
        targetValue = if (isTabActive) 0f else 12f,
        animationSpec = tween(durationMillis = 300, delayMillis = index * 80 + 320, easing = FastOutSlowInEasing),
        label = "tipoAreteItemTranslationY_$index"
    )

    val barProgress by animateFloatAsState(
        targetValue = if (isTabActive) targetFraction else 0f,
        animationSpec = tween(durationMillis = 500, delayMillis = index * 80 + 400, easing = FastOutSlowInEasing),
        label = "tipoAreteBarAnim_$index"
    )

    val textAlpha by animateFloatAsState(
        targetValue = if (isTabActive) 1f else 0f,
        animationSpec = tween(durationMillis = 350, delayMillis = index * 80 + 380, easing = LinearOutSlowInEasing),
        label = "tipoAreteTextAlpha_$index"
    )

    Surface(
        modifier = modifier.graphicsLayer {
            alpha = itemAlpha
            translationY = itemTranslationY * density
        },
        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.2f),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Text(
                item.label,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "${item.cantidad} cabezas",
                fontSize = 16.sp,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
            Spacer(Modifier.height(2.dp))
            Text(
                "${String.format(locale, "%.1f", item.porcentaje)}% del total",
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.graphicsLayer { alpha = textAlpha }
            )
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f))
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxHeight()
                        .fillMaxWidth(fraction = barProgress.coerceIn(0f, 1f))
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}


// ----------------------------------------------------------------------------------
// DIÁLOGO DE FILTROS DE GANADO (5 CRITERIOS REQUERIDOS)
// ----------------------------------------------------------------------------------
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun GanadoFilterDialog(
    currentFiltroFechas: FiltroFechas,
    currentTipoArete: String,
    currentColores: Set<String>,
    currentEstados: Set<EstadoInventario>,
    currentRangosPesoIds: Set<String>,
    rangosDisponibles: List<RangoPeso>,
    availableYears: List<Int>,
    onDismiss: () -> Unit,
    onApply: (FiltroFechas, String, Set<String>, Set<EstadoInventario>, Set<String>) -> Unit
) {
    // 1. Fecha
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

    // 2. Tipo de Arete
    var tipoArete by remember { mutableStateOf(currentTipoArete) }

    // 3. Color
    var colores by remember { mutableStateOf(currentColores) }
    val coloresDisponibles = listOf("Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo")

    // 4. Estado
    var estados by remember { mutableStateOf(currentEstados) }
    val estadosDisponibles = listOf(
        EstadoInventario.ACTIVO to "Activo",
        EstadoInventario.VENDIDO to "Vendido",
        EstadoInventario.MUERTO to "Muerte"
    )

    // 5. Rango de Peso (Multiselección)
    var rangosPesoIds by remember { mutableStateOf(currentRangosPesoIds) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.FilterAlt, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Filtros de Ganado", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.verticalScroll(rememberScrollState())
            ) {
                // 1. FILTRO POR FECHA
                Text("1. Rango de Fechas", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)

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
                            Spacer(Modifier.height(6.dp))
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

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 2. FILTRO POR TIPO DE ARETE
                Text("2. Tipo de Arete", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    FilterChip(
                        selected = tipoArete == "AMBOS",
                        onClick = { tipoArete = "AMBOS" },
                        label = { Text("Ambos", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = tipoArete == "SADER",
                        onClick = {
                            tipoArete = when (tipoArete) {
                                "AMBOS" -> "SADER"
                                "SADER" -> "AMBOS"
                                "OTRO" -> "AMBOS"
                                else -> "SADER"
                            }
                        },
                        label = { Text("SADER", fontSize = 12.sp) }
                    )
                    FilterChip(
                        selected = tipoArete == "OTRO",
                        onClick = {
                            tipoArete = when (tipoArete) {
                                "AMBOS" -> "OTRO"
                                "OTRO" -> "AMBOS"
                                "SADER" -> "AMBOS"
                                else -> "OTRO"
                            }
                        },
                        label = { Text("Otro (Genérico)", fontSize = 12.sp) }
                    )
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 3. FILTRO POR COLOR
                Text("3. Color del Animal", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FilterChip(
                    selected = colores.isEmpty(),
                    onClick = { colores = emptySet() },
                    label = { Text("Todos los colores", fontSize = 12.sp) }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    coloresDisponibles.forEach { colorOpt ->
                        FilterChip(
                            selected = colores.contains(colorOpt),
                            onClick = {
                                val set = colores.toMutableSet()
                                if (set.contains(colorOpt)) {
                                    set.remove(colorOpt)
                                } else {
                                    set.add(colorOpt)
                                }
                                if (set.size == coloresDisponibles.size || set.isEmpty()) {
                                    colores = emptySet()
                                } else {
                                    colores = set
                                }
                            },
                            label = { Text(colorOpt, fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 4. FILTRO POR ESTADO DE INVENTARIO
                Text("4. Estado del Inventario", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FilterChip(
                    selected = estados.isEmpty(),
                    onClick = { estados = emptySet() },
                    label = { Text("Todos los estados", fontSize = 12.sp) }
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    estadosDisponibles.forEach { (estKey, estLabel) ->
                        FilterChip(
                            selected = estados.contains(estKey),
                            onClick = {
                                val set = estados.toMutableSet()
                                if (set.contains(estKey)) {
                                    set.remove(estKey)
                                } else {
                                    set.add(estKey)
                                }
                                if (set.size == estadosDisponibles.size || set.isEmpty()) {
                                    estados = emptySet()
                                } else {
                                    estados = set
                                }
                            },
                            label = { Text(estLabel, fontSize = 12.sp) }
                        )
                    }
                }

                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))

                // 5. FILTRO POR RANGOS DE PESO
                Text("5. Rango de Peso", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                FilterChip(
                    selected = rangosPesoIds.isEmpty(),
                    onClick = { rangosPesoIds = emptySet() },
                    label = { Text("Todos los rangos", fontSize = 12.sp) }
                )
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    rangosDisponibles.forEach { rango ->
                        FilterChip(
                            selected = rangosPesoIds.contains(rango.idRangoPeso),
                            onClick = {
                                val set = rangosPesoIds.toMutableSet()
                                val id = rango.idRangoPeso
                                if (set.contains(id)) {
                                    set.remove(id)
                                } else {
                                    set.add(id)
                                }
                                if (rangosDisponibles.isNotEmpty() && set.size == rangosDisponibles.size || set.isEmpty()) {
                                    rangosPesoIds = emptySet()
                                } else {
                                    rangosPesoIds = set
                                }
                            },
                            label = { Text("${rango.nombreRango} (${rango.limiteInferior.toInt()}-${rango.limiteSuperior.toInt()} kg)", fontSize = 11.sp) }
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
                    onApply(filtroFec, tipoArete, colores, estados, rangosPesoIds)
                },
                colors = ButtonDefaults.buttonColors(containerColor = SanMartinOrange)
            ) {
                Text("Aplicar Filtros")
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    onApply(
                        FiltroFechas.Ultimo(Periodo.MENSUAL),
                        "AMBOS",
                        emptySet(),
                        emptySet(),
                        emptySet()
                    )
                }
            ) {
                Text("Restablecer")
            }
        }
    )
}

// ----------------------------------------------------------------------------------
// TABLA DETALLADA DE ANIMALES DE ALTO CONTRASTE
// ----------------------------------------------------------------------------------
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TablaGanadoReporte(
    modifier: Modifier = Modifier,
    tablaRows: List<ReporteGanadoRow>,
    totalPesoEntradaTxt: String,
    totalPesoSalidaTxt: String,
    totalPrecioCompraTxt: String,
    totalPrecioVentaTxt: String,
    onShowFoto: (String, String?) -> Unit
) {
    val horizontalScrollState = rememberScrollState()

    val widthArete = 115.dp
    val widthTipo = 80.dp
    val widthFechaEntrada = 85.dp
    val widthColor = 75.dp
    val widthSexo = 65.dp
    val widthEstado = 80.dp
    val widthPesoEntrada = 85.dp
    val widthPesoSalida = 85.dp
    val widthRangoPeso = 85.dp
    val widthPrecioCompra = 95.dp
    val widthPrecioVenta = 95.dp
    val widthProveedor = 110.dp
    val widthComprador = 110.dp
    val widthFechaSalida = 85.dp
    val widthFoto = 80.dp

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
    ) {
        stickyHeader {
            Surface(
                shadowElevation = 6.dp,
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrozenHeaderCell(text = "Número Arete", width = widthArete)
                    SombraDivisoraFija()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HeaderCell(text = "Tipo Arete", width = widthTipo)
                        HeaderCell(text = "Fecha Ent.", width = widthFechaEntrada)
                        HeaderCell(text = "Color", width = widthColor)
                        HeaderCell(text = "Sexo", width = widthSexo)
                        HeaderCell(text = "Estado", width = widthEstado)
                        HeaderCell(text = "Peso Ent.", width = widthPesoEntrada)
                        HeaderCell(text = "Peso Sal.", width = widthPesoSalida)
                        HeaderCell(text = "Rango Peso", width = widthRangoPeso)
                        HeaderCell(text = "P. Compra", width = widthPrecioCompra)
                        HeaderCell(text = "P. Venta", width = widthPrecioVenta)
                        HeaderCell(text = "Proveedor", width = widthProveedor)
                        HeaderCell(text = "Comprador", width = widthComprador)
                        HeaderCell(text = "Fecha Sal.", width = widthFechaSalida)
                        HeaderCell(text = "Foto", width = widthFoto)
                    }
                }
            }
        }

        if (tablaRows.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "No hay registros de ganado para los filtros seleccionados.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            itemsIndexed(tablaRows) { index, row ->
                val rowBg = if (index % 2 == 0) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.7f)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(rowBg),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FrozenDataCell(text = row.numeroArete, width = widthArete, bg = rowBg, isBold = true)
                    SombraDivisoraFija()
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .horizontalScroll(horizontalScrollState),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        DataCell(text = row.tipoArete, width = widthTipo)
                        DataCell(text = row.fechaEntradaTexto, width = widthFechaEntrada)
                        DataCell(text = row.color, width = widthColor)
                        DataCell(text = row.sexo, width = widthSexo)
                        EstadoCell(estado = row.estado, width = widthEstado)
                        DataCell(text = row.pesoEntradaTexto, width = widthPesoEntrada)
                        DataCell(text = row.pesoSalidaTexto, width = widthPesoSalida, isMuted = row.pesoSalidaTexto == "N/A")
                        DataCell(text = row.rangoPeso, width = widthRangoPeso)
                        DataCell(text = row.precioCompraTexto, width = widthPrecioCompra)
                        DataCell(text = row.precioVentaTexto, width = widthPrecioVenta, isMuted = row.precioVentaTexto == "N/A")
                        DataCell(text = row.proveedor, width = widthProveedor)
                        DataCell(text = row.comprador, width = widthComprador, isMuted = row.comprador == "N/A")
                        DataCell(text = row.fechaSalidaTexto, width = widthFechaSalida, isMuted = row.fechaSalidaTexto == "N/A")
                        FotoBotonCell(
                            width = widthFoto,
                            hasFoto = !row.fotografiaLocal.isNullOrBlank(),
                            onClick = { onShowFoto(row.numeroArete, row.fotografiaLocal) }
                        )
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.8.dp)
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shadowElevation = 8.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        FrozenTotalCell(text = "TOTALES", width = widthArete)
                        SombraDivisoraFija()
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(horizontalScrollState),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TotalCell(text = "", width = widthTipo)
                            TotalCell(text = "", width = widthFechaEntrada)
                            TotalCell(text = "", width = widthColor)
                            TotalCell(text = "", width = widthSexo)
                            TotalCell(text = "", width = widthEstado)
                            TotalCell(text = totalPesoEntradaTxt, width = widthPesoEntrada)
                            TotalCell(text = totalPesoSalidaTxt, width = widthPesoSalida)
                            TotalCell(text = "", width = widthRangoPeso)
                            TotalCell(text = totalPrecioCompraTxt, width = widthPrecioCompra)
                            TotalCell(text = totalPrecioVentaTxt, width = widthPrecioVenta)
                            TotalCell(text = "", width = widthProveedor)
                            TotalCell(text = "", width = widthComprador)
                            TotalCell(text = "", width = widthFechaSalida)
                            TotalCell(text = "", width = widthFoto)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SombraDivisoraFija() {
    Box(
        modifier = Modifier
            .fillMaxHeight()
            .width(5.dp)
            .background(
                Brush.horizontalGradient(
                    colors = listOf(
                        Color.Black.copy(alpha = 0.22f),
                        Color.Transparent
                    )
                )
            )
    )
}

@Composable
private fun FrozenHeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun HeaderCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun FrozenDataCell(text: String, width: Dp, bg: Color, isBold: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(bg)
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun DataCell(text: String, width: Dp, isMuted: Boolean = false) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = if (isMuted) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            fontWeight = if (isMuted) FontWeight.Normal else FontWeight.Medium,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun EstadoCell(estado: String, width: Dp) {
    val (bgColor, textColor) = when (estado) {
        "Activo" -> Color(0xFFDCFCE7) to Color(0xFF166534)
        "Vendido" -> Color(0xFFDBEAFE) to Color(0xFF1E40AF)
        else -> Color(0xFFFEE2E2) to Color(0xFF991B1B)
    }

    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 4.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Surface(
            color = bgColor,
            shape = RoundedCornerShape(10.dp)
        ) {
            Text(
                text = estado,
                color = textColor,
                fontWeight = FontWeight.Bold,
                fontSize = 10.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun FotoBotonCell(width: Dp, hasFoto: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 2.dp),
        contentAlignment = Alignment.Center
    ) {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier.height(28.dp),
            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = if (hasFoto) SanMartinOrange else MaterialTheme.colorScheme.onSurfaceVariant
            ),
            border = androidx.compose.foundation.BorderStroke(
                1.dp,
                if (hasFoto) SanMartinOrange else MaterialTheme.colorScheme.outline
            )
        ) {
            Icon(
                Icons.Default.PhotoCamera,
                contentDescription = null,
                modifier = Modifier.size(12.dp)
            )
            Spacer(Modifier.width(2.dp))
            Text(
                text = if (hasFoto) "Foto" else "Sin",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
private fun FrozenTotalCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .background(MaterialTheme.colorScheme.primaryContainer)
            .padding(horizontal = 8.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 12.sp
        )
    }
}

@Composable
private fun TotalCell(text: String, width: Dp) {
    Box(
        modifier = Modifier
            .width(width)
            .fillMaxHeight()
            .padding(horizontal = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = text,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            fontWeight = FontWeight.Bold,
            fontSize = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Preview(showBackground = true)
@Composable
fun ReportesGanadoPreview() {
    val sampleStats = ReporteGanadoDataCalculator.calcular(emptyList(), emptyList())
    MaterialTheme {
        ReportesGanadoContent(
            stats = sampleStats,
            tablaRows = listOf(
                ReporteGanadoRow(
                    numeroArete = "484123456789012",
                    tipoArete = "SADER",
                    fechaEntradaTexto = "12/10/2026",
                    color = "Pinto",
                    sexo = "Macho",
                    estado = "Activo",
                    pesoEntrada = 240.0,
                    pesoEntradaTexto = "240 kg",
                    pesoSalida = null,
                    pesoSalidaTexto = "N/A",
                    rangoPeso = "Becerro",
                    precioCompra = 12500.0,
                    precioCompraTexto = "$12,500.00",
                    precioVenta = null,
                    precioVentaTexto = "N/A",
                    proveedor = "Rancho San Martín",
                    comprador = "N/A",
                    fechaSalidaTexto = "N/A",
                    fotografiaLocal = null
                )
            ),
            hayFiltrosActivos = false,
            onOpenFiltros = {},
            onOpenExport = {},
            onResetFiltros = {},
            onShowFoto = { _, _ -> },
            onBack = {}
        )
    }
}
