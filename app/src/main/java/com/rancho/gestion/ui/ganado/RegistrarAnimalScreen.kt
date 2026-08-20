package com.rancho.gestion.ui.ganado

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.ViewWeek
import androidx.compose.ui.res.vectorResource
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.BarcodeScannerScreen
import com.rancho.gestion.ui.components.SuccessDialog
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
fun RegistrarAnimalScreen(container: AppContainer, onBack: () -> Unit) {
    val context = LocalContext.current
    var arete by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var sexo by rememberSaveable { mutableStateOf(Sexo.MACHO) }
    var peso by rememberSaveable { mutableStateOf("") }
    var precio by rememberSaveable { mutableStateOf("") }
    var proveedor by rememberSaveable { mutableStateOf("") }
    
    // Almacenar URIs como String para mejor persistencia en rememberSaveable
    var fotoUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    var pendingUriStr by rememberSaveable { mutableStateOf<String?>(null) }
    
    var error by rememberSaveable { mutableStateOf<String?>(null) }
    var guardando by remember { mutableStateOf(false) }
    var exito by rememberSaveable { mutableStateOf(false) }
    var mostrarScanner by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val isAdmin = SessionManager.isAdmin()

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
            error = "Se necesita permiso de cámara para tomar la foto"
        }
    }

    val lanzadorPermisoScanner = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            mostrarScanner = true
        } else {
            error = "Se necesita permiso de cámara para escanear"
        }
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
        if (yaConcedido) {
            mostrarScanner = true
        } else {
            lanzadorPermisoScanner.launch(Manifest.permission.CAMERA)
        }
    }

    if (mostrarScanner) {
        Dialog(
            onDismissRequest = { mostrarScanner = false },
            properties = androidx.compose.ui.window.DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false
            )
        ) {
            BarcodeScannerScreen(
                onResult = { result ->
                    arete = result
                    mostrarScanner = false
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        RegistrarAnimalContent(
            arete = arete,
            onAreteChange = { arete = it; error = null },
            color = color,
            onColorChange = { color = it },
            sexo = sexo,
            onSexoChange = { sexo = it },
            peso = peso,
            onPesoChange = { peso = it.filter { c -> c.isDigit() || c == '.' } },
            precio = precio,
            onPrecioChange = { precio = it.filter { c -> c.isDigit() || c == '.' } },
            proveedor = proveedor,
            onProveedorChange = { proveedor = it },
            fotoUri = fotoUriStr?.toUri(),
            onTomarFoto = { abrirCamara() },
            onQuitarFoto = { fotoUriStr = null },
            onEscanearClick = { iniciarEscaneo() },
            error = error,
            exito = exito,
            guardando = guardando,
            isAdmin = isAdmin,
            onGuardar = {
                val pesoD = peso.toDoubleOrNull()
                val precioD = precio.toDoubleOrNull()
                when {
                    arete.isBlank() -> error = "El arete es obligatorio"
                    !arete.startsWith("484") -> error = "El arete debe empezar con 484"
                    arete.length > 15 -> error = "El arete no debe exceder 15 dígitos"
                    pesoD == null || pesoD <= 0 -> error = "Ingresa un peso válido"
                    precioD == null || precioD < 0 -> error = "Ingresa un precio válido"
                    proveedor.isBlank() -> error = "El proveedor es obligatorio"
                    else -> {
                        guardando = true
                        scope.launch {
                            val optimizedPath = if (fotoUriStr != null) {
                                withContext(Dispatchers.IO) {
                                    ImageOptimizer.optimize(context, fotoUriStr!!.toUri())
                                }
                            } else null

                            val resultado = container.ganadoRepository.registrarAnimal(
                                arete = arete,
                                color = color,
                                sexo = sexo,
                                peso = pesoD,
                                precioCompra = precioD,
                                proveedor = proveedor,
                                fotografiaLocal = optimizedPath,
                                idUsuario = SessionManager.currentUserId(),
                                esAdmin = SessionManager.isAdmin()
                            )
                            guardando = false
                            when (resultado) {
                                is ResultadoOperacion.Ok -> {
                                    exito = true
                                }
                                is ResultadoOperacion.Error -> error = resultado.mensaje
                            }
                        }
                    }
                }
            },
            onContinuar = {
                exito = false
                arete = ""; color = ""; peso = ""; precio = ""; proveedor = ""
                fotoUriStr = null
            },
            onBack = onBack
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RegistrarAnimalContent(
    arete: String,
    onAreteChange: (String) -> Unit,
    color: String,
    onColorChange: (String) -> Unit,
    sexo: Sexo,
    onSexoChange: (Sexo) -> Unit,
    peso: String,
    onPesoChange: (String) -> Unit,
    precio: String,
    onPrecioChange: (String) -> Unit,
    proveedor: String,
    onProveedorChange: (String) -> Unit,
    fotoUri: Uri?,
    onTomarFoto: () -> Unit,
    onQuitarFoto: () -> Unit,
    onEscanearClick: () -> Unit,
    error: String?,
    exito: Boolean,
    guardando: Boolean,
    isAdmin: Boolean,
    onGuardar: () -> Unit,
    onContinuar: () -> Unit,
    onBack: () -> Unit
) {
    var sexoMenuAbierto by remember { mutableStateOf(false) }
    var colorMenuAbierto by remember { mutableStateOf(false) }
    val opcionesColor = listOf("Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo")

    Scaffold(topBar = { BackTopBar(title = "Registrar Animal", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(8.dp))

            Text("Fotografía (opcional)", style = MaterialTheme.typography.labelLarge, color = RanchoTextSecondary)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(84.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (fotoUri != null) {
                        AsyncImage(
                            model = fotoUri,
                            contentDescription = "Foto del animal",
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder),
                            contentDescription = null,
                            tint = RanchoTextSecondary.copy(alpha = 0.5f),
                            modifier = Modifier.size(48.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    OutlinedButton(onClick = onTomarFoto, shape = RoundedCornerShape(50)) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(if (fotoUri == null) "Tomar foto" else "Repetir foto")
                    }
                    if (fotoUri != null) {
                        TextButton(onClick = onQuitarFoto) {
                            Text("Quitar foto", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }

            Spacer(Modifier.height(18.dp))
            
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = arete,
                        onValueChange = onAreteChange,
                        label = { Text("Arete") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(Modifier.width(8.dp))
                    Button(
                        onClick = onEscanearClick,
                        modifier = Modifier
                            .height(56.dp)
                            .width(80.dp),
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
                Text(
                    text = "Escribe o escanea con el lector de código de barras",
                    style = MaterialTheme.typography.bodySmall,
                    color = RanchoTextSecondary,
                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                )
            }

            Spacer(Modifier.height(14.dp))
            ExposedDropdownMenuBox(expanded = colorMenuAbierto, onExpandedChange = { colorMenuAbierto = it }) {
                OutlinedTextField(
                    value = color,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Color") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = colorMenuAbierto) }
                )
                ExposedDropdownMenu(expanded = colorMenuAbierto, onDismissRequest = { colorMenuAbierto = false }) {
                    opcionesColor.forEach { opcion ->
                        DropdownMenuItem(
                            text = { Text(opcion) },
                            onClick = { 
                                onColorChange(opcion)
                                colorMenuAbierto = false 
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.height(14.dp))

            ExposedDropdownMenuBox(expanded = sexoMenuAbierto, onExpandedChange = { sexoMenuAbierto = it }) {
                OutlinedTextField(
                    value = if (sexo == Sexo.MACHO) "Macho" else "Hembra",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Sexo") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = sexoMenuAbierto) }
                )
                ExposedDropdownMenu(expanded = sexoMenuAbierto, onDismissRequest = { sexoMenuAbierto = false }) {
                    DropdownMenuItem(text = { Text("Macho") }, onClick = { onSexoChange(Sexo.MACHO); sexoMenuAbierto = false })
                    DropdownMenuItem(text = { Text("Hembra") }, onClick = { onSexoChange(Sexo.HEMBRA); sexoMenuAbierto = false })
                }
            }

            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = peso,
                onValueChange = onPesoChange,
                label = { Text("Peso (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = precio,
                onValueChange = onPrecioChange,
                label = { Text("Precio de compra") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(Modifier.height(14.dp))
            OutlinedTextField(
                value = proveedor,
                onValueChange = onProveedorChange,
                label = { Text("Proveedor") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onGuardar,
                enabled = !guardando,
                modifier = Modifier.fillMaxWidth().height(52.dp)
            ) { Text(if (guardando) "Guardando..." else "Guardar") }

            Spacer(Modifier.height(32.dp))
        }

        if (exito) {
            SuccessDialog(
                title = "¡Registro Exitoso!",
                message = if (isAdmin) 
                    "La operación fue realizada con éxito."
                else 
                    "La operación fue realizada con éxito y queda en espera de revisión por un administrador.",
                onContinuar = onContinuar,
                onSalir = onBack,
                continuarLabel = "Continuar"
            )
        }
    }
}


private fun crearUriParaFoto(context: Context): Uri {
    val carpeta = File(context.cacheDir, "fotos_ganado").apply { if (!exists()) mkdirs() }
    val archivo = File.createTempFile("animal_", ".jpg", carpeta)
    // Usar la autoridad real del paquete en tiempo de ejecución (RF-FIX)
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, archivo)
}

@Preview(showBackground = true)
@Composable
fun RegistrarAnimalPreview() {
    MaterialTheme {
        RegistrarAnimalContent(
            arete = "1234",
            onAreteChange = {},
            color = "Negro",
            onColorChange = {},
            sexo = Sexo.MACHO,
            onSexoChange = {},
            peso = "450",
            onPesoChange = {},
            precio = "15000",
            onPrecioChange = {},
            proveedor = "Rancho X",
            onProveedorChange = {},
            fotoUri = null,
            onTomarFoto = {},
            onQuitarFoto = {},
            onEscanearClick = {},
            error = null,
            exito = false,
            guardando = false,
            isAdmin = true,
            onGuardar = {},
            onContinuar = {},
            onBack = {}
        )
    }
}
