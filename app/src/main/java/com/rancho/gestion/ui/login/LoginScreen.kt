package com.rancho.gestion.ui.login

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.R
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
    val scope = rememberCoroutineScope()

    fun intentarLogin() {
        if (username.isBlank() || password.isBlank()) {
            error = "Ingresa usuario y contraseña"
            return
        }
        loading = true
        scope.launch {
            val usuario = container.authRepository.login(username, password)
            loading = false
            if (usuario != null) {
                SessionManager.login(usuario)
                onLoginSuccess()
            } else {
                error = "Usuario o contraseña incorrectos"
            }
        }
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
        onLoginClick = { intentarLogin() }
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
    onLoginClick: () -> Unit
) {
    val scrollState = rememberScrollState()
    // Fondo gris oscuro profesional que resalta el logo transparente
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
            Spacer(Modifier.height(60.dp))

            // PALABRA RANCHO SUPERIOR
            Text(
                text = "RANCHO",
                color = SanMartinCream,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Light,
                letterSpacing = 8.sp,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // LOGO ORIGINAL DESDE IMAGEN (PROC-21-FINAL)
            Image(
                painter = painterResource(id = R.drawable.logotransparente),
                contentDescription = "Logo Rancho San Martín",
                modifier = Modifier
                    .size(width = 280.dp, height = 180.dp),
                contentScale = ContentScale.Fit
            )

            // NOMBRE DE LA MARCA (TEXTO COMPLEMENTARIO)
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

            Spacer(Modifier.height(24.dp))

            Text(
                text = "Inicie sesión para continuar",
                color = SanMartinCream.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(24.dp))

            LoginTextField(
                value = username,
                onValueChange = onUsernameChange,
                label = "Usuario",
                icon = Icons.Default.Person
            )

            Spacer(Modifier.height(18.dp))

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

            Spacer(Modifier.height(36.dp))

            // BOTÓN DE ACCIÓN PERSONALIZADO
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
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), color = Color.White, strokeWidth = 2.dp)
                } else {
                    Text("INICIAR SESIÓN", fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
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
            onLoginClick = {}
        )
    }
}
