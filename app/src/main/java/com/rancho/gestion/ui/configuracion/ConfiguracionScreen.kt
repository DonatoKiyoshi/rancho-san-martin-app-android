package com.rancho.gestion.ui.configuracion

import androidx.compose.animation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.RangoPeso
import com.rancho.gestion.data.repository.ResultadoOperacion
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.components.InfoDialog
import com.rancho.gestion.ui.components.MenuOptionCard
import com.rancho.gestion.ui.components.SectionCard
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff

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

    ConfiguracionContent(
        rangos = rangos,
        isAdmin = isAdmin,
        idUsuarioActual = usuario?.idUsuario ?: -1L,
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
                container.authRepository.cambiarContrasena(user, actual, nueva)
            } else false
        }
    )
}

@Composable
private fun ConfiguracionContent(
    rangos: List<RangoPeso>,
    isAdmin: Boolean,
    idUsuarioActual: Long,
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
    val scope = rememberCoroutineScope()
    
    // Estado para controlar qué sección está expandida
    var seccionExpandida by remember { mutableStateOf<String?>(null) } // "rangos" o "password"

    Scaffold(topBar = { HomeTopBar(title = "Configuración", onHomeClick = onOpenDrawer) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(horizontal = 20.dp)
                .verticalScrollFix()
        ) {
            if (isAdmin) {
                MenuOptionCard(
                    label = "Administración de Usuarios",
                    subtitle = "Alta, edición, baja y restablecimiento de contraseña de Empleados",
                    icon = Icons.Default.Group,
                    onClick = onNavigateToUsuarios
                )
                Spacer(Modifier.height(16.dp))
            }

            if (isAdmin) {
                // SECCIÓN: Rangos de Clasificación
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
                                    IconButton(onClick = { onEliminarRango(rango) }, modifier = Modifier.size(24.dp)) {
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
            }

            if (idUsuarioActual != 1L) {
                // SECCIÓN: Cambiar Contraseña
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

@Composable
private fun Modifier.verticalScrollFix(): Modifier {
    val scrollState = rememberScrollState()
    return this.verticalScroll(scrollState)
}

@Preview(showBackground = true)
@Composable
fun ConfiguracionPreview() {
    MaterialTheme {
        ConfiguracionContent(
            rangos = listOf(
                RangoPeso(idRangoPeso = 1, limiteInferior = 130.0, limiteSuperior = 220.0, nombreRango = "Becerro")
            ),
            isAdmin = true,
            idUsuarioActual = 1L,
            onOpenDrawer = {},
            onAgregarRango = { _, _, _ -> ResultadoOperacion.Ok },
            onEliminarRango = {},
            onChangePassword = { _, _ -> true },
            onNavigateToUsuarios = {}
        )
    }
}
