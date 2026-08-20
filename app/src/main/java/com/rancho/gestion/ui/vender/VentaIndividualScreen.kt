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
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.BarcodeScannerScreen
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.SuccessDialog
import com.rancho.gestion.ui.components.InfoDialog
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch

@Composable
fun VentaIndividualScreen(container: AppContainer, onBack: () -> Unit) {
    Scaffold(topBar = { BackTopBar(title = "Venta Individual", onBack = onBack) }) { padding ->
        Box(Modifier.padding(padding)) {
            VentaIndividualContent(container)
        }
    }
}

/** Stateful version for internal use and container screens. */
@Composable
fun VentaIndividualContent(
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
                    scannedArete = result
                    mostrarScanner = false
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        VentaIndividualStatelessContent(
            scannedArete = scannedArete,
            onScannedAreteConsumed = { scannedArete = "" },
            onEscanearClick = { iniciarEscaneo() },
            onSalirToGanado = onSalirToGanado,
            onCheckFase = { arete ->
                container.ganadoRepository.buscarPorArete(arete.trim())?.fase
            },
            onVender = { arete, comprador, peso, monto, confirmar ->
                container.ventaRepository.realizarVentaIndividual(
                    arete = arete,
                    comprador = comprador,
                    pesoSalida = peso,
                    montoCobro = monto,
                    idUsuario = SessionManager.currentUserId(),
                    esAdmin = SessionManager.isAdmin(),
                    confirmarRemoverDeJaula = confirmar
                )
            }
        )
    }
}

@Composable
private fun VentaIndividualStatelessContent(
    scannedArete: String = "",
    onScannedAreteConsumed: () -> Unit = {},
    onEscanearClick: () -> Unit = {},
    onSalirToGanado: () -> Unit = {},
    onCheckFase: suspend (String) -> com.rancho.gestion.data.entities.FaseGanado? = { null },
    onVender: suspend (String, String, Double, Double, Boolean) -> ResultadoOperacion
) {
    var arete by remember { mutableStateOf("") }
    var comprador by remember { mutableStateOf("") }
    var peso by remember { mutableStateOf("") }
    var precio by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var exito by remember { mutableStateOf(false) }
    var guardando by remember { mutableStateOf(false) }
    var mostrarAlertaFase by remember { mutableStateOf(false) }
    var mostrarConfirmacionJaula by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(scannedArete) {
        if (scannedArete.isNotBlank()) {
            arete = scannedArete
            onScannedAreteConsumed()
        }
    }

    Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState())) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = arete,
                onValueChange = { arete = it; error = null },
                label = { Text("Arete") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            Button(
                onClick = onEscanearClick,
                modifier = Modifier.size(width = 80.dp, height = 56.dp),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = RanchoPrimary),
                contentPadding = PaddingValues(0.dp)
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.ViewWeek, contentDescription = null)
                    Text("Escanear", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
        
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = comprador,
            onValueChange = { comprador = it },
            label = { Text("Comprador") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = peso,
            onValueChange = { peso = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Peso") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = precio,
            onValueChange = { precio = it.filter { c -> c.isDigit() || c == '.' } },
            label = { Text("Monto a Cobrar ($)") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            modifier = Modifier.fillMaxWidth()
        )

        error?.let {
            Spacer(Modifier.height(10.dp))
            Text(it, color = MaterialTheme.colorScheme.error)
        }
        if (exito) {
            val isAdmin = SessionManager.isAdmin()
            SuccessDialog(
                title = "¡Venta Exitosa!",
                message = if (isAdmin) 
                    "La operación fue realizada con éxito."
                else 
                    "La operación fue realizada con éxito y queda en espera de revisión por un administrador.",
                onContinuar = { 
                    exito = false
                    arete = ""; comprador = ""; peso = ""; precio = ""
                },
                onSalir = onSalirToGanado,
                continuarLabel = "Continuar"
            )
        }

        Spacer(Modifier.height(28.dp))
        Button(
            onClick = {
                val areteTrim = arete.trim()
                val compradorTrim = comprador.trim()
                val pesoD = peso.toDoubleOrNull()
                val precioD = precio.toDoubleOrNull()
                when {
                    areteTrim.isBlank() -> error = "El arete es obligatorio"
                    compradorTrim.isBlank() -> error = "El comprador es obligatorio"
                    pesoD == null || pesoD <= 0 -> error = "Ingresa un peso válido"
                    precioD == null || precioD <= 0 -> error = "Ingresa un precio válido"
                    else -> {
                        guardando = true
                        scope.launch {
                            val fase = onCheckFase(areteTrim)
                            if (fase == com.rancho.gestion.data.entities.FaseGanado.DESARROLLO) {
                                guardando = false
                                mostrarAlertaFase = true
                                return@launch
                            }

                            val resultado = onVender(areteTrim, compradorTrim, pesoD, precioD, false)
                            guardando = false
                            when (resultado) {
                                is ResultadoOperacion.Ok -> {
                                    exito = true
                                }
                                is ResultadoOperacion.Error -> {
                                    if (resultado.mensaje.startsWith("JAULA_CONFLICT:")) {
                                        mostrarConfirmacionJaula = resultado.mensaje.substringAfter(":")
                                    } else {
                                        error = resultado.mensaje
                                    }
                                }
                            }
                        }
                    }
                }
            },
            enabled = !guardando,
            shape = androidx.compose.foundation.shape.RoundedCornerShape(50),
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(if (guardando) "Guardando..." else "Vender") }

        if (mostrarAlertaFase) {
            InfoDialog(
                title = "Animal en Desarrollo",
                message = "El animal no puede venderse ya que se encuentra en fase de Desarrollo.",
                onDismiss = { mostrarAlertaFase = false }
            )
        }

        if (mostrarConfirmacionJaula != null) {
            val jaulaNombre = mostrarConfirmacionJaula!!
            ConfirmDialog(
                title = "Animal en Jaula",
                message = "El animal se encuentra dado de alta en la jaula '$jaulaNombre'. ¿Deseas realizar la venta individual? Al hacerlo, se eliminará de la jaula automáticamente.",
                confirmLabel = "Vender y quitar de jaula",
                onConfirm = {
                    scope.launch {
                        guardando = true
                        val res = onVender(arete.trim(), comprador.trim(), peso.toDoubleOrNull() ?: 0.0, precio.toDoubleOrNull() ?: 0.0, true)
                        guardando = false
                        mostrarConfirmacionJaula = null
                        if (res is ResultadoOperacion.Ok) exito = true else error = (res as ResultadoOperacion.Error).mensaje
                    }
                },
                onDismiss = { mostrarConfirmacionJaula = null }
            )
        }
    }
}

@Preview(showBackground = true)
@Composable
fun VentaIndividualPreview() {
    MaterialTheme {
        VentaIndividualStatelessContent(
            onCheckFase = { null },
            onVender = { _, _, _, _, _ -> ResultadoOperacion.Ok }
        )
    }
}
