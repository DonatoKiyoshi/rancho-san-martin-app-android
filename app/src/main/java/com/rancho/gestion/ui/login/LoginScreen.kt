package com.rancho.gestion.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.ui.theme.SanMartinCream
import com.rancho.gestion.ui.theme.SanMartinOrange
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(container: AppContainer, onLoginSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }

    // Estados para dialogo de activacion biométrica tras login exitoso
    var usuarioPendienteLogin by remember { mutableStateOf<Usuario?>(null) }
    var mostrarDialogoBiometria by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val biometricAuthManager = container.biometricAuthManager
    val scope = rememberCoroutineScope()

    val biometricAvailable = remember { biometricAuthManager.isBiometricAvailable() }
    val biometricEnabled = remember { biometricAuthManager.isBiometricEnabled() }

    fun ejecutarLoginBiometrico() {
        if (activity == null) return
        val savedUsername = biometricAuthManager.getSavedUsername() ?: return

        biometricAuthManager.autenticar(
            activity = activity,
            title = "Inicio de Sesión Biométrico",
            subtitle = "Escanea tu huella o rostro para ingresar",
            onSuccess = {
                scope.launch {
                    val userEncontrado = container.authRepository.getUsuarioByUsername(savedUsername)

                    if (userEncontrado != null) {
                        // Sincronizamos la credencial cifrada con la cuenta
                        biometricAuthManager.saveCredentials(userEncontrado.nombreUsuario, userEncontrado.passwordHash)
                        SessionManager.login(userEncontrado)
                        kotlinx.coroutines.delay(150) // Permite que el diálogo nativo de BiometricPrompt se cierre limpiamente
                        onLoginSuccess()
                    } else {
                        error = "No se pudo recuperar la sesión biométrica. Inicia sesión manualmente."
                        biometricAuthManager.clearCredentials()
                    }
                }
            },
            onError = { msg ->
                error = msg
            }
        )
    }

    // Si la biometría está habilitada, intentamos sugerir el login biométrico automáticamente al abrir
    LaunchedEffect(Unit) {
        if (biometricAvailable && biometricEnabled && biometricAuthManager.getSavedUsername() != null) {
            ejecutarLoginBiometrico()
        }
    }

    fun completarLoginExitoso(usuario: Usuario) {
        if (biometricAvailable && !biometricAuthManager.hasBeenAskedBiometric()) {
            usuarioPendienteLogin = usuario
            mostrarDialogoBiometria = true
        } else {
            SessionManager.login(usuario)
            onLoginSuccess()
        }
    }

    fun intentarLoginManual() {
        if (username.isBlank() || password.isBlank()) {
            error = "Ingresa usuario y contraseña"
            return
        }
        loading = true
        scope.launch {
            val usuario = container.authRepository.login(username, password)
            loading = false
            if (usuario != null) {
                completarLoginExitoso(usuario)
            } else {
                error = "Usuario o contraseña incorrectos"
            }
        }
    }

    // Diálogo de confirmación al iniciar sesión por primera vez
    if (mostrarDialogoBiometria && usuarioPendienteLogin != null) {
        AlertDialog(
            onDismissRequest = {
                val user = usuarioPendienteLogin!!
                biometricAuthManager.markAskedBiometric(false)
                mostrarDialogoBiometria = false
                SessionManager.login(user)
                onLoginSuccess()
            },
            title = { Text("¿Activar inicio de sesión biométrico?") },
            text = {
                Text(
                    "¿Deseas guardar tus credenciales de acceso de forma segura para ingresar fácilmente usando tu huella dactilar o reconocimiento facial en tus próximos ingresos?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val user = usuarioPendienteLogin!!
                        biometricAuthManager.saveCredentials(user.nombreUsuario, user.passwordHash)
                        mostrarDialogoBiometria = false
                        SessionManager.login(user)
                        onLoginSuccess()
                    }
                ) {
                    Text("Sí, activar", color = SanMartinOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val user = usuarioPendienteLogin!!
                        biometricAuthManager.markAskedBiometric(false)
                        mostrarDialogoBiometria = false
                        SessionManager.login(user)
                        onLoginSuccess()
                    }
                ) {
                    Text("Ahora no")
                }
            }
        )
    }

    LoginContent(
        username = username,
        onUsernameChange = { username = it; error = null },
        password = password,
        onPasswordChange = { password = it; error = null },
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        error = error,
        loading = loading,
        showBiometricButton = biometricAvailable && biometricEnabled && biometricAuthManager.getSavedUsername() != null,
        onBiometricClick = { ejecutarLoginBiometrico() },
        onLoginClick = { intentarLoginManual() }
    )
}

@Composable
private fun LoginContent(
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    error: String?,
    loading: Boolean,
    showBiometricButton: Boolean,
    onBiometricClick: () -> Unit,
    onLoginClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    val bgColor = Color(0xFF1A1C1E)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = bgColor
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 36.dp)
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(scrollState),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(50.dp))

            // PALABRA RANCHO SUPERIOR
            Text(
                text = "RANCHO",
                color = SanMartinCream,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // LOGO ORIGINAL
            Image(
                painter = painterResource(id = R.drawable.logotransparente),
                contentDescription = "Logo Rancho San Martín",
                modifier = Modifier.size(width = 280.dp, height = 180.dp),
                contentScale = ContentScale.Fit
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier.padding(top = 8.dp)
            ) {
                Box(Modifier.width(30.dp).height(1.dp).background(SanMartinCream.copy(alpha = 0.6f)))
                Spacer(Modifier.width(12.dp))
                Text(
                    text = "SAN MARTÍN",
                    color = SanMartinCream,
                    fontWeight = FontWeight.ExtraBold,
                    fontSize = 24.sp,
                    letterSpacing = 2.sp
                )
                Spacer(Modifier.width(12.dp))
                Box(Modifier.width(30.dp).height(1.dp).background(SanMartinCream.copy(alpha = 0.6f)))
            }

            Spacer(Modifier.height(20.dp))

            Text(
                text = "Inicie sesión para continuar",
                color = SanMartinCream.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(20.dp))

            LoginTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Usuario",
                icon = Icons.Default.Person
            )

            Spacer(Modifier.height(16.dp))

            LoginTextField(
                value = password,
                onValueChange = onPasswordChange,
                label = "Contraseña",
                icon = Icons.Default.Lock,
                isPassword = true,
                passwordVisible = passwordVisible,
                onToggleVisibility = onTogglePasswordVisibility
            )

            if (error != null) {
                Spacer(Modifier.height(16.dp))
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center
                )
            }

            Spacer(Modifier.height(28.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Button(
                    onClick = onLoginClick,
                    enabled = !loading,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SanMartinOrange,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(58.dp)
                ) {
                    if (loading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("INICIAR SESIÓN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
                    }
                }

                if (showBiometricButton) {
                    Spacer(Modifier.width(12.dp))
                    OutlinedButton(
                        onClick = onBiometricClick,
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.height(58.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = SanMartinCream
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, SanMartinOrange)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Fingerprint,
                            contentDescription = "Acceso Biométrico",
                            tint = SanMartinOrange,
                            modifier = Modifier.size(28.dp)
                        )
                    }
                }
            }

            Spacer(Modifier.height(30.dp))
        }
    }
}

@Composable
private fun LoginTextField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    isPassword: Boolean = false,
    passwordVisible: Boolean = false,
    onToggleVisibility: (() -> Unit)? = null
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, color = SanMartinCream.copy(alpha = 0.6f)) },
        leadingIcon = { Icon(icon, contentDescription = null, tint = SanMartinCream.copy(alpha = 0.7f)) },
        trailingIcon = if (isPassword && onToggleVisibility != null) {
            {
                IconButton(onClick = onToggleVisibility) {
                    Icon(
                        if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                        contentDescription = null,
                        tint = SanMartinCream.copy(alpha = 0.5f)
                    )
                }
            }
        } else null,
        singleLine = true,
        shape = RoundedCornerShape(16.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = SanMartinCream,
            unfocusedTextColor = SanMartinCream,
            cursorColor = SanMartinOrange,
            focusedBorderColor = SanMartinOrange,
            unfocusedBorderColor = SanMartinCream.copy(alpha = 0.2f),
            focusedLabelColor = SanMartinOrange
        ),
        visualTransformation = if (isPassword && !passwordVisible) PasswordVisualTransformation() else VisualTransformation.None,
        keyboardOptions = KeyboardOptions(keyboardType = if (isPassword) KeyboardType.Password else KeyboardType.Text),
        modifier = Modifier.fillMaxWidth()
    )
}

@Preview(showBackground = true)
@Composable
fun LoginPreview() {
    MaterialTheme {
        LoginContent(
            username = "admin",
            onUsernameChange = {},
            password = "",
            onPasswordChange = {},
            passwordVisible = false,
            onTogglePasswordVisibility = {},
            error = null,
            loading = false,
            showBiometricButton = true,
            onBiometricClick = {},
            onLoginClick = {}
        )
    }
}
