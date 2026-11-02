package com.rancho.gestion.ui.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.Rol
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.SimpleFormDialog
import com.rancho.gestion.util.SessionManager
import kotlinx.coroutines.launch

@Composable
fun UsuariosScreen(container: AppContainer, onBack: () -> Unit) {
    if (!SessionManager.isAdmin()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("ACCESO DENEGADO", color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.Black)
        }
        LaunchedEffect(Unit) { onBack() }
        return
    }

    val usuarios by container.authRepository.getUsuarios().collectAsState(initial = emptyList())
    val scope = rememberCoroutineScope()
    
    var usuarioAEditar by remember { mutableStateOf<Usuario?>(null) }
    var datosEdicionConfirmar by remember { mutableStateOf<Pair<String, String>?>(null) }
    var usuarioAEliminar by remember { mutableStateOf<Usuario?>(null) }
    var usuarioARestablecer by remember { mutableStateOf<Usuario?>(null) }
    var mostrarNuevoUsuario by remember { mutableStateOf(false) }
    var datosRegistroConfirmar by remember { mutableStateOf<Usuario?>(null) }

    Scaffold(
        topBar = { BackTopBar(title = "Administración de Usuarios", onBack = onBack) },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { mostrarNuevoUsuario = true },
                icon = { Icon(Icons.Default.PersonAdd, null, tint = MaterialTheme.colorScheme.onPrimary) },
                text = { Text("Agregar Usuario", color = MaterialTheme.colorScheme.onPrimary) },
                containerColor = MaterialTheme.colorScheme.primary
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().background(MaterialTheme.colorScheme.background).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(usuarios, key = { it.idUsuario }) { usuario ->
                UsuarioCard(
                    usuario = usuario,
                    onEdit = { usuarioAEditar = usuario },
                    onDelete = { usuarioAEliminar = usuario },
                    onResetPassword = { usuarioARestablecer = usuario }
                )
            }
        }
    }

    if (mostrarNuevoUsuario) {
        UsuarioDialog(
            titulo = "Nuevo Usuario",
            onDismiss = { mostrarNuevoUsuario = false },
            checkExists = { container.authRepository.existeUsuario(it) },
            onConfirm = { nombre, user, pass, _ ->
                datosRegistroConfirmar = Usuario(
                    nombre = nombre,
                    nombreUsuario = user.trim().lowercase(),
                    passwordHash = pass,
                    rol = Rol.ADMINISTRADOR
                )
            }
        )
        
        if (datosRegistroConfirmar != null) {
            val u = datosRegistroConfirmar!!
            ConfirmDialog(
                title = "Confirmar Registro",
                message = buildString {
                    append("¿Estas seguro de que quieres registrar un nuevo usuario con estos datos?\n\n")
                    append("Rol: ${if (u.rol == Rol.ADMINISTRADOR) "Administrador" else "Empleado"}\n")
                    append("Nombre: ${u.nombre}\n")
                    append("Usuario: ${u.nombreUsuario}\n")
                    append("Contraseña: ${u.passwordHash}")
                },
                confirmLabel = "Registrar",
                onConfirm = {
                    scope.launch {
                        container.authRepository.registrarUsuario(u)
                        mostrarNuevoUsuario = false
                        datosRegistroConfirmar = null
                    }
                },
                onDismiss = { datosRegistroConfirmar = null }
            )
        }
    }

    var mostrarSinCambios by remember { mutableStateOf(false) }

    if (usuarioAEditar != null) {
        UsuarioDialog(
            titulo = "Editar Usuario",
            initialNombre = usuarioAEditar!!.nombre,
            initialUser = usuarioAEditar!!.nombreUsuario,
            initialRol = usuarioAEditar!!.rol,
            isEdit = true,
            onDismiss = { usuarioAEditar = null },
            checkExists = { newUser ->
                if (newUser.trim().lowercase() == usuarioAEditar!!.nombreUsuario) false
                else container.authRepository.existeUsuario(newUser)
            },
            onConfirm = { nombre, user, _, _ ->
                if (nombre.trim() == usuarioAEditar!!.nombre && user.trim().lowercase() == usuarioAEditar!!.nombreUsuario) {
                    usuarioAEditar = null
                    mostrarSinCambios = true
                } else {
                    datosEdicionConfirmar = nombre.trim() to user.trim().lowercase()
                }
            }
        )
        
        if (datosEdicionConfirmar != null) {
            val (nuevoNombre, nuevoUser) = datosEdicionConfirmar!!
            val cambioNombre = nuevoNombre != usuarioAEditar!!.nombre
            val cambioUser = nuevoUser != usuarioAEditar!!.nombreUsuario
            
            ConfirmDialog(
                title = "Confirmar Cambios",
                message = buildString {
                    append("¿Está seguro que desea guardar los cambios de este usuario?\n\n")
                    if (cambioNombre) append("Nombre Anterior: ${usuarioAEditar!!.nombre}\nNombre Nuevo: $nuevoNombre\n\n")
                    if (cambioUser) append("Usuario Anterior: ${usuarioAEditar!!.nombreUsuario}\nUsuario Nuevo: $nuevoUser")
                },
                confirmLabel = "Guardar",
                onConfirm = {
                    scope.launch {
                        container.authRepository.actualizarUsuario(
                            usuarioAEditar!!.copy(nombre = nuevoNombre, nombreUsuario = nuevoUser),
                            null
                        )
                        usuarioAEditar = null
                        datosEdicionConfirmar = null
                    }
                },
                onDismiss = { datosEdicionConfirmar = null }
            )
        }
    }
    
    if (mostrarSinCambios) {
        AlertDialog(
            onDismissRequest = { mostrarSinCambios = false },
            confirmButton = { TextButton(onClick = { mostrarSinCambios = false }) { Text("Aceptar") } },
            title = { Text("Sin cambios", color = MaterialTheme.colorScheme.onSurface) },
            text = { Text("No se ha hecho ningún cambio a los datos del usuario.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
        )
    }

    if (usuarioARestablecer != null) {
        var mostrarConfirmacion by remember { mutableStateOf<String?>(null) }
        
        RestablecerPasswordDialog(
            nombreUsuario = usuarioARestablecer!!.nombre,
            onDismiss = { usuarioARestablecer = null },
            onConfirm = { nuevaPass ->
                mostrarConfirmacion = nuevaPass
            }
        )
        
        if (mostrarConfirmacion != null) {
            ConfirmDialog(
                title = "Confirmar Cambio de Contraseña",
                message = "¿Estás seguro de que desea cambiar la contraseña de ${usuarioARestablecer!!.nombre} a '${mostrarConfirmacion}'?",
                confirmLabel = "Cambiar",
                onConfirm = {
                    val pass = mostrarConfirmacion!!
                    scope.launch {
                        container.authRepository.actualizarUsuario(usuarioARestablecer!!, pass)
                        usuarioARestablecer = null
                        mostrarConfirmacion = null
                    }
                },
                onDismiss = { mostrarConfirmacion = null }
            )
        }
    }

    if (usuarioAEliminar != null) {
        val isMe = usuarioAEliminar!!.idUsuario == SessionManager.currentUserId()
        AlertDialog(
            onDismissRequest = { usuarioAEliminar = null },
            icon = { 
                Icon(
                    imageVector = Icons.Default.Warning, 
                    contentDescription = null, 
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(48.dp)
                ) 
            },
            title = { 
                Text(
                    text = if (isMe) "Confirmar Auto-Eliminación" else "Eliminar Usuario",
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                ) 
            },
            text = { 
                Text(
                    text = if (isMe) {
                        "Estás a punto de eliminar TU PROPIA CUENTA. Si continúas, la sesión se cerrará inmediatamente y no podrás volver a entrar con tus credenciales actuales. ¿Estás seguro?"
                    } else {
                        "¿Estás seguro de eliminar a ${usuarioAEliminar!!.nombre}? Esta acción no se puede deshacer."
                    },
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        scope.launch {
                            val idAEliminar = usuarioAEliminar!!.idUsuario
                            container.authRepository.eliminarUsuario(usuarioAEliminar!!)
                            usuarioAEliminar = null
                            if (idAEliminar == SessionManager.currentUserId()) {
                                SessionManager.logout()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) { 
                    Text(if (isMe) "Eliminar y Salir" else "Eliminar") 
                }
            },
            dismissButton = {
                TextButton(onClick = { usuarioAEliminar = null }) { 
                    Text("Cancelar") 
                }
            }
        )
    }
}

@Composable
private fun UsuarioCard(
    usuario: Usuario,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onResetPassword: () -> Unit
) {
    val isMe = usuario.idUsuario == SessionManager.currentUserId()

    ElevatedCard(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = usuario.nombre.take(1).uppercase(),
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = usuario.nombre,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "@${usuario.nombreUsuario} · ${if (usuario.rol == Rol.ADMINISTRADOR) "Administrador" else "Empleado"}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
                if (isMe) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "Tú",
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            
            Row {
                if (usuario.idUsuario != "1") {
                    IconButton(onClick = onEdit) {
                        Icon(Icons.Default.Edit, "Editar", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onResetPassword) {
                        Icon(Icons.Default.LockReset, "Restablecer", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    IconButton(onClick = onDelete) {
                        Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UsuarioDialog(
    titulo: String,
    initialNombre: String = "",
    initialUser: String = "",
    initialRol: Rol = Rol.ADMINISTRADOR,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    checkExists: suspend (String) -> Boolean,
    onConfirm: (String, String, String, Rol) -> Unit
) {
    var nombre by remember { mutableStateOf(initialNombre) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var rol by remember { mutableStateOf(initialRol) }
    var error by remember { mutableStateOf<String?>(null) }
    var validando by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    SimpleFormDialog(
        title = titulo,
        onDismiss = onDismiss,
        confirmLabel = if (isEdit) "Guardar" else "Agregar",
        confirmEnabled = !validando,
        onConfirm = {
            val passError = when {
                pass.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
                pass.length > 16 -> "La contraseña no debe exceder los 16 caracteres"
                else -> null
            }

            if (nombre.isBlank() || user.isBlank() || (!isEdit && pass.isBlank())) {
                error = "Completa todos los campos obligatorios"
            } else if (nombre.length > 50) {
                error = "El nombre no debe exceder los 50 caracteres"
            } else if (user.length > 20) {
                error = "El usuario no debe exceder los 20 caracteres"
            } else if (!isEdit && passError != null) {
                error = passError
            } else {
                validando = true
                scope.launch {
                    if (checkExists(user)) {
                        error = "El nombre de usuario '$user' ya está en uso"
                        validando = false
                    } else {
                        onConfirm(nombre, user, pass, rol)
                        validando = false
                    }
                }
            }
        }
    ) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { input -> 
                val filtered = input.filter { it.isLetter() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || "áéíóúÁÉÍÓÚ".contains(it) }
                if (filtered.length <= 50) nombre = filtered
            },
            label = { Text("Nombre Completo") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = user,
            onValueChange = { input -> 
                val filtered = input.filter { it.isLetterOrDigit() || "@-_".contains(it) }
                if (filtered.length <= 20) user = filtered
            },
            label = { Text("Nombre de Usuario") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        if (!isEdit) {
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { if (it.length <= 16) pass = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = if (passVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { passVisible = !passVisible }) {
                        Icon(
                            imageVector = if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (passVisible) "Ocultar" else "Mostrar"
                        )
                    }
                },
                modifier = Modifier.fillMaxWidth()
            )
        }
        
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}

@Composable
private fun RestablecerPasswordDialog(
    nombreUsuario: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    var pass by remember { mutableStateOf("") }
    var confirmPass by remember { mutableStateOf("") }
    var passVisible by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SimpleFormDialog(
        title = "Restablecer Contraseña",
        onDismiss = onDismiss,
        confirmLabel = "Restablecer",
        onConfirm = {
            when {
                pass.length < 6 -> error = "La contraseña debe tener al menos 6 caracteres"
                pass.length > 16 -> error = "La contraseña no debe exceder los 16 caracteres"
                pass != confirmPass -> error = "Las contraseñas no coinciden"
                else -> onConfirm(pass)
            }
        }
    ) {
        Text("Usuario: $nombreUsuario", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurface)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { if (it.length <= 16) { pass = it; error = null } },
            label = { Text("Nueva Contraseña") },
            singleLine = true,
            visualTransformation = if (passVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            trailingIcon = {
                IconButton(onClick = { passVisible = !passVisible }) {
                    Icon(
                        imageVector = if (passVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                        contentDescription = if (passVisible) "Ocultar" else "Mostrar"
                    )
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(8.dp))
        OutlinedTextField(
            value = confirmPass,
            onValueChange = { if (it.length <= 16) { confirmPass = it; error = null } },
            label = { Text("Confirmar Nueva Contraseña") },
            singleLine = true,
            visualTransformation = if (passVisible) androidx.compose.ui.text.input.VisualTransformation.None else PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth()
        )
        error?.let {
            Spacer(Modifier.height(8.dp))
            Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
        }
    }
}
