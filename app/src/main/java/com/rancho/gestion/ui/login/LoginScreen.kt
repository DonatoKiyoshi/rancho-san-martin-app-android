package com.rancho.gestion.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import java.util.Calendar

enum class LoginUiState {
    FIRST_TIME,
    SAVED_USER_SELECTION,
    SAVED_USER_PASSWORD
}

@Composable
fun LoginScreen(container: AppContainer, onLoginSuccess: () -> Unit) {
    val biometricAuthManager = container.biometricAuthManager
    val savedUsername = remember { biometricAuthManager.getSavedUsername() }
    val savedName = remember { biometricAuthManager.getSavedName() }

    var username by remember { mutableStateOf(savedUsername ?: "") }
    var password by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var loading by remember { mutableStateOf(false) }
    
    var uiState by remember { 
        mutableStateOf(
            if (savedUsername != null) LoginUiState.SAVED_USER_SELECTION else LoginUiState.FIRST_TIME
        )
    }

    // Estados para dialogo de guardar usuario y activacion biométrica
    var usuarioPendienteLogin by remember { mutableStateOf<Usuario?>(null) }
    var mostrarDialogoGuardarUsuario by remember { mutableStateOf(false) }
    var mostrarDialogoBiometria by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val activity = context as? FragmentActivity
    val scope = rememberCoroutineScope()

    val biometricAvailable = remember { biometricAuthManager.isBiometricAvailable() }
    val biometricEnabled = remember { biometricAuthManager.isBiometricEnabled() }

    fun ejecutarLoginBiometrico() {
        if (activity == null) return
        val currentSavedUsername = biometricAuthManager.getSavedUsername() ?: return

        biometricAuthManager.autenticar(
            activity = activity,
            title = "Inicio de Sesión Biométrico",
            subtitle = "Escanea tu huella o rostro para ingresar",
            onSuccess = {
                scope.launch {
                    val userEncontrado = container.authRepository.getUsuarioByUsername(currentSavedUsername)

                    if (userEncontrado != null) {
                        SessionManager.login(userEncontrado)
                        kotlinx.coroutines.delay(150) // Permite que el diálogo nativo de BiometricPrompt se cierre limpiamente
                        onLoginSuccess()
                    } else {
                        error = "No se pudo recuperar la sesión biométrica. Inicia sesión manualmente."
                        uiState = LoginUiState.FIRST_TIME
                        biometricAuthManager.clearCredentials()
                    }
                }
            },
            onError = { msg ->
                error = msg
            }
        )
    }

    // Si la biometría está habilitada y estamos en selección, intentamos sugerir el login biométrico automáticamente al abrir
    LaunchedEffect(uiState) {
        if (uiState == LoginUiState.SAVED_USER_SELECTION && biometricAvailable && biometricEnabled) {
            ejecutarLoginBiometrico()
        }
    }

    fun completarLoginExitoso(usuario: Usuario) {
        val hasSavedUser = biometricAuthManager.getSavedUsername() != null
        if (!hasSavedUser) {
            usuarioPendienteLogin = usuario
            mostrarDialogoGuardarUsuario = true
        } else if (biometricAvailable && !biometricAuthManager.hasBeenAskedBiometric()) {
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

    if (mostrarDialogoGuardarUsuario && usuarioPendienteLogin != null) {
        AlertDialog(
            onDismissRequest = {
                val user = usuarioPendienteLogin!!
                mostrarDialogoGuardarUsuario = false
                SessionManager.login(user)
                onLoginSuccess()
            },
            title = { Text("¿Guardar usuario?") },
            text = {
                Text("¿Deseas guardar tu usuario para no tener que ingresarlo la próxima vez?")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val user = usuarioPendienteLogin!!
                        biometricAuthManager.saveUser(user.nombreUsuario, user.nombre, user.passwordHash)
                        mostrarDialogoGuardarUsuario = false
                        if (biometricAvailable && !biometricAuthManager.hasBeenAskedBiometric()) {
                            mostrarDialogoBiometria = true
                        } else {
                            SessionManager.login(user)
                            onLoginSuccess()
                        }
                    }
                ) {
                    Text("Sí, guardar", color = SanMartinOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        val user = usuarioPendienteLogin!!
                        mostrarDialogoGuardarUsuario = false
                        SessionManager.login(user)
                        onLoginSuccess()
                    }
                ) {
                    Text("Ahora no")
                }
            }
        )
    }

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
                        biometricAuthManager.enableBiometric()
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
        uiState = uiState,
        savedName = savedName,
        username = username,
        onUsernameChange = { username = it; error = null },
        password = password,
        onPasswordChange = { password = it; error = null },
        passwordVisible = passwordVisible,
        onTogglePasswordVisibility = { passwordVisible = !passwordVisible },
        error = error,
        loading = loading,
        biometricEnabled = biometricEnabled,
        onBiometricClick = { ejecutarLoginBiometrico() },
        onLoginClick = { intentarLoginManual() },
        onSelectPasswordClick = { uiState = LoginUiState.SAVED_USER_PASSWORD },
        onBackToSelectionClick = { 
            uiState = LoginUiState.SAVED_USER_SELECTION
            password = ""
            error = null
        },
        onChangeUserClick = { 
            biometricAuthManager.clearCredentials()
            uiState = LoginUiState.FIRST_TIME
            username = ""
            password = ""
            error = null
        }
    )
}

@Composable
private fun LoginContent(
    uiState: LoginUiState,
    savedName: String?,
    username: String,
    onUsernameChange: (String) -> Unit,
    password: String,
    onPasswordChange: (String) -> Unit,
    passwordVisible: Boolean,
    onTogglePasswordVisibility: () -> Unit,
    error: String?,
    loading: Boolean,
    biometricEnabled: Boolean,
    onBiometricClick: () -> Unit,
    onLoginClick: () -> Unit,
    onSelectPasswordClick: () -> Unit,
    onBackToSelectionClick: () -> Unit,
    onChangeUserClick: () -> Unit
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

            when (uiState) {
                LoginUiState.FIRST_TIME -> {
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

                    Button(
                        onClick = onLoginClick,
                        enabled = !loading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = SanMartinOrange,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier
                            .fillMaxWidth()
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
                }
                
                LoginUiState.SAVED_USER_SELECTION -> {
                    val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
                    val greeting = if (hour < 12) "Buenos días" else "Buenas tardes"
                    
                    Text(
                        text = "$greeting, ${savedName ?: username}",
                        color = SanMartinCream,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(8.dp))
                    
                    Text(
                        text = "Ingrese con",
                        color = SanMartinCream.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyLarge,
                        textAlign = TextAlign.Center
                    )
                    
                    Spacer(Modifier.height(24.dp))
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = onSelectPasswordClick,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SanMartinOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(58.dp)
                        ) {
                            Text("Contraseña", fontWeight = FontWeight.Bold)
                        }

                        if (biometricEnabled) {
                            Spacer(Modifier.width(16.dp))
                            Button(
                                onClick = onBiometricClick,
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = SanMartinOrange,
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(16.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .height(58.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Fingerprint,
                                    contentDescription = "Huella",
                                    modifier = Modifier.padding(end = 8.dp)
                                )
                                Text("Huella", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Text(
                        text = "¿No eres ${savedName ?: username}?",
                        color = SanMartinCream.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onChangeUserClick() }
                    )
                }
                
                LoginUiState.SAVED_USER_PASSWORD -> {
                    Text(
                        text = "Ingrese su contraseña para continuar",
                        color = SanMartinCream.copy(alpha = 0.8f),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )

                    Spacer(Modifier.height(20.dp))

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
                        OutlinedButton(
                            onClick = onBackToSelectionClick,
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(0.3f)
                                .height(58.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = SanMartinCream
                            ),
                            border = androidx.compose.foundation.BorderStroke(1.dp, SanMartinCream.copy(alpha = 0.5f))
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Regresar",
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        Spacer(Modifier.width(12.dp))

                        Button(
                            onClick = onLoginClick,
                            enabled = !loading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = SanMartinOrange,
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .weight(0.7f)
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
                    }
                    
                    Spacer(Modifier.height(32.dp))
                    
                    Text(
                        text = "¿No eres ${savedName ?: username}?",
                        color = SanMartinCream.copy(alpha = 0.7f),
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.clickable { onChangeUserClick() }
                    )
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
            uiState = LoginUiState.FIRST_TIME,
            savedName = null,
            username = "admin",
            onUsernameChange = {},
            password = "",
            onPasswordChange = {},
            passwordVisible = false,
            onTogglePasswordVisibility = {},
            error = null,
            loading = false,
            biometricEnabled = true,
            onBiometricClick = {},
            onLoginClick = {},
            onSelectPasswordClick = {},
            onBackToSelectionClick = {},
            onChangeUserClick = {}
        )
    }
}
