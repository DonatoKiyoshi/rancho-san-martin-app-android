package com.rancho.gestion.ui.ganado

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.Animal
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.components.ThousandsSeparatorVisualTransformation
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.ui.theme.RanchoTheme
import com.rancho.gestion.util.ImageOptimizer
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun DetalleAprobacionAnimalScreen(
    container: AppContainer,
    arete: String,
    onBack: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val animalState = produceState<Animal?>(initialValue = null, arete) {
        value = container.ganadoRepository.buscarPorArete(arete)
    }

    val animal = animalState.value

    val usuarioState = produceState<Usuario?>(initialValue = null, animal) {
        if (animal != null) {
            value = container.authRepository.getUsuarioById(animal.idUsuarioRegistro)
        }
    }

    if (animal == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator()
        }
    } else {
        DetalleAprobacionAnimalContent(
            animal = animal,
            usuarioRegistro = usuarioState.value,
            onBack = onBack,
            onAprobar = { updatedAnimal ->
                scope.launch {
                    container.ganadoRepository.aprobarAnimal(updatedAnimal, SessionManager.currentUserId())
                    onBack()
                }
            },
            onRechazar = { animalToRechazar ->
                scope.launch {
                    container.ganadoRepository.rechazarAnimal(animalToRechazar)
                    onBack()
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleAprobacionAnimalContent(
    animal: Animal,
    usuarioRegistro: Usuario?,
    onBack: () -> Unit,
    onAprobar: (Animal) -> Unit,
    onRechazar: (Animal) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var color by remember { mutableStateOf(animal.color) }
    var sexo by remember { mutableStateOf(animal.sexo) }
    var peso by remember { mutableStateOf(animal.pesoEntrada.toString()) }
    var precio by remember { mutableStateOf(animal.precioCompra.toString()) }
    var proveedor by remember { mutableStateOf(animal.proveedor) }

    var fotoUriStr by remember { mutableStateOf(animal.fotografiaLocal) }
    var pendingUriStr by remember { mutableStateOf<String?>(null) }
    var fotoFullScreen by remember { mutableStateOf(false) }

    val lanzadorCamara = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { exitoso ->
        if (exitoso && pendingUriStr != null) {
            scope.launch {
                val optimizedPath = withContext(Dispatchers.IO) {
                    ImageOptimizer.optimize(context, pendingUriStr!!.toUri())
                }
                fotoUriStr = optimizedPath
            }
        }
    }

    val lanzadorPermisoCamara = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { concedido ->
        if (concedido) {
            val uri = crearUriParaFoto(context)
            pendingUriStr = uri.toString()
            lanzadorCamara.launch(uri)
        }
    }

    fun abrirCamara() {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            val uri = crearUriParaFoto(context)
            pendingUriStr = uri.toString()
            lanzadorCamara.launch(uri)
        } else {
            lanzadorPermisoCamara.launch(Manifest.permission.CAMERA)
        }
    }

    var showConfirmAprobar by remember { mutableStateOf(false) }
    var showConfirmRechazar by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Scaffold(
        topBar = { BackTopBar(title = "Detalle de Aprobación", onBack = onBack) }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Fotografía
            Box(
                modifier = Modifier
                    .size(160.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .clickable { if (fotoUriStr != null) fotoFullScreen = true },
                contentAlignment = Alignment.Center
            ) {
                if (fotoUriStr != null) {
                    AsyncImage(
                        model = fotoUriStr,
                        contentDescription = "Foto del animal",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                    Box(
                        Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Fullscreen, null, tint = Color.White.copy(alpha = 0.8f), modifier = Modifier.size(32.dp))
                    }
                } else {
                    Icon(
                        imageVector = ImageVector.vectorResource(id = R.drawable.ic_vaca_placeholder),
                        contentDescription = null,
                        modifier = Modifier.size(80.dp),
                        tint = RanchoTextSecondary.copy(alpha = 0.2f)
                    )
                }
            }
            
            Spacer(Modifier.height(12.dp))
            OutlinedButton(onClick = { abrirCamara() }, shape = RoundedCornerShape(50)) {
                Icon(Icons.Default.PhotoCamera, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(if (fotoUriStr == null) "Tomar foto" else "Cambiar foto")
            }

            Spacer(Modifier.height(24.dp))

            SectionCard {
                Text("Información de Registro", fontWeight = FontWeight.Bold, color = RanchoPrimary)
                Spacer(Modifier.height(16.dp))

                OutlinedTextField(
                    value = animal.numeroArete,
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Número de Arete") },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = usuarioRegistro?.nombre ?: "ID: ${animal.idUsuarioRegistro}",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Usuario que registró") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        disabledTextColor = MaterialTheme.colorScheme.onSurface,
                        disabledLabelColor = MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    enabled = false
                )

                Spacer(Modifier.height(12.dp))

                var colorMenuAbierto by remember { mutableStateOf(false) }
                val opcionesColor = listOf("Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo")
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
                        opcionesColor.forEach { opt ->
                            DropdownMenuItem(text = { Text(opt) }, onClick = { color = opt; colorMenuAbierto = false })
                        }
                    }
                }

                Spacer(Modifier.height(12.dp))

                var sexoMenuAbierto by remember { mutableStateOf(false) }
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
                        DropdownMenuItem(text = { Text("Macho") }, onClick = { sexo = Sexo.MACHO; sexoMenuAbierto = false })
                        DropdownMenuItem(text = { Text("Hembra") }, onClick = { sexo = Sexo.HEMBRA; sexoMenuAbierto = false })
                    }
                }

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = peso,
                    onValueChange = { input ->
                        val filtered = input.replace(',', '.')
                        if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                            val parts = filtered.split('.')
                            val isDeleting = filtered.length < peso.length
                            if (isDeleting || parts[0].length <= 4) {
                                if (parts.size < 2 || parts[1].length <= 2) {
                                    peso = filtered
                                    error = null
                                }
                            }
                        }
                    },
                    label = { Text("Peso (kg)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = ThousandsSeparatorVisualTransformation()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = precio,
                    onValueChange = { input ->
                        val filtered = input.replace(',', '.')
                        if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                            val parts = filtered.split('.')
                            val isDeleting = filtered.length < precio.length
                            if (isDeleting || parts[0].length <= 6) {
                                if (parts.size < 2 || parts[1].length <= 2) {
                                    precio = filtered
                                    error = null
                                }
                            }
                        }
                    },
                    label = { Text("Precio de Compra ($)") },
                    modifier = Modifier.fillMaxWidth(),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    visualTransformation = ThousandsSeparatorVisualTransformation()
                )

                Spacer(Modifier.height(12.dp))

                OutlinedTextField(
                    value = proveedor,
                    onValueChange = { input ->
                        if (input.length <= 50) {
                            val allowedChars = Regex("[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚ\\-_]*")
                            if (allowedChars.matches(input)) {
                                proveedor = input
                                error = null
                            }
                        }
                    },
                    label = { Text("Proveedor") },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            error?.let {
                Spacer(Modifier.height(12.dp))
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            Spacer(Modifier.height(32.dp))

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = { showConfirmRechazar = true },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Rechazar")
                }
                Button(
                    onClick = {
                        val pesoD = peso.toDoubleOrNull()
                        val precioD = precio.toDoubleOrNull()
                        when {
                            pesoD == null || pesoD <= 0 -> error = "Ingresa un peso válido"
                            precioD == null || precioD < 0 -> error = "Ingresa un precio válido"
                            proveedor.isBlank() -> error = "El proveedor es obligatorio"
                            else -> showConfirmAprobar = true
                        }
                    },
                    modifier = Modifier.weight(1f).height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = RanchoPrimary)
                ) {
                    Text("Aceptar")
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }

    if (showConfirmAprobar) {
        AlertDialog(
            onDismissRequest = { showConfirmAprobar = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = RanchoPrimary) },
            title = { Text("Confirmar Aprobación") },
            text = { Text("¿Deseas aprobar la entrada del animal con arete ${animal.numeroArete} con los datos actuales?") },
            confirmButton = {
                Button(onClick = {
                    val updatedAnimal = animal.copy(
                        color = color,
                        sexo = sexo,
                        pesoEntrada = peso.toDouble(),
                        precioCompra = precio.toDouble(),
                        proveedor = proveedor,
                        fotografiaLocal = fotoUriStr
                    )
                    onAprobar(updatedAnimal)
                    showConfirmAprobar = false
                }) { Text("Aprobar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmAprobar = false }) { Text("Cancelar") }
            }
        )
    }

    if (showConfirmRechazar) {
        AlertDialog(
            onDismissRequest = { showConfirmRechazar = false },
            icon = { Icon(Icons.Default.Info, contentDescription = null, modifier = Modifier.size(48.dp), tint = MaterialTheme.colorScheme.error) },
            title = { Text("Confirmar Rechazo") },
            text = { Text("¿Estás seguro de rechazar esta entrada? La información será eliminada permanentemente.") },
            confirmButton = {
                Button(
                    onClick = {
                        onRechazar(animal)
                        showConfirmRechazar = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { Text("Rechazar") }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmRechazar = false }) { Text("Cancelar") }
            }
        )
    }

    if (fotoFullScreen && fotoUriStr != null) {
        FullScreenImageDialog(
            uriStr = fotoUriStr!!,
            onDismiss = { fotoFullScreen = false },
            onRetake = {
                fotoFullScreen = false
                abrirCamara()
            }
        )
    }
}

private fun crearUriParaFoto(context: Context): Uri {
    val carpeta = File(context.cacheDir, "fotos_ganado").apply { if (!exists()) mkdirs() }
    val archivo = File.createTempFile("animal_", ".jpg", carpeta)
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, archivo)
}

@Preview(showBackground = true)
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleAprobacionAnimalPreview() {
    val dummyAnimal = Animal(
        numeroArete = "484123456",
        color = "Negro",
        sexo = Sexo.MACHO,
        pesoEntrada = 450.0,
        precioCompra = 15000.0,
        proveedor = "Rancho San Martín",
        idRangoPeso = null,
        idUsuarioRegistro = 1
    )
    val dummyUsuario = Usuario(
        idUsuario = 1,
        nombre = "Juan Pérez",
        nombreUsuario = "juanp",
        passwordHash = "",
        rol = com.rancho.gestion.data.entities.Rol.EMPLEADO
    )
    RanchoTheme {
        DetalleAprobacionAnimalContent(
            animal = dummyAnimal,
            usuarioRegistro = dummyUsuario,
            onBack = {},
            onAprobar = {},
            onRechazar = {}
        )
    }
}

