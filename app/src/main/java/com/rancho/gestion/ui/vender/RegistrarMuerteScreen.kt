package com.rancho.gestion.ui.vender

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.Animal
import com.rancho.gestion.data.entities.EstadoInventario
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.domain.MermaCalculator
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.BarcodeScannerScreen
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.components.SuccessDialog
import com.rancho.gestion.ui.components.ThousandsSeparatorVisualTransformation
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch
import java.text.NumberFormat
import java.util.Locale

@Composable
fun RegistrarMuerteScreen(container: AppContainer, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = "Registrar Muerte", onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            RegistrarMuerteContent(container)
        }
    }
}

/** Stateful version for internal use and container screens. */
@Composable
fun RegistrarMuerteContent(
    container: AppContainer, 
    isActive: Boolean = true,
    onSalirToGanado: () -> Unit = {}
) {
    var mostrarScanner by remember { mutableStateOf(false) }
    var scannedArete by remember { mutableStateOf("") }
    
    val context = LocalContext.current
    val lanzadorPermisoScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mostrarScanner = true
        }
    }

    // Auto-close scanner if page becomes inactive
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
                    scannedArete = result.filter { it.isDigit() }.take(15)
                    mostrarScanner = false
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        RegistrarMuerteStatelessContent(
            scannedArete = scannedArete,
            onScannedAreteConsumed = { scannedArete = "" },
            onEscanearClick = { iniciarEscaneo() },
            onBuscarPorArete = { arete -> container.ganadoRepository.buscarPorArete(arete) },
            onSalirToGanado = onSalirToGanado,
            onRegistrarMuerte = { arete, motivo, recuperado ->
                container.ganadoRepository.registrarMuerte(
                    arete = arete, 
                    motivo = motivo, 
                    recuperacion = recuperado,
                    idUsuario = SessionManager.currentUserId(),
                    esAdmin = SessionManager.isAdmin()
                )
            }
        )
    }
}

@Composable
private fun RegistrarMuerteStatelessContent(
    scannedArete: String = "",
    onScannedAreteConsumed: () -> Unit = {},
    onEscanearClick: () -> Unit = {},
    onBuscarPorArete: suspend (String) -> Animal?,
    onSalirToGanado: () -> Unit = {},
    onRegistrarMuerte: suspend (String, String, Double) -> ResultadoOperacion
) {
    var arete by remember { mutableStateOf("") }
    var animalEncontrado by remember { mutableStateOf<Animal?>(null) }
    var noEncontrado by remember { mutableStateOf(false) }
    var buscando by remember { mutableStateOf(false) }

    var motivo by remember { mutableStateOf("") }
    var valorRecuperado by remember { mutableStateOf("") }
    var confirmando by remember { mutableStateOf(false) }
    var mensajeExito by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }

    val scope = rememberCoroutineScope()
    val currency = remember { NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")) }

    LaunchedEffect(scannedArete) {
        if (scannedArete.isNotBlank()) {
            arete = scannedArete
            onScannedAreteConsumed()
            // Auto-trigger search after scan
            buscando = true
            try {
                val animal = onBuscarPorArete(arete.trim())
                if (animal != null && animal.estadoInventario == EstadoInventario.ACTIVO) {
                    animalEncontrado = animal
                    noEncontrado = false
                } else {
                    animalEncontrado = null
                    noEncontrado = true
                }
            } catch (e: Exception) {
                // Silently handle cancellation or other errors to avoid UI flicker
                noEncontrado = true
            } finally {
                buscando = false
            }
        }
    }

    Column(
        Modifier
            .fillMaxSize()
            .padding(20.dp)
            .imePadding()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.Top
    ) {
        SectionCard {
            Text("Ingrese el Arete", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = arete,
                    onValueChange = { input ->
                        if (input.all { c -> c.isDigit() } && input.length <= 15) {
                            arete = input
                            animalEncontrado = null
                            noEncontrado = false
                            mensajeExito = null
                        }
                    },
                    label = { Text("Arete") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(
                    onClick = onEscanearClick,
                    modifier = Modifier.size(width = 80.dp, height = 56.dp),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    contentPadding = PaddingValues(0.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.ViewWeek, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary)
                        Text("Escanear", fontSize = 10.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onPrimary)
                    }
                }
            }
            
            Spacer(Modifier.height(12.dp))
            Button(
                onClick = {
                    val areteTrim = arete.trim()
                    if (areteTrim.isBlank()) return@Button
                    buscando = true
                    scope.launch {
                        try {
                            val animal = onBuscarPorArete(areteTrim)
                            if (animal != null && animal.estadoInventario == EstadoInventario.ACTIVO) {
                                animalEncontrado = animal
                                noEncontrado = false
                            } else {
                                animalEncontrado = null
                                noEncontrado = true
                            }
                        } catch (e: Exception) {
                            noEncontrado = true
                        } finally {
                            buscando = false
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = arete.isNotBlank() && !buscando
            ) { Text(if (buscando) "Buscando..." else "Buscar") }

            if (noEncontrado) {
                Spacer(Modifier.height(10.dp))
                Text("No se encontró un animal activo con ese arete.", color = MaterialTheme.colorScheme.error)
            }

            animalEncontrado?.let { animal ->
                Spacer(Modifier.height(16.dp))
                Text("Arete encontrado", style = MaterialTheme.typography.bodyLarge)
                Text("Color: ${animal.color} · Peso: ${animal.pesoEntrada.toInt()} kg")
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = motivo,
                    onValueChange = { input ->
                        if (input.length <= 50) {
                            val regex = Regex("[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚ]*")
                            if (regex.matches(input)) {
                                motivo = input
                                error = null
                            }
                        }
                    },
                    label = { Text("Motivo / causa de la muerte") },
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = valorRecuperado,
                    onValueChange = { input ->
                        val filtered = input.filter { it.isDigit() || it == '.' }
                        if (filtered.count { it == '.' } <= 1) {
                            val parts = filtered.split('.')
                            val isDeleting = filtered.length < valorRecuperado.length
                            if (isDeleting || parts[0].length <= 6) {
                                if (parts.size < 2 || parts[1].length <= 2) {
                                    valorRecuperado = filtered
                                    error = null
                                }
                            }
                        }
                    },
                    label = { Text("Valor recuperado (rastro, restos, etc.)") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = ThousandsSeparatorVisualTransformation(),
                    modifier = Modifier.fillMaxWidth()
                )
                val recuperadoD = valorRecuperado.toDoubleOrNull() ?: 0.0
                val perdida = MermaCalculator.calcularPerdida(animal.precioCompra, recuperadoD)
                Spacer(Modifier.height(8.dp))
                Text(
                    "Pérdida estimada: ${currency.format(perdida)} (compra ${currency.format(animal.precioCompra)} − recuperado ${currency.format(recuperadoD)})",
                    style = MaterialTheme.typography.bodyMedium
                )

                Text("¿Desea Eliminar el Animal?", modifier = Modifier.padding(top = 14.dp))
                Row(Modifier.padding(top = 8.dp)) {
                    Button(onClick = {
                        val recuperadoD = valorRecuperado.toDoubleOrNull() ?: 0.0
                        if (motivo.isBlank()) {
                            error = "El motivo de la muerte es obligatorio"
                        } else if (recuperadoD < 0 || recuperadoD > 100000) {
                            error = "El valor recuperado debe estar entre $0 y $100,000"
                        } else {
                            confirmando = true
                        }
                    }) { Text("SI") }
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(onClick = {
                        animalEncontrado = null
                        arete = ""
                    }) { Text("NO") }
                }
            }

            error?.let {
                Spacer(Modifier.height(8.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }
        }
    }

    if (mensajeExito != null) {
        val isAdmin = SessionManager.isAdmin()
        SuccessDialog(
            title = "¡Baja Exitosa!",
            message = if (isAdmin) 
                "La operación fue realizada con éxito."
            else 
                "La operación fue realizada con éxito y queda en espera de revisión por un administrador.",
            onContinuar = { 
                mensajeExito = null
                animalEncontrado = null
                arete = ""; motivo = ""; valorRecuperado = ""
            },
            onSalir = onSalirToGanado,
            continuarLabel = "Continuar"
        )
    }

    if (confirmando && animalEncontrado != null) {
        val animal = animalEncontrado!!
        val recuperadoD = valorRecuperado.toDoubleOrNull() ?: 0.0
        ConfirmDialog(
            title = "Confirmar baja por muerte",
            message = "Se dará de baja el arete ${animal.numeroArete} y se registrará una pérdida neta de " +
                currency.format(MermaCalculator.calcularPerdida(animal.precioCompra, recuperadoD)) + ".",
            onConfirm = {
                scope.launch {
                    val resultado = onRegistrarMuerte(animal.numeroArete, motivo, recuperadoD)
                    confirmando = false
                    when (resultado) {
                        is ResultadoOperacion.Ok -> {
                            mensajeExito = "Baja registrada correctamente."
                            animalEncontrado = null
                            arete = ""; motivo = ""; valorRecuperado = ""
                        }
                        is ResultadoOperacion.Error -> error = resultado.mensaje
                    }
                }
            },
            onDismiss = { confirmando = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun RegistrarMuertePreview() {
    MaterialTheme {
        RegistrarMuerteStatelessContent(
            onBuscarPorArete = { null },
            onRegistrarMuerte = { _, _, _ -> ResultadoOperacion.Ok }
        )
    }
}
