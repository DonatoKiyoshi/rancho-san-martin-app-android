package com.rancho.gestion.ui.configuracion

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NightsStay
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material.icons.filled.WbSunny
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.components.InfoDialog
import com.rancho.gestion.ui.components.MenuOptionCard
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.SanMartinOrange
import com.rancho.gestion.util.AppThemeMode
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch

@Composable
fun ConfiguracionScreen(
    container: AppContainer, 
    onOpenDrawer: () -> Unit,
    onNavigateToUsuarios: () -> Unit = {}
) {
    val rangos by container.configuracionRepository.rangos().collectAsState(initial = emptyList())
    val isAdmin = SessionManager.isAdmin()
    val scope = rememberCoroutineScope()
    val usuario by SessionManager.usuarioActual.collectAsState()

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAuthManager = container.biometricAuthManager
    val themeManager = container.themeManager

    var biometricEnabled by remember { mutableStateOf(biometricAuthManager.isBiometricEnabled()) }
    val biometricAvailable = remember { biometricAuthManager.isBiometricAvailable() }
    val currentThemeMode by themeManager.themeMode.collectAsState()

    // Estados para dialogo de confirmacion del switch biometrico
    var mostrarDialogoConfirmacionBiometria by remember { mutableStateOf(false) }
    var estadoBiometricoDeseado by remember { mutableStateOf(false) }

    ConfiguracionContent(
        rangos = rangos,
        isAdmin = isAdmin,
        idUsuarioActual = usuario?.idUsuario ?: "",
        biometricAvailable = biometricAvailable,
        biometricEnabled = biometricEnabled,
        currentThemeMode = currentThemeMode,
        onThemeModeChange = { newMode -> themeManager.setThemeMode(newMode) },
        onRequestToggleBiometric = { estadoDeseado ->
            estadoBiometricoDeseado = estadoDeseado
            mostrarDialogoConfirmacionBiometria = true
        },
        onOpenDrawer = onOpenDrawer,
        onNavigateToUsuarios = onNavigateToUsuarios,
        onAgregarRango = { nombre, min, max ->
            container.configuracionRepository.agregarRango(nombre, min, max)
        },
        onEliminarRango = { rango ->
            scope.launch { container.configuracionRepository.eliminarRango(rango) }
        },
        onChangePassword = { actual, nueva ->
            val user = usuario
            if (user != null) {
                val ok = container.authRepository.cambiarContrasena(user, actual, nueva)
                if (ok && biometricAuthManager.isBiometricEnabled()) {
                    val userActualizado = container.authRepository.getUsuarioById(user.idUsuario)
                    if (userActualizado != null) {
                        biometricAuthManager.saveCredentials(userActualizado.nombreUsuario, userActualizado.passwordHash)
                    }
                }
                ok
            } else false
        }
    )

    // Diálogo de confirmación antes de activar o desactivar la biometría
    if (mostrarDialogoConfirmacionBiometria) {
        val esActivar = estadoBiometricoDeseado
        AlertDialog(
            onDismissRequest = { mostrarDialogoConfirmacionBiometria = false },
            title = {
                Text(if (esActivar) "¿Activar acceso biométrico?" else "¿Desactivar acceso biométrico?")
            },
            text = {
                Text(
                    if (esActivar) {
                        "¿Deseas activar el inicio de sesión con tu huella dactilar o reconocimiento facial? Tus credenciales se guardarán de forma segura en este dispositivo."
                    } else {
                        "¿Deseas desactivar el inicio de sesión biométrico? Se eliminarán las credenciales guardadas en este dispositivo y tendrás que ingresar tu usuario y contraseña manualmente."
                    }
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        mostrarDialogoConfirmacionBiometria = false
                        if (esActivar) {
                            val user = usuario
                            if (user != null && activity != null) {
                                biometricAuthManager.autenticar(
                                    activity = activity,
                                    title = "Confirmar Activación Biométrica",
                                    subtitle = "Escanea tu huella o rostro para habilitar la opción",
                                    onSuccess = {
                                        biometricAuthManager.saveCredentials(user.nombreUsuario, user.passwordHash)
                                        biometricEnabled = true
                                    }
                                )
                            } else if (user != null) {
                                biometricAuthManager.saveCredentials(user.nombreUsuario, user.passwordHash)
                                biometricEnabled = true
                            }
                        } else {
                            biometricAuthManager.clearCredentials()
                            biometricEnabled = false
                        }
                    }
                ) {
                    Text(
                        if (esActivar) "Sí, activar" else "Sí, desactivar",
                        color = if (esActivar) SanMartinOrange else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { mostrarDialogoConfirmacionBiometria = false }) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@Composable
private fun ConfiguracionContent(
    rangos: List<RangoPeso>,
    isAdmin: Boolean,
    idUsuarioActual: String,
    biometricAvailable: Boolean,
    biometricEnabled: Boolean,
    currentThemeMode: AppThemeMode,
    onThemeModeChange: (AppThemeMode) -> Unit,
    onRequestToggleBiometric: (Boolean) -> Unit,
    onOpenDrawer: () -> Unit,
    onNavigateToUsuarios: () -> Unit,
    onAgregarRango: suspend (String, Double, Double) -> ResultadoOperacion,
    onEliminarRango: (RangoPeso) -> Unit,
    onChangePassword: suspend (String, String) -> Boolean
) {
    var nombreRango by remember { mutableStateOf("") }
    var nuevoMin by remember { mutableStateOf("") }
    var nuevoMax by remember { mutableStateOf("") }
    var errorRango by remember { mutableStateOf<String?>(null) }
    var mostrarAlertaLimiteRangos by remember { mutableStateOf(false) }
    var rangoAEliminar by remember { mutableStateOf<RangoPeso?>(null) }
    val scope = rememberCoroutineScope()
    
    var seccionExpandida by remember { mutableStateOf<String?>(null) }

    Scaffold(topBar = { HomeTopBar(title = "Configuración", onHomeClick = onOpenDrawer) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .imePadding()
                .verticalScroll(rememberScrollState())
        ) {
            if (isAdmin) {
                // 1. SECCIÓN: Rangos de Clasificación
                MenuOptionCard(
                    label = "Rangos de Clasificación",
                    subtitle = "Gestionar categorías de clasificación por peso",
                    icon = Icons.Default.Add,
                    onClick = {
                        seccionExpandida = if (seccionExpandida == "rangos") null else "rangos"
                    }
                )
                
                AnimatedVisibility(
                    visible = seccionExpandida == "rangos",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(Modifier.padding(top = 12.dp)) {
                        SectionCard {
                            Text("Configuración de Rangos", fontWeight = FontWeight.Bold, color = RanchoPrimary)
                            Spacer(Modifier.height(14.dp))
                            rangos.forEach { rango ->
                                Row(
                                    Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("${rango.nombreRango}: ${rango.limiteInferior.toInt()} a ${rango.limiteSuperior.toInt()} kg", style = MaterialTheme.typography.bodyMedium)
                                    IconButton(
                                        onClick = { rangoAEliminar = rango },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(Icons.Default.Close, contentDescription = "Eliminar", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                                    }
                                }
                                HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                            }

                            Spacer(Modifier.height(10.dp))
                            OutlinedTextField(
                                value = nombreRango,
                                onValueChange = { input ->
                                    val filtered = input.filter { 
                                        it.isLetterOrDigit() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || 
                                        "áéíóúÁÉÍÓÚ-_".contains(it) 
                                    }
                                    if (filtered.length <= 20) nombreRango = filtered
                                },
                                label = { Text("Nombre (ej. Becerro)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                OutlinedTextField(
                                    value = nuevoMin,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }
                                        val num = digits.toIntOrNull() ?: 0
                                        if (num <= 1000) nuevoMin = digits
                                    },
                                    label = { Text("Min") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text("a")
                                Spacer(Modifier.width(8.dp))
                                OutlinedTextField(
                                    value = nuevoMax,
                                    onValueChange = { input ->
                                        val digits = input.filter { it.isDigit() }
                                        val num = digits.toIntOrNull() ?: 0
                                        if (num <= 1000) nuevoMax = digits
                                    },
                                    label = { Text("Max") },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            errorRango?.let {
                                Spacer(Modifier.height(6.dp))
                                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
                            }
                            Spacer(Modifier.height(14.dp))
                            Button(
                                onClick = {
                                    val min = nuevoMin.toDoubleOrNull()
                                    val max = nuevoMax.toDoubleOrNull()
                                    val nombreTrim = nombreRango.trim()
                                    
                                    if (rangos.size >= 20) {
                                        mostrarAlertaLimiteRangos = true
                                        return@Button
                                    }

                                    when {
                                        nombreTrim.isBlank() -> errorRango = "El nombre es obligatorio"
                                        rangos.any { it.nombreRango.equals(nombreTrim, ignoreCase = true) } -> {
                                            errorRango = "Ya existe un rango con el nombre '$nombreTrim'"
                                        }
                                        min == null || max == null -> errorRango = "Ingresa valores numéricos válidos"
                                        min < 0 || max > 1000 -> errorRango = "El peso debe estar entre 0 y 1,000 kg"
                                        min >= max -> errorRango = "El mínimo debe ser menor al máximo"
                                        else -> {
                                            scope.launch {
                                                val resultado = onAgregarRango(nombreTrim, min, max)
                                                when (resultado) {
                                                    is ResultadoOperacion.Ok -> {
                                                        errorRango = null; nuevoMin = ""; nuevoMax = ""; nombreRango = ""
                                                    }
                                                    is ResultadoOperacion.Error -> errorRango = resultado.mensaje
                                                }
                                            }
                                        }
                                    }
                                },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Icon(Icons.Default.Add, contentDescription = null)
                                Spacer(Modifier.width(4.dp))
                                Text("Agregar Rango")
                            }
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))

                // 2. SECCIÓN: Administración de Usuarios
                MenuOptionCard(
                    label = "Administración de Usuarios",
                    subtitle = "Alta, edición, baja y restablecimiento de contraseña de Empleados",
                    icon = Icons.Default.Group,
                    onClick = onNavigateToUsuarios
                )
                Spacer(Modifier.height(16.dp))
            }

            // 3. SECCIÓN: Tema de la App
            SectionCard {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // 1. TÍTULO Y ÍCONO (Arriba)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = null,
                                tint = SanMartinOrange,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Text(
                            text = "Tema de la app",
                            fontWeight = FontWeight.Bold,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(Modifier.height(14.dp))

                    // 2. BOTONES PARA CAMBIAR TEMA (Abajo del título)
                    Surface(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.fillMaxWidth().height(42.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Opción Claro
                            val isClaro = currentThemeMode == AppThemeMode.CLARO
                            val claroBg by animateColorAsState(
                                targetValue = if (isClaro) SanMartinOrange else Color.Transparent,
                                animationSpec = tween(250),
                                label = "claroBg"
                            )
                            val claroContent by animateColorAsState(
                                targetValue = if (isClaro) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(250),
                                label = "claroTxt"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(claroBg)
                                    .clickable { onThemeModeChange(AppThemeMode.CLARO) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.WbSunny,
                                        contentDescription = "Claro",
                                        tint = claroContent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Claro",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = claroContent
                                    )
                                }
                            }

                            // Opción Oscuro
                            val isOscuro = currentThemeMode == AppThemeMode.OSCURO
                            val oscuroBg by animateColorAsState(
                                targetValue = if (isOscuro) SanMartinOrange else Color.Transparent,
                                animationSpec = tween(250),
                                label = "oscuroBg"
                            )
                            val oscuroContent by animateColorAsState(
                                targetValue = if (isOscuro) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                                animationSpec = tween(250),
                                label = "oscuroTxt"
                            )

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(50))
                                    .background(oscuroBg)
                                    .clickable { onThemeModeChange(AppThemeMode.OSCURO) },
                                contentAlignment = Alignment.Center
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = Icons.Default.NightsStay,
                                        contentDescription = "Oscuro",
                                        tint = oscuroContent,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(Modifier.width(6.dp))
                                    Text(
                                        text = "Oscuro",
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = oscuroContent
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(10.dp))

                    // 3. DESCRIPCIÓN (Al último)
                    Text(
                        text = "Cambia el aspecto visual de la aplicación",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Spacer(Modifier.height(16.dp))

            // 4. SECCIÓN: Inicio de Sesión Biométrico (Huella / Rostro)
            if (biometricAvailable) {
                SectionCard {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onRequestToggleBiometric(!biometricEnabled) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Fingerprint,
                                contentDescription = null,
                                tint = SanMartinOrange,
                                modifier = Modifier.size(28.dp)
                            )
                            Spacer(Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "Inicio de Sesión Biométrico",
                                    fontWeight = FontWeight.Bold,
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "Accede con tu huella dactilar o reconocimiento facial registrado en tu dispositivo",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Spacer(Modifier.width(8.dp))
                        Switch(
                            checked = biometricEnabled,
                            onCheckedChange = { nuevoEstado ->
                                onRequestToggleBiometric(nuevoEstado)
                            },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                                checkedTrackColor = SanMartinOrange
                            )
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            if (idUsuarioActual != "1") {
                // 5. SECCIÓN: Cambiar Contraseña
                MenuOptionCard(
                    label = "Cambiar Contraseña",
                    subtitle = "Actualiza tus credenciales de acceso",
                    icon = Icons.Default.Lock,
                    onClick = {
                        seccionExpandida = if (seccionExpandida == "password") null else "password"
                    }
                )

                AnimatedVisibility(
                    visible = seccionExpandida == "password",
                    enter = expandVertically() + fadeIn(),
                    exit = shrinkVertically() + fadeOut()
                ) {
                    Column(Modifier.padding(top = 12.dp)) {
                        CambiarContrasenaCard(onChangePassword)
                    }
                }
            }
            
            Spacer(Modifier.height(32.dp))
        }
    }

    // Diálogo de confirmación para eliminar rango de clasificación
    if (rangoAEliminar != null) {
        val r = rangoAEliminar!!
        AlertDialog(
            onDismissRequest = { rangoAEliminar = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text("¿Eliminar Rango?", fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Text(
                    "¿Estás seguro de que deseas eliminar el rango ${r.nombreRango} de ${r.limiteInferior.toInt()} a ${r.limiteSuperior.toInt()} kg?"
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onEliminarRango(r)
                        rangoAEliminar = null
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text("Sí, eliminar", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { rangoAEliminar = null }) {
                    Text("Cancelar")
                }
            }
        )
    }

    if (mostrarAlertaLimiteRangos) {
        InfoDialog(
            title = "Límite Alcanzado",
            message = "El límite de 20 rangos de clasificación ya fue alcanzado. Debes eliminar alguno existente antes de agregar uno nuevo.",
            onDismiss = { mostrarAlertaLimiteRangos = false }
        )
    }
}

@Composable
private fun CambiarContrasenaCard(onChangePassword: suspend (String, String) -> Boolean) {
    var actual by remember { mutableStateOf("") }
    var nueva by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var mensaje by remember { mutableStateOf<String?>(null) }
    var esError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Lock, null, tint = RanchoPrimary, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(8.dp))
            Text("Cambiar Contraseña", fontWeight = FontWeight.Bold, color = RanchoPrimary)
        }
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = actual,
            onValueChange = { actual = it },
            label = { Text("Contraseña Actual") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                val icon = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff
                IconButton(onClick = { passwordVisible = !passwordVisible }) {
                    Icon(icon, contentDescription = null)
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = nueva,
            onValueChange = { nueva = it },
            label = { Text("Contraseña Nueva") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = confirmar,
            onValueChange = { confirmar = it },
            label = { Text("Confirmar Contraseña") },
            singleLine = true,
            visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        mensaje?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = if (esError) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
        }
        Spacer(Modifier.height(14.dp))
        Button(
            onClick = {
                when {
                    nueva.length < 6 -> { mensaje = "La contraseña debe tener al menos 6 caracteres"; esError = true }
                    nueva.length > 16 -> { mensaje = "La contraseña no debe exceder los 16 caracteres"; esError = true }
                    nueva != confirmar -> { mensaje = "Las contraseñas no coinciden"; esError = true }
                    else -> scope.launch {
                        val ok = onChangePassword(actual, nueva)
                        esError = !ok
                        mensaje = if (ok) "Contraseña actualizada correctamente." else "La contraseña actual es incorrecta."
                        if (ok) { actual = ""; nueva = ""; confirmar = "" }
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text("Cambiar") }
    }
}

@Preview(showBackground = true)
@Composable
fun ConfiguracionPreview() {
    MaterialTheme {
        ConfiguracionContent(
            rangos = listOf(
                RangoPeso(idRangoPeso = "1", limiteInferior = 130.0, limiteSuperior = 220.0, nombreRango = "Becerro")
            ),
            isAdmin = true,
            idUsuarioActual = "1",
            biometricAvailable = true,
            biometricEnabled = true,
            currentThemeMode = AppThemeMode.CLARO,
            onThemeModeChange = {},
            onRequestToggleBiometric = {},
            onOpenDrawer = {},
            onAgregarRango = { _, _, _ -> ResultadoOperacion.Ok },
            onEliminarRango = {},
            onChangePassword = { _, _ -> true },
            onNavigateToUsuarios = {}
        )
    }
}
