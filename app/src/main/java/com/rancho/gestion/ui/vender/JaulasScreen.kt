package com.rancho.gestion.ui.vender

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material.icons.filled.Visibility
import com.rancho.gestion.ui.theme.SanMartinOrange
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.*
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.domain.JaulaCapacidad
import com.rancho.gestion.ui.components.AreteTag
import com.rancho.gestion.ui.components.AutocompleteAreteTextField
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.BarcodeScannerScreen
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.InfoDialog
import com.rancho.gestion.ui.components.LabeledProgressBar
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.components.SimpleFormDialog
import com.rancho.gestion.ui.components.ThousandsSeparatorVisualTransformation
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoSuccess
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.util.ImageOptimizer
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun JaulasScreen(container: AppContainer, onBack: () -> Unit) {
    val snackbarHostState = remember { SnackbarHostState() }
    Scaffold(
        topBar = { BackTopBar(title = "Jaulas", onBack = onBack) },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Box(Modifier.padding(padding)) {
            JaulasContent(container, snackbarHostState = snackbarHostState)
        }
    }
}

/** Stateful version for internal use and container screens. */
@Composable
fun JaulasContent(container: AppContainer, isActive: Boolean = true, snackbarHostState: SnackbarHostState? = null) {
    val jaulas by container.ventaRepository.getJaulasAbiertas().collectAsState(initial = emptyList())
    val rangos by container.configuracionRepository.rangos().collectAsState(initial = emptyList())
    
    val usuarioActual by SessionManager.usuarioActual.collectAsState()
    val isAdmin = usuarioActual?.rol == com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
    
    val scope = rememberCoroutineScope()

    var mostrarNuevaJaula by remember { mutableStateOf(false) }
    var mostrarAlertaLimite by remember { mutableStateOf(false) }
    var jaulaAEliminar by remember { mutableStateOf<Venta?>(null) }
    var jaulaParaAgregar by remember { mutableStateOf<Venta?>(null) }
    var jaulaParaVerAnimales by remember { mutableStateOf<Venta?>(null) }
    var jaulaParaVender by remember { mutableStateOf<Venta?>(null) }

    // Auto-close dialogs if page becomes inactive
    LaunchedEffect(isActive) {
        if (!isActive) {
            jaulaParaAgregar = null
            mostrarNuevaJaula = false
            jaulaAEliminar = null
            jaulaParaVerAnimales = null
            jaulaParaVender = null
        }
    }

    JaulasStatelessContent(
        jaulas = jaulas,
        isAdmin = isAdmin,
        onNuevaJaulaClick = {
            if (jaulas.size >= 20) {
                mostrarAlertaLimite = true
            } else {
                mostrarNuevaJaula = true
            }
        },
        onEliminarJaula = { jaulaAEliminar = it },
        onAgregarAnimales = { jaulaParaAgregar = it },
        onVerAnimales = { jaulaParaVerAnimales = it },
        onVenderJaula = { jaulaParaVender = it }
    )

    if (mostrarNuevaJaula) {
        var errorNombre by remember { mutableStateOf<String?>(null) }
        NuevaJaulaDialog(
            onDismiss = { mostrarNuevaJaula = false; errorNombre = null },
            error = errorNombre,
            onCrear = { nombre ->
                val nombreTrim = nombre.trim()
                if (jaulas.any { it.comprador.equals(nombreTrim, ignoreCase = true) }) {
                    errorNombre = "Ya existe una jaula con el nombre '$nombreTrim'"
                } else {
                    scope.launch { 
                        container.ventaRepository.crearJaula(nombreTrim, SessionManager.currentUserId()) 
                    }
                    mostrarNuevaJaula = false
                    errorNombre = null
                }
            }
        )
    }

    if (mostrarAlertaLimite) {
        InfoDialog(
            title = "Límite Alcanzado",
            message = "El límite de 20 jaulas creadas ya fue alcanzado. Debes cerrar o eliminar alguna antes de crear una nueva.",
            onDismiss = { mostrarAlertaLimite = false }
        )
    }

    if (jaulaAEliminar != null) {
        ConfirmDialog(
            title = "Eliminar Jaula",
            message = "¿Estás seguro de eliminar el lote '${jaulaAEliminar!!.comprador}'? Esta acción no se puede deshacer.",
            onConfirm = {
                val jaula = jaulaAEliminar ?: return@ConfirmDialog
                jaulaAEliminar = null
                scope.launch {
                    try {
                        container.ventaRepository.eliminarJaula(jaula)
                        snackbarHostState?.showSnackbar("Jaula '${jaula.comprador}' eliminada correctamente")
                    } catch (e: Exception) {
                        snackbarHostState?.showSnackbar("Error al eliminar jaula: ${e.message}")
                    }
                }
            },
            onDismiss = { jaulaAEliminar = null }
        )
    }

    if (jaulaParaAgregar != null) {
        val jaulaActualizada = jaulas.find { it.idVenta == jaulaParaAgregar!!.idVenta } ?: jaulaParaAgregar!!
        AgregarAnimalesDialog(
            jaula = jaulaActualizada,
            rangos = rangos,
            container = container,
            onDismiss = { jaulaParaAgregar = null },
            isActive = isActive
        )
    }

    if (jaulaParaVerAnimales != null) {
        val jaulaActualizada = jaulas.find { it.idVenta == jaulaParaVerAnimales!!.idVenta } ?: jaulaParaVerAnimales!!
        VerAnimalesDialog(
            jaula = jaulaActualizada, 
            container = container, 
            onDismiss = { jaulaParaVerAnimales = null },
            onAgregarClick = {
                jaulaParaAgregar = it
                jaulaParaVerAnimales = null
            }
        )
    }

    if (jaulaParaVender != null) {
        ConfirmDialog(
            title = "Confirmar Venta",
            message = "¿Deseas finalizar la venta de la jaula '${jaulaParaVender!!.comprador}'? Los animales se marcarán como vendidos y la jaula se cerrará.",
            onConfirm = {
                val jaula = jaulaParaVender!!
                scope.launch {
                    val res = container.ventaRepository.consolidarVenta(jaula.idVenta, SessionManager.currentUserId(), isAdmin)
                    if (res is ResultadoOperacion.Ok) {
                        jaulaParaVender = null
                        val msg = if (isAdmin) "Venta finalizada con éxito" else "Solicitud de venta enviada a revisión"
                        snackbarHostState?.showSnackbar(msg)
                    } else if (res is ResultadoOperacion.Error) {
                        snackbarHostState?.showSnackbar(res.mensaje)
                    }
                }
            },
            onDismiss = { jaulaParaVender = null }
        )
    }
}

@Composable
private fun JaulasStatelessContent(
    jaulas: List<Venta>,
    isAdmin: Boolean,
    onNuevaJaulaClick: () -> Unit,
    onEliminarJaula: (Venta) -> Unit,
    onAgregarAnimales: (Venta) -> Unit,
    onVerAnimales: (Venta) -> Unit,
    onVenderJaula: (Venta) -> Unit
) {
    Column(Modifier.padding(horizontal = 20.dp).fillMaxSize()) {
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            Button(
                onClick = onNuevaJaulaClick,
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.width(4.dp))
                Text("Nueva Jaula")
            }
            Text("Total: ${jaulas.size}", style = MaterialTheme.typography.labelLarge)
        }
        Spacer(Modifier.height(12.dp))

        if (jaulas.isEmpty()) {
            Text("Aún no hay jaulas abiertas. Crea una para comenzar a agrupar animales.")
        }

        LazyColumn(verticalArrangement = Arrangement.spacedBy(16.dp), modifier = Modifier.weight(1f)) {
            items(jaulas, key = { it.idVenta }) { jaula ->
                JaulaCard(
                    jaula = jaula,
                    isAdmin = isAdmin,
                    onEliminar = { onEliminarJaula(jaula) },
                    onAgregar = { onAgregarAnimales(jaula) },
                    onVerAnimales = { onVerAnimales(jaula) },
                    onVender = { onVenderJaula(jaula) }
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun JaulaCard(
    jaula: Venta,
    isAdmin: Boolean,
    onEliminar: () -> Unit,
    onAgregar: () -> Unit,
    onVerAnimales: () -> Unit,
    onVender: () -> Unit
) {
    val currency = remember { java.text.NumberFormat.getCurrencyInstance(java.util.Locale.forLanguageTag("es-MX")) }
    val cabezas = jaula.totalCabezas
    val toneladas = jaula.pesoTotal / 1000.0

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp)) {
            // TÍTULO DE LA JAULA
            Text(
                text = "Jaula: ${jaula.comprador}",
                fontWeight = FontWeight.ExtraBold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(14.dp))

            // BARRA DE CABEZAS
            LabeledProgressBar(
                label = "Cabezas",
                val1 = cabezas.toString(),
                val2 = "110",
                progress = cabezas.toFloat() / 110f
            )
            Spacer(Modifier.height(12.dp))

            // BARRA DE TONELADAS
            LabeledProgressBar(
                label = "Toneladas",
                val1 = String.format(java.util.Locale.forLanguageTag("es-MX"), "%.1f", toneladas),
                val2 = "24.5",
                progress = (toneladas / 24.5).toFloat()
            )
            
            Spacer(Modifier.height(16.dp))
            
            // FILA DE ACCIONES 1 (VER DETALLES Y AGREGAR ANIMALES)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                TextButton(
                    onClick = onVerAnimales,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { 
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Visibility,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            "Ver detalles",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                TextButton(
                    onClick = onAgregar,
                    contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                ) { 
                    Text(
                        "Agregar Animales +",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            HorizontalDivider(
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
            )

            // FILA DE ACCIONES 2 (ELIMINAR Y VENDER JAULA)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isAdmin) {
                    TextButton(
                        onClick = onEliminar,
                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 4.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.error,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "Eliminar",
                                color = MaterialTheme.colorScheme.error,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                } else {
                    Spacer(Modifier.width(1.dp))
                }

                Button(
                    onClick = onVender, 
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    modifier = Modifier.height(44.dp)
                ) { 
                    Text("Vender Jaula", fontWeight = FontWeight.Bold, fontSize = 15.sp) 
                }
            }

            Spacer(Modifier.height(16.dp))

            // CAJA DE TOTAL ACUMULADO CON LÍNEA DIVISORA VERTICAL Y BILLETERA
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                shape = RoundedCornerShape(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Total",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = "acumulado",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.ExtraBold,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }

                    Box(
                        modifier = Modifier
                            .height(34.dp)
                            .width(1.dp)
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f))
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = currency.format(jaula.montoTotal),
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary,
                            maxLines = 1,
                            softWrap = false
                        )
                        Spacer(Modifier.width(10.dp))
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_money_stack),
                            contentDescription = null,
                            tint = SanMartinOrange,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NuevaJaulaDialog(
    onDismiss: () -> Unit,
    error: String? = null,
    onCrear: (String) -> Unit
) {
    var nombre by remember { mutableStateOf("") }

    SimpleFormDialog(
        title = "Nueva Jaula",
        onDismiss = onDismiss,
        onConfirm = { onCrear(nombre.ifBlank { "Jaula" }) }
    ) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { input ->
                val filtered = input.filter { 
                    it.isLetterOrDigit() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || 
                    "áéíóúÁÉÍÓÚ-_".contains(it) 
                }
                if (filtered.length <= 50) {
                    nombre = filtered
                }
            },
            label = { Text("Nombre del lote / comprador") },
            singleLine = true,
            isError = error != null,
            modifier = Modifier.fillMaxWidth()
        )
        if (error != null) {
            Text(
                text = error,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(start = 16.dp, top = 4.dp)
            )
        }
    }
}

@Composable
private fun AgregarAnimalesDialog(
    jaula: Venta, 
    rangos: List<RangoPeso>,
    container: AppContainer, 
    onDismiss: () -> Unit,
    isActive: Boolean = true
) {
    val candidatos by container.ventaRepository.getCandidatos().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    var mensaje by remember { mutableStateOf<String?>(null) }
    var mostrarAlertaFase by remember { mutableStateOf(false) }
    var mostrarScanner by remember { mutableStateOf(false) }
    var mostrarFormularioNuevoAnimal by remember { mutableStateOf(false) }
    var searchArete by remember { mutableStateOf("") }
    var animalParaPeso by remember { mutableStateOf<Animal?>(null) }

    val context = LocalContext.current
    val lanzadorPermisoScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mostrarScanner = true
        } else {
            mensaje = "Permiso de cámara denegado"
        }
    }

    LaunchedEffect(isActive) {
        if (!isActive) mostrarScanner = false
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

    fun intentarSeleccionarAnimal(animal: Animal) {
        val isFullHeads = jaula.totalCabezas >= 115
        val isFullWeight = (jaula.pesoTotal / 1000.0) >= 25.0

        if (animal.fase == FaseGanado.DESARROLLO) {
            mostrarAlertaFase = true
        } else if (isFullHeads) {
            mensaje = "Capacidad máxima alcanzada (115 cabezas)."
        } else if (isFullWeight) {
            mensaje = "Límite de peso alcanzado (25.0 toneladas)."
        } else {
            animalParaPeso = animal
        }
    }

    if (animalParaPeso != null) {
        PesoActualDialog(
            animal = animalParaPeso!!,
            onDismiss = { animalParaPeso = null },
            onConfirm = { pesoActual, montoCobro ->
                scope.launch {
                    val resultado = container.ventaRepository.agregarAnimalAVenta(
                        ventaId = jaula.idVenta,
                        arete = animalParaPeso!!.numeroArete,
                        pesoSalida = pesoActual,
                        montoCobro = montoCobro
                    )
                    if (resultado is ResultadoOperacion.Ok) {
                        animalParaPeso = null
                        searchArete = ""
                    } else if (resultado is ResultadoOperacion.Error) {
                        mensaje = resultado.mensaje
                        animalParaPeso = null
                    }
                }
            }
        )
    }

    if (mostrarFormularioNuevoAnimal) {
        RegistrarYAgregarAnimalAJaulaDialog(
            jaula = jaula,
            container = container,
            onDismiss = { mostrarFormularioNuevoAnimal = false },
            onSuccess = {
                mostrarFormularioNuevoAnimal = false
                searchArete = ""
            }
        )
    }

    if (mostrarScanner && isActive) {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = { mostrarScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            BarcodeScannerScreen(
                onResult = { result ->
                    val sanitized = result.filter { it.isDigit() }.take(15)
                    val animal = candidatos.firstOrNull { it.numeroArete == sanitized }
                    if (animal != null) {
                        intentarSeleccionarAnimal(animal)
                        mostrarScanner = false
                    } else {
                        mensaje = "Animal $sanitized no encontrado o ya asignado."
                        mostrarScanner = false
                    }
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.92f)
                    .fillMaxHeight(0.88f),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp,
                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(Modifier.fillMaxSize().padding(22.dp)) {
                    // Encabezado con título, estado y botón de cierre (X)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "Agregar animales",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Jaula: ${jaula.comprador}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
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

                    Spacer(Modifier.height(16.dp))

                    // Búsqueda e integración de escáner
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchArete,
                            onValueChange = { searchArete = it },
                            placeholder = { Text("Buscar o ingresar arete...", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(16.dp),
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Search,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { iniciarEscaneo() }) {
                                    Icon(
                                        Icons.Default.ViewWeek,
                                        contentDescription = "Escanear",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            },
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
                            )
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // Botón de acción destacada: Registrar y agregar nuevo animal
                    FilledTonalButton(
                        onClick = { mostrarFormularioNuevoAnimal = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.filledTonalButtonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                        ),
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    Icons.Default.AddCircle,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(Modifier.width(10.dp))
                                Text(
                                    text = "Registrar y agregar nuevo animal",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            }
                            Icon(
                                Icons.Default.ChevronRight,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.6f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    val filtrados = remember(searchArete, candidatos) {
                        if (searchArete.isBlank()) candidatos
                        else candidatos.filter { it.numeroArete.contains(searchArete, ignoreCase = true) }
                    }

                    mensaje?.let {
                        Text(
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }

                    Text(
                        text = "Disponibles para carga (${filtrados.size}):",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(10.dp))

                    if (filtrados.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = if (searchArete.isBlank()) "No hay animales disponibles."
                                else "No hay coincidencias para '$searchArete'",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }

                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(filtrados, key = { it.numeroArete }) { animal ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.surface,
                                tonalElevation = 2.dp,
                                border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        AreteTag(animal.numeroArete, animal.tipoArete)
                                        Spacer(Modifier.height(4.dp))
                                        val sexoStr = if (animal.sexo == Sexo.MACHO) "Macho" else "Hembra"
                                        Text(
                                            text = "$sexoStr · ${animal.color} · ${animal.pesoEntrada.toInt()} kg",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Button(
                                        onClick = { intentarSeleccionarAnimal(animal) },
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(12.dp)
                                    ) {
                                        Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(4.dp))
                                        Text("Agregar", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    if (mostrarAlertaFase) {
        InfoDialog(
            title = "Animal en Desarrollo",
            message = "El animal no puede agregarse a la jaula ya que se encuentra en fase de Desarrollo.",
            onDismiss = { mostrarAlertaFase = false }
        )
    }
}

/** Diálogo combinado para registrar un animal nuevo en DB y agregarlo directamente a la jaula. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrarYAgregarAnimalAJaulaDialog(
    jaula: Venta,
    container: AppContainer,
    onDismiss: () -> Unit,
    onSuccess: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var tipoArete by remember { mutableStateOf(TipoArete.SADER) }
    var arete by remember { mutableStateOf("") }
    var color by remember { mutableStateOf("") }
    var sexo by remember { mutableStateOf(Sexo.MACHO) }
    var pesoEntrada by remember { mutableStateOf("") }
    var precioCompra by remember { mutableStateOf("") }
    var proveedor by remember { mutableStateOf("") }

    var nuevoPeso by remember { mutableStateOf("") }
    var montoCobro by remember { mutableStateOf("") }

    var fotoUriStr by remember { mutableStateOf<String?>(null) }
    var pendingUriStr by remember { mutableStateOf<String?>(null) }

    var error by remember { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var mostrarScanner by remember { mutableStateOf(false) }

    var colorMenuAbierto by remember { mutableStateOf(false) }
    var sexoMenuAbierto by remember { mutableStateOf(false) }
    val opcionesColor = listOf("Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo", "Pinto")

    val lanzadorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exitoso ->
        if (exitoso) fotoUriStr = pendingUriStr
    }

    val lanzadorPermisoCamara = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            try {
                val uri = crearUriParaFoto(context)
                pendingUriStr = uri.toString()
                lanzadorCamara.launch(uri)
            } catch (e: Exception) {
                error = "Error al preparar cámara: ${e.message}"
            }
        } else {
            error = "Se necesita permiso de cámara"
        }
    }

    val lanzadorPermisoScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) mostrarScanner = true else error = "Se necesita permiso de cámara para escanear"
    }

    fun abrirCamara() {
        val yaConcedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (yaConcedido) {
            try {
                val uri = crearUriParaFoto(context)
                pendingUriStr = uri.toString()
                lanzadorCamara.launch(uri)
            } catch (e: Exception) {
                error = "Error al abrir cámara: ${e.message}"
            }
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    fun iniciarEscaneo() {
        val yaConcedido = ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED
        if (yaConcedido) mostrarScanner = true else lanzadorPermisoScanner.launch(Manifest.permission.CAMERA)
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
                    arete = result.filter { it.isDigit() }.take(15)
                    mostrarScanner = false
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        androidx.compose.ui.window.Dialog(
            onDismissRequest = onDismiss,
            properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth(0.95f)
                    .fillMaxHeight(0.9f),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surface
            ) {
                Column(
                    modifier = Modifier
                        .padding(20.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "Registrar y Cargar a ${jaula.comprador}",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(Modifier.height(16.dp))

                    // 1. FOTOGRAFÍA
                    Text("Fotografía", style = MaterialTheme.typography.labelLarge, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(72.dp)
                                .clip(RoundedCornerShape(16.dp))
                                .background(Color(0xFFEBE7DF)),
                            contentAlignment = Alignment.Center
                        ) {
                            if (fotoUriStr != null) {
                                AsyncImage(
                                    model = fotoUriStr!!.toUri(),
                                    contentDescription = "Foto animal",
                                    modifier = Modifier.fillMaxSize(),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Icon(
                                    imageVector = ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder),
                                    contentDescription = null,
                                    tint = Color(0xFF94A3B8),
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }
                        Spacer(Modifier.width(14.dp))
                        Column {
                            OutlinedButton(
                                onClick = { abrirCamara() },
                                shape = RoundedCornerShape(50),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFCBD5E1))
                            ) {
                                Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(16.dp), tint = Color(0xFF334155))
                                Spacer(Modifier.width(6.dp))
                                Text(if (fotoUriStr == null) "Tomar foto" else "Repetir foto", color = Color(0xFF334155), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            if (fotoUriStr != null) {
                                TextButton(onClick = { fotoUriStr = null }) {
                                    Text("Quitar foto", color = MaterialTheme.colorScheme.error, fontSize = 11.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // 2. TIPO DE ARETE - CONTROL SEGMENTADO ANIMADO Y FLUIDO
                    val targetBgColor = if (tipoArete == TipoArete.SADER) Color(0xFFFFD54F) else Color(0xFF80CBC4)
                    val animatedBgColor by animateColorAsState(
                        targetValue = targetBgColor,
                        animationSpec = spring(
                            dampingRatio = Spring.DampingRatioLowBouncy,
                            stiffness = Spring.StiffnessMediumLow
                        ),
                        label = "segmentColor"
                    )

                    val saderTextColor by animateColorAsState(
                        targetValue = if (tipoArete == TipoArete.SADER) Color(0xFF1B364B) else Color(0xFF64748B),
                        animationSpec = tween(durationMillis = 250),
                        label = "saderTextColor"
                    )

                    val otroTextColor by animateColorAsState(
                        targetValue = if (tipoArete == TipoArete.OTRO) Color(0xFF1B364B) else Color(0xFF64748B),
                        animationSpec = tween(durationMillis = 250),
                        label = "otroTextColor"
                    )

                    Text("Tipo de Arete", style = MaterialTheme.typography.labelLarge, color = Color(0xFF475569), fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(6.dp))
                    Surface(
                        color = Color(0xFFE2E8ED),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                    ) {
                        BoxWithConstraints(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(4.dp)
                        ) {
                            val segmentWidth = maxWidth / 2

                            val indicatorOffset by animateDpAsState(
                                targetValue = if (tipoArete == TipoArete.SADER) 0.dp else segmentWidth,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioLowBouncy,
                                    stiffness = Spring.StiffnessMediumLow
                                ),
                                label = "indicatorOffset"
                            )

                            // PÍLDORA DESLIZANTE CON COLOR ANIMADO
                            Box(
                                modifier = Modifier
                                    .offset { androidx.compose.ui.unit.IntOffset(indicatorOffset.roundToPx(), 0) }
                                    .width(segmentWidth)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(animatedBgColor)
                            )

                            // SEGMENTOS CLICKEABLES
                            Row(modifier = Modifier.fillMaxSize()) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            tipoArete = TipoArete.SADER
                                            arete = ""
                                            error = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("SADER", color = saderTextColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(50))
                                        .clickable {
                                            tipoArete = TipoArete.OTRO
                                            arete = ""
                                            error = null
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text("OTRO", color = otroTextColor, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 3. CAMPO ARETE
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = arete,
                            onValueChange = { input ->
                                val digitsOnly = input.filter { it.isDigit() }
                                val maxLen = if (tipoArete == TipoArete.SADER) 15 else 6
                                arete = digitsOnly.take(maxLen)
                                error = null
                            },
                            label = { Text(if (tipoArete == TipoArete.SADER) "Arete SADER" else "Arete OTRO") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                        if (tipoArete == TipoArete.SADER) {
                            Spacer(Modifier.width(8.dp))
                            Button(
                                onClick = { iniciarEscaneo() },
                                modifier = Modifier
                                    .height(56.dp)
                                    .width(76.dp),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = RanchoPrimary),
                                contentPadding = PaddingValues(0.dp)
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Icon(Icons.Default.ViewWeek, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Text("Escanear", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 4. DROPDOWNS COLOR Y SEXO
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        ExposedDropdownMenuBox(
                            expanded = colorMenuAbierto,
                            onExpandedChange = { colorMenuAbierto = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = color,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Color") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorMenuAbierto) }
                            )
                            ExposedDropdownMenu(expanded = colorMenuAbierto, onDismissRequest = { colorMenuAbierto = false }) {
                                opcionesColor.forEach { op ->
                                    DropdownMenuItem(text = { Text(op) }, onClick = { color = op; colorMenuAbierto = false })
                                }
                            }
                        }

                        ExposedDropdownMenuBox(
                            expanded = sexoMenuAbierto,
                            onExpandedChange = { sexoMenuAbierto = it },
                            modifier = Modifier.weight(1f)
                        ) {
                            OutlinedTextField(
                                value = if (sexo == Sexo.MACHO) "Macho" else "Hembra",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Sexo") },
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().menuAnchor(),
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexoMenuAbierto) }
                            )
                            ExposedDropdownMenu(expanded = sexoMenuAbierto, onDismissRequest = { sexoMenuAbierto = false }) {
                                DropdownMenuItem(text = { Text("Macho") }, onClick = { sexo = Sexo.MACHO; sexoMenuAbierto = false })
                                DropdownMenuItem(text = { Text("Hembra") }, onClick = { sexo = Sexo.HEMBRA; sexoMenuAbierto = false })
                            }
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // 5. PESO ENTRADA Y PRECIO COMPRA
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = pesoEntrada,
                            onValueChange = { input ->
                                val filtered = input.replace(',', '.')
                                if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                                    val parts = filtered.split('.')
                                    val isDeleting = filtered.length < pesoEntrada.length
                                    if (isDeleting || parts[0].length <= 4) {
                                        if (parts.size == 1 || parts[1].length <= 2) {
                                            pesoEntrada = filtered
                                            if (nuevoPeso.isBlank()) nuevoPeso = filtered
                                            error = null
                                        }
                                    }
                                }
                            },
                            label = { Text("Peso Ent. (kg)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            visualTransformation = ThousandsSeparatorVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )

                        OutlinedTextField(
                            value = precioCompra,
                            onValueChange = { input ->
                                val filtered = input.replace(',', '.')
                                if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                                    val parts = filtered.split('.')
                                    val isDeleting = filtered.length < precioCompra.length
                                    if (isDeleting || parts[0].length <= 6) {
                                        if (parts.size == 1 || parts[1].length <= 2) {
                                            precioCompra = filtered
                                            error = null
                                        }
                                    }
                                }
                            },
                            label = { Text("P. Compra ($)") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            visualTransformation = ThousandsSeparatorVisualTransformation(),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Spacer(Modifier.height(12.dp))

                    // 6. PROVEEDOR
                    OutlinedTextField(
                        value = proveedor,
                        onValueChange = { input ->
                            if (input.length <= 50) {
                                val allowedChars = Regex("[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚ\\-_]*")
                                if (allowedChars.matches(input)) proveedor = input
                            }
                        },
                        label = { Text("Proveedor") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(16.dp))
                    HorizontalDivider(color = Color(0xFFCBD5E1))
                    Spacer(Modifier.height(16.dp))

                    Text("Detalles de Carga a Jaula", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = RanchoPrimary)
                    Spacer(Modifier.height(10.dp))

                    // 7. NUEVO PESO EN JAULA Y MONTO A COBRAR
                    OutlinedTextField(
                        value = nuevoPeso,
                        onValueChange = { input ->
                            val filtered = input.replace(',', '.')
                            if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                                val parts = filtered.split('.')
                                val isDeleting = filtered.length < nuevoPeso.length
                                if (isDeleting || parts[0].length <= 4) {
                                    if (parts.size == 1 || parts[1].length <= 2) {
                                        nuevoPeso = filtered
                                        error = null
                                    }
                                }
                            }
                        },
                        label = { Text("Nuevo Peso en Jaula (kg)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = ThousandsSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(Modifier.height(12.dp))

                    OutlinedTextField(
                        value = montoCobro,
                        onValueChange = { input ->
                            val filtered = input.replace(',', '.')
                            if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                                val parts = filtered.split('.')
                                val isDeleting = filtered.length < montoCobro.length
                                if (isDeleting || parts[0].length <= 6) {
                                    if (parts.size == 1 || parts[1].length <= 2) {
                                        montoCobro = filtered
                                        error = null
                                    }
                                }
                            }
                        },
                        label = { Text("Monto a Cobrar por este Animal ($)") },
                        placeholder = { Text("Ej. 15000") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        visualTransformation = ThousandsSeparatorVisualTransformation(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    error?.let {
                        Spacer(Modifier.height(10.dp))
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }

                    Spacer(Modifier.height(20.dp))

                    // BOTONES CANCELAR Y CONFIRMAR
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(onClick = onDismiss, enabled = !guardando) {
                            Text("Cancelar")
                        }
                        Spacer(Modifier.width(12.dp))
                        Button(
                            onClick = {
                                val pEntD = pesoEntrada.toDoubleOrNull()
                                val pCompD = precioCompra.toDoubleOrNull()
                                val nPesoD = nuevoPeso.toDoubleOrNull()
                                val mCobroD = montoCobro.toDoubleOrNull()

                                when {
                                    arete.isBlank() -> error = "El arete es obligatorio"
                                    tipoArete == TipoArete.SADER && arete.length != 15 -> error = "El arete SADER debe tener exactamente 15 dígitos"
                                    tipoArete == TipoArete.OTRO && arete.length > 6 -> error = "El arete OTRO no puede exceder 6 dígitos"
                                    color.isBlank() -> error = "El color es obligatorio"
                                    pEntD == null || pEntD < 40 || pEntD > 1000 -> error = "El peso de entrada debe estar entre 40 y 1,000 kg"
                                    pCompD == null || pCompD < 400 || pCompD > 100000 -> error = "El precio de compra debe estar entre 400 y 100,000"
                                    proveedor.isBlank() -> error = "El proveedor es obligatorio"
                                    nPesoD == null || nPesoD < 40 || nPesoD > 1000 -> error = "El nuevo peso debe estar entre 40 y 1,000 kg"
                                    mCobroD == null || mCobroD < 100 || mCobroD > 100000 -> error = "El monto a cobrar debe estar entre $100 y $100,000"
                                    else -> {
                                        guardando = true
                                        scope.launch {
                                            val optimizedPath = if (fotoUriStr != null) {
                                                withContext(Dispatchers.IO) {
                                                    ImageOptimizer.optimize(context, fotoUriStr!!.toUri())
                                                }
                                            } else null

                                            // STEP 1: REGISTRAR ANIMAL EN DB
                                            val resReg = container.ganadoRepository.registrarAnimal(
                                                arete = arete,
                                                tipoArete = tipoArete,
                                                color = color,
                                                sexo = sexo,
                                                peso = pEntD,
                                                precioCompra = pCompD,
                                                proveedor = proveedor.trim(),
                                                fotografiaLocal = optimizedPath,
                                                idUsuario = SessionManager.currentUserId(),
                                                esAdmin = SessionManager.isAdmin()
                                            )

                                            if (resReg is ResultadoOperacion.Error) {
                                                guardando = false
                                                error = resReg.mensaje
                                                return@launch
                                            }

                                            // STEP 2: AGREGAR A LA JAULA
                                            val resAdd = container.ventaRepository.agregarAnimalAVenta(
                                                ventaId = jaula.idVenta,
                                                arete = arete,
                                                pesoSalida = nPesoD,
                                                montoCobro = mCobroD
                                            )

                                            guardando = false
                                            if (resAdd is ResultadoOperacion.Ok) {
                                                onSuccess()
                                            } else if (resAdd is ResultadoOperacion.Error) {
                                                error = resAdd.mensaje
                                            }
                                        }
                                    }
                                }
                            },
                            enabled = !guardando,
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Text(if (guardando) "Procesando..." else "Registrar y Cargar")
                        }
                    }
                }
            }
        }
    }
}

private fun crearUriParaFoto(context: Context): Uri {
    val carpeta = File(context.cacheDir, "fotos_ganado").apply { if (!exists()) mkdirs() }
    val archivo = File.createTempFile("animal_", ".jpg", carpeta)
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, archivo)
}

/** Diálogo para pedir el peso actual y monto total por animal antes de agregar a la jaula. */
@Composable
fun PesoActualDialog(
    animal: Animal,
    onDismiss: () -> Unit,
    onConfirm: (Double, Double) -> Unit
) {
    var peso by remember { mutableStateOf(animal.pesoEntrada.toInt().toString()) }
    var monto by remember { mutableStateOf("0") }
    var error by remember { mutableStateOf<String?>(null) }

    SimpleFormDialog(
        title = "Detalles de Carga",
        onDismiss = onDismiss,
        confirmLabel = "Confirmar",
        onConfirm = {
            val p = peso.toDoubleOrNull()
            val m = monto.toDoubleOrNull()
            if (p == null || p <= 0) {
                error = "Ingresa un peso válido"
            } else if (m == null || m < 100) {
                error = "El monto mínimo por animal debe ser de $100"
            } else {
                onConfirm(p, m)
            }
        }
    ) {
        Text("Animal: ${animal.numeroArete}", fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = peso,
            onValueChange = { peso = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Nuevo Peso (kg)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = ThousandsSeparatorVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Monto a Cobrar por este Animal ($)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            visualTransformation = ThousandsSeparatorVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Ej. 15000") }
        )

        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun VerAnimalesDialog(
    jaula: Venta, 
    container: AppContainer, 
    onDismiss: () -> Unit,
    onAgregarClick: (Venta) -> Unit
) {
    val detalles by container.ventaRepository.getDetalles(jaula.idVenta).collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    val currency = remember { java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "MX")) }

    var animalAEliminar by remember { mutableStateOf<DetalleVenta?>(null) }
    var confirmandoVaciar by remember { mutableStateOf(false) }

    if (animalAEliminar != null) {
        ConfirmDialog(
            title = "Quitar animal",
            message = "¿Estás seguro de quitar el arete ${animalAEliminar!!.numeroArete} de este lote?",
            onConfirm = {
                scope.launch {
                    container.ventaRepository.eliminarAnimalDeVenta(animalAEliminar!!)
                    animalAEliminar = null
                }
            },
            onDismiss = { animalAEliminar = null }
        )
    }

    if (confirmandoVaciar) {
        ConfirmDialog(
            title = "Vaciar jaula",
            message = "¿Estás seguro de quitar TODOS los animales de este lote? Esta acción no se puede deshacer.",
            onConfirm = {
                scope.launch {
                    container.ventaRepository.vaciarJaula(jaula.idVenta)
                    confirmandoVaciar = false
                }
            },
            onDismiss = { confirmandoVaciar = false }
        )
    }

    androidx.compose.ui.window.Dialog(
        onDismissRequest = onDismiss,
        properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(0.95f).fillMaxHeight(0.8f),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Animales en la jaula", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    TextButton(onClick = { onAgregarClick(jaula) }) {
                        Icon(Icons.Default.Add, contentDescription = null)
                        Spacer(Modifier.width(4.dp))
                        Text("Agregar")
                    }
                }
                
                Spacer(Modifier.height(16.dp))
                
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", modifier = Modifier.width(30.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("Arete", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)
                    Text("Peso (kg)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("Precio (kg)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("Subtotal", modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(40.dp))
                }
                
                HorizontalDivider()

                if (detalles.isEmpty()) {
                    Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                        Text("No hay animales en este lote.", color = MaterialTheme.colorScheme.outline)
                    }
                } else {
                    LazyColumn(Modifier.weight(1f)) {
                        itemsIndexed(detalles, key = { _, it -> it.idDetalleVenta }) { index, detalle ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text((index + 1).toString(), modifier = Modifier.width(30.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                                Text(detalle.numeroArete, modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                Text(detalle.pesoSalida.toInt().toString(), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodyMedium)
                                Text(currency.format(detalle.precioUnitario), modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.bodySmall)
                                Text(currency.format(detalle.subtotal), modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                                
                                IconButton(
                                    onClick = { animalAEliminar = detalle },
                                    modifier = Modifier.width(40.dp)
                                ) {
                                    Icon(Icons.Default.Delete, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                }

                Spacer(Modifier.height(8.dp))
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("${detalles.size} animales agregados", style = MaterialTheme.typography.bodyMedium, color = RanchoTextSecondary)
                    TextButton(onClick = { confirmandoVaciar = true }) {
                        Text("Vaciar jaula", color = MaterialTheme.colorScheme.error)
                    }
                }
                
                Spacer(Modifier.height(8.dp))
                Button(onClick = onDismiss, modifier = Modifier.align(Alignment.End)) {
                    Text("Cerrar")
                }
            }
        }
    }
}
