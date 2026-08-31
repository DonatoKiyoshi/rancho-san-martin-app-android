package com.rancho.gestion.ui.vender

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payments
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rancho.gestion.AppContainer
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
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoSuccess
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
                jaulaAEliminar = null // Cerrar inmediatamente
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
        // Encontrar la versión reactiva de la jaula para tener totales actualizados
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
    val currency = remember { java.text.NumberFormat.getCurrencyInstance(java.util.Locale("es", "MX")) }
    val cabezas = jaula.totalCabezas
    val toneladas = jaula.pesoTotal / 1000.0

    SectionCard {
        Text(
            text = "Jaula: ${jaula.comprador}",
            fontWeight = FontWeight.Bold,
            style = MaterialTheme.typography.titleMedium
        )
        Spacer(Modifier.height(12.dp))
        LabeledProgressBar("Cabezas", cabezas.toString(), "110", cabezas.toFloat() / 110)
        Spacer(Modifier.height(10.dp))
        LabeledProgressBar(
            "Toneladas",
            String.format("%.1f", jaula.pesoTotal / 1000.0),
            "24.5",
            (toneladas / 24.5).toFloat()
        )
        
        Spacer(Modifier.height(12.dp))
        
        // Fila 1: Ver detalles / Agregar Animales (Swap PROC-19)
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onVerAnimales) { 
                Text("Ver detalles", fontWeight = FontWeight.Bold) 
            }
            TextButton(onClick = onAgregar) { 
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Agregar Animales", fontWeight = FontWeight.Medium)
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }

        Spacer(Modifier.height(8.dp))
        
        // Fila 2: Eliminar (Admin) / Vender Jaula
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            if (isAdmin) {
                TextButton(onClick = onEliminar) {
                    Text("Eliminar", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Bold)
                }
            } else {
                Spacer(Modifier.width(1.dp))
            }
            Button(
                onClick = onVender, 
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.height(40.dp)
            ) { 
                Text("Vender Jaula", fontWeight = FontWeight.Bold) 
            }
        }

        Spacer(Modifier.height(12.dp))

        // Nueva Fila: Monto Total (Ajuste visual para no cortar cantidad)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "Total",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RanchoPrimary
                )
                Text(
                    text = "Acumulado:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = RanchoPrimary
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = currency.format(jaula.montoTotal),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = RanchoPrimary,
                    maxLines = 1,
                    softWrap = false
                )
                Spacer(Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Payments, 
                    contentDescription = null,
                    tint = RanchoSuccess,
                    modifier = Modifier.size(28.dp)
                )
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
                // Filtrar caracteres: letras, números, espacios, ñ, Ñ, vocales acentuadas, guión y guión bajo
                val filtered = input.filter { 
                    it.isLetterOrDigit() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || 
                    "áéíóúÁÉÍÓÚ-_".contains(it) 
                }
                // Limitar a 50 caracteres
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
        androidx.compose.ui.window.Dialog(onDismissRequest = onDismiss) {
            Surface(shape = androidx.compose.foundation.shape.RoundedCornerShape(24.dp)) {
                Column(Modifier.padding(20.dp).heightIn(max = 600.dp)) {
                    Text("Agregar animales a ${jaula.comprador}", style = MaterialTheme.typography.titleLarge)
                    Spacer(Modifier.height(16.dp))
                    
                    // Buscador manual y botón de Scanner (RF-UPDATE: Dinámico)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = searchArete,
                            onValueChange = { searchArete = it },
                            label = { Text("Ingresar Arete") },
                            modifier = Modifier.weight(1f),
                            singleLine = true,
                            shape = RoundedCornerShape(12.dp),
                            leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) }
                        )
                        Spacer(Modifier.width(8.dp))
                        IconButton(
                            onClick = { iniciarEscaneo() },
                            modifier = Modifier
                                .size(56.dp)
                                .background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp))
                        ) {
                            Icon(Icons.Default.ViewWeek, contentDescription = "Escanear", tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                    
                    val filtrados = remember(searchArete, candidatos) {
                        if (searchArete.isBlank()) candidatos
                        else candidatos.filter { it.numeroArete.contains(searchArete, ignoreCase = true) }
                    }
                    
                    mensaje?.let { 
                        Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                        Spacer(Modifier.height(8.dp))
                    }
                    
                    Text("Disponibles para carga:", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    Spacer(Modifier.height(8.dp))

                    if (filtrados.isEmpty()) {
                        Text(
                            if (searchArete.isBlank()) "No hay animales disponibles." 
                            else "No hay coincidencias para '$searchArete'",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                    
                    LazyColumn(Modifier.weight(1f, fill = false)) {
                        items(filtrados, key = { it.numeroArete }) { animal ->
                            Row(
                                Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(Modifier.weight(1f)) {
                                    AreteTag(animal.numeroArete, animal.tipoArete)
                                    val sexoStr = if (animal.sexo == Sexo.MACHO) "Macho" else "Hembra"
                                    Text(
                                        text = "$sexoStr · ${animal.color} · ${animal.pesoEntrada.toInt()} kg", 
                                        fontSize = 12.sp, 
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Button(
                                    onClick = { intentarSeleccionarAnimal(animal) },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp)
                                ) { 
                                    Text("Agregar", fontSize = 12.sp) 
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        TextButton(onClick = onDismiss) { Text("Cerrar") }
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
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(Modifier.height(16.dp))
        
        OutlinedTextField(
            value = monto,
            onValueChange = { monto = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Monto a Cobrar por este Animal ($)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
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
                // Header
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
                
                // Table Header
                Row(
                    Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)).padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("#", modifier = Modifier.width(30.dp), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("Arete", modifier = Modifier.weight(1.5f), style = MaterialTheme.typography.labelMedium)
                    Text("Peso (kg)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("Precio (kg)", modifier = Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelMedium)
                    Text("Subtotal", modifier = Modifier.weight(1.2f), textAlign = TextAlign.End, style = MaterialTheme.typography.labelMedium)
                    Spacer(Modifier.width(40.dp)) // For delete icon
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

                // Footer
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
