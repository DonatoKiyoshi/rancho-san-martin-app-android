package com.rancho.gestion.ui.ganado

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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
import androidx.compose.ui.window.Dialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.net.toUri
import coil.compose.AsyncImage
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.Sexo
import com.rancho.gestion.data.entities.TipoArete
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.BarcodeScannerScreen
import com.rancho.gestion.ui.components.SuccessDialog
import com.rancho.gestion.ui.components.ThousandsSeparatorVisualTransformation
import com.rancho.gestion.ui.theme.RanchoPrimary
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
    var tipoArete by rememberSaveable { mutableStateOf(TipoArete.SADER) }
    var arete by rememberSaveable { mutableStateOf("") }
    var color by rememberSaveable { mutableStateOf("") }
    var sexo by rememberSaveable { mutableStateOf(Sexo.MACHO) }
    var peso by rememberSaveable { mutableStateOf("") }
    var precio by rememberSaveable { mutableStateOf("") }
    var proveedor by rememberSaveable { mutableStateOf("") }
    
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
                    arete = result.filter { it.isDigit() }.take(15)
                    mostrarScanner = false
                },
                onBack = { mostrarScanner = false }
            )
        }
    } else {
        RegistrarAnimalContent(
            tipoArete = tipoArete,
            onTipoAreteChange = { 
                tipoArete = it
                arete = ""
                error = null
            },
            arete = arete,
            onAreteChange = { input ->
                val digitsOnly = input.filter { it.isDigit() }
                val maxLen = if (tipoArete == TipoArete.SADER) 15 else 6
                arete = digitsOnly.take(maxLen)
                error = null
            },
            color = color,
            onColorChange = { color = it },
            sexo = sexo,
            onSexoChange = { sexo = it },
            peso = peso,
            onPesoChange = { input ->
                val filtered = input.replace(',', '.')
                if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                    val parts = filtered.split('.')
                    val isDeleting = filtered.length < peso.length
                    if (isDeleting || parts[0].length <= 4) {
                        if (parts.size == 1 || parts[1].length <= 2) {
                            peso = filtered
                            error = null
                        }
                    }
                }
            },
            precio = precio,
            onPrecioChange = { input ->
                val filtered = input.replace(',', '.')
                if (filtered.all { it.isDigit() || it == '.' } && filtered.count { it == '.' } <= 1) {
                    val parts = filtered.split('.')
                    val isDeleting = filtered.length < precio.length
                    if (isDeleting || parts[0].length <= 6) {
                        if (parts.size == 1 || parts[1].length <= 2) {
                            precio = filtered
                            error = null
                        }
                    }
                }
            },
            proveedor = proveedor,
            onProveedorChange = { input ->
                if (input.length <= 50) {
                    val allowedChars = Regex("[a-zA-Z0-9 ñÑáéíóúÁÉÍÓÚ\\-_]*")
                    if (allowedChars.matches(input)) {
                        proveedor = input
                    }
                }
            },
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
                    tipoArete == TipoArete.SADER && arete.length != 15 -> error = "El arete SADER debe tener exactamente 15 dígitos"
                    tipoArete == TipoArete.OTRO && arete.length > 6 -> error = "El arete OTRO no puede exceder 6 dígitos"
                    pesoD == null || pesoD < 40 || pesoD > 1000 -> error = "El peso debe estar entre 40 y 1,000 kg"
                    precioD == null || precioD < 400 || precioD > 100000 -> error = "El precio debe estar entre 400 y 100,000"
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
                                tipoArete = tipoArete,
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
    tipoArete: TipoArete,
    onTipoAreteChange: (TipoArete) -> Unit,
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
    val opcionesColor = listOf("Negro", "Hosco", "Ballo", "Blanco", "Careto", "Sardo", "Pinto")

    // Colores y animaciones fluidas según el tipo de arete seleccionado
    val targetBgColor = if (tipoArete == TipoArete.SADER) Color(0xFFFFD54F) else Color(0xFF80CBC4)
    val animatedBgColor by animateColorAsState(
        targetValue = targetBgColor,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessMediumLow
        ),
        label = "segmentColor"
    )

    val buttonBgColor by animateColorAsState(
        targetValue = if (tipoArete == TipoArete.SADER) MaterialTheme.colorScheme.primary else Color(0xFF14B8A6),
        animationSpec = tween(durationMillis = 300, easing = FastOutSlowInEasing),
        label = "buttonColor"
    )

    val saderTextColor by animateColorAsState(
        targetValue = if (tipoArete == TipoArete.SADER) Color(0xFF1B364B) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 250),
        label = "saderTextColor"
    )

    val otroTextColor by animateColorAsState(
        targetValue = if (tipoArete == TipoArete.OTRO) Color(0xFF1B364B) else MaterialTheme.colorScheme.onSurfaceVariant,
        animationSpec = tween(durationMillis = 250),
        label = "otroTextColor"
    )

    Scaffold(topBar = { BackTopBar(title = "Registrar Animal", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.background)
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(12.dp))

            // SECCIÓN FOTOGRAFÍA
            Text("Fotografía", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .size(80.dp)
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
                            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
                            modifier = Modifier.size(52.dp)
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column {
                    OutlinedButton(
                        onClick = onTomarFoto,
                        shape = RoundedCornerShape(50),
                        border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                    ) {
                        Icon(Icons.Default.PhotoCamera, contentDescription = null, modifier = Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurface)
                        Spacer(Modifier.width(8.dp))
                        Text(if (fotoUri == null) "Tomar foto" else "Repetir foto", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
                    }
                    if (fotoUri != null) {
                        TextButton(onClick = onQuitarFoto) {
                            Text("Quitar foto", color = MaterialTheme.colorScheme.error, fontSize = 12.sp)
                        }
                    }
                }
            }

            Spacer(Modifier.height(20.dp))

            // SECCIÓN TIPO DE ARETE - CONTROL SEGMENTADO ANIMADO Y FLUIDO
            Text("Tipo de Arete", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))

            Surface(
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
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
                                .clickable { onTipoAreteChange(TipoArete.SADER) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "SADER",
                                color = saderTextColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .clip(RoundedCornerShape(50))
                                .clickable { onTipoAreteChange(TipoArete.OTRO) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "OTRO",
                                color = otroTextColor,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(14.dp))
            
            // CAMPO ARETE + BOTÓN ESCANEAR INTEGRADO
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = arete,
                        onValueChange = onAreteChange,
                        label = { Text(if (tipoArete == TipoArete.SADER) "Arete SADER" else "Arete OTRO") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    )
                    if (tipoArete == TipoArete.SADER) {
                        Spacer(Modifier.width(8.dp))
                        Button(
                            onClick = onEscanearClick,
                            modifier = Modifier
                                .height(56.dp)
                                .width(80.dp),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = buttonBgColor),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.ViewWeek, contentDescription = null)
                                Text("Escanear", fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                }
                Text(
                    text = if (tipoArete == TipoArete.SADER) 
                        "Escribe o escanea el código de barras"
                    else 
                        "Ingresa máximo 6 dígitos numéricos",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                )
            }

            Spacer(Modifier.height(14.dp))

            // DROPDOWN COLOR
            ExposedDropdownMenuBox(expanded = colorMenuAbierto, onExpandedChange = { colorMenuAbierto = it }) {
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

            // DROPDOWN SEXO
            ExposedDropdownMenuBox(expanded = sexoMenuAbierto, onExpandedChange = { sexoMenuAbierto = it }) {
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
                    DropdownMenuItem(text = { Text("Macho") }, onClick = { onSexoChange(Sexo.MACHO); sexoMenuAbierto = false })
                    DropdownMenuItem(text = { Text("Hembra") }, onClick = { onSexoChange(Sexo.HEMBRA); sexoMenuAbierto = false })
                }
            }

            Spacer(Modifier.height(14.dp))

            // CAMPO PESO
            OutlinedTextField(
                value = peso,
                onValueChange = onPesoChange,
                label = { Text("Peso (kg)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = ThousandsSeparatorVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // CAMPO PRECIO COMPRA
            OutlinedTextField(
                value = precio,
                onValueChange = onPrecioChange,
                label = { Text("Precio de compra") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                visualTransformation = ThousandsSeparatorVisualTransformation(),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(Modifier.height(14.dp))

            // CAMPO PROVEEDOR
            OutlinedTextField(
                value = proveedor,
                onValueChange = onProveedorChange,
                label = { Text("Proveedor") },
                singleLine = true,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            )

            error?.let {
                Spacer(Modifier.height(10.dp))
                Text(it, color = MaterialTheme.colorScheme.error)
            }

            Spacer(Modifier.height(28.dp))

            // BOTÓN GUARDAR
            Button(
                onClick = onGuardar,
                enabled = !guardando,
                shape = RoundedCornerShape(50),
                colors = ButtonDefaults.buttonColors(containerColor = buttonBgColor),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            ) {
                Text(if (guardando) "Guardando..." else "Guardar", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

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
    val authority = "${context.packageName}.fileprovider"
    return FileProvider.getUriForFile(context, authority, archivo)
}

@Preview(showBackground = true)
@Composable
fun RegistrarAnimalPreview() {
    MaterialTheme {
        RegistrarAnimalContent(
            tipoArete = TipoArete.SADER,
            onTipoAreteChange = {},
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
