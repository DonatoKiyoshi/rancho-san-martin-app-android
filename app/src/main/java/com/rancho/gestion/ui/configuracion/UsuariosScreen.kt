package com.rancho.gestion.ui.configuracion

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.AppContainer
import com.rancho.gestion.data.entities.Rol
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.ConfirmDialog
import com.rancho.gestion.ui.components.SimpleFormDialog
import com.rancho.gestion.ui.theme.RanchoCard
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoTextSecondary
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
                icon = { Icon(Icons.Default.PersonAdd, null) },
                text = { Text("Agregar Usuario") }
            )
        }
    ) { padding ->
        LazyColumn(
            Modifier.padding(padding).fillMaxSize().padding(horizontal = 20.dp),
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
            onConfirm = { nombre, user, pass, rol ->
                datosRegistroConfirmar = Usuario(
                    nombre = nombre,
                    nombreUsuario = user,
                    passwordHash = pass, // Guardamos la temporal para mostrarla
                    rol = rol
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

    // Estado para mensaje de "sin cambios"
    var mostrarSinCambios by remember { mutableStateOf(false) }

    if (usuarioAEditar != null) {
        UsuarioDialog(
            titulo = "Editar Usuario",
            initialNombre = usuarioAEditar!!.nombre,
            initialUser = usuarioAEditar!!.nombreUsuario,
            initialRol = usuarioAEditar!!.rol,
            isEdit = true,
            onDismiss = { usuarioAEditar = null },
            onConfirm = { nombre, user, _, _ ->
                if (nombre.trim() == usuarioAEditar!!.nombre && user.trim() == usuarioAEditar!!.nombreUsuario) {
                    usuarioAEditar = null
                    mostrarSinCambios = true
                } else {
                    datosEdicionConfirmar = nombre.trim() to user.trim()
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
            title = { Text("Sin cambios") },
            text = { Text("No se ha hecho ningún cambio a los datos del usuario.") }
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
        ConfirmDialog(
            title = "Eliminar Usuario",
            message = "¿Estás seguro de eliminar a ${usuarioAEliminar!!.nombre}? Esta acción no se puede deshacer.",
            confirmLabel = "Eliminar",
            onConfirm = {
                scope.launch {
                    container.authRepository.eliminarUsuario(usuarioAEliminar!!)
                    usuarioAEliminar = null
                }
            },
            onDismiss = { usuarioAEliminar = null }
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
        colors = CardDefaults.elevatedCardColors(containerColor = RanchoCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            Modifier.padding(16.dp).fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(48.dp).clip(CircleShape).background(RanchoPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    usuario.nombre.take(1).uppercase(),
                    color = RanchoPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 20.sp
                )
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(usuario.nombre, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "@${usuario.nombreUsuario} · ${if (usuario.rol == Rol.ADMINISTRADOR) "Administrador" else "Empleado"}",
                    color = RanchoTextSecondary,
                    style = MaterialTheme.typography.bodySmall
                )
                if (isMe) Text("Tú", color = RanchoPrimary, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold)
            }
            
            Row {
                IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, "Editar", tint = RanchoTextSecondary) }
                IconButton(onClick = onResetPassword) { Icon(Icons.Default.LockReset, "Restablecer", tint = RanchoTextSecondary) }
                if (!isMe && usuario.rol != Rol.ADMINISTRADOR) {
                    IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, "Eliminar", tint = MaterialTheme.colorScheme.error) }
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
    initialRol: Rol = Rol.EMPLEADO,
    isEdit: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String, String, String, Rol) -> Unit
) {
    var nombre by remember { mutableStateOf(initialNombre) }
    var user by remember { mutableStateOf(initialUser) }
    var pass by remember { mutableStateOf("") }
    var rol by remember { mutableStateOf(initialRol) }
    var rolMenuAbierto by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    SimpleFormDialog(
        title = titulo,
        onDismiss = onDismiss,
        confirmLabel = if (isEdit) "Guardar" else "Agregar",
        onConfirm = {
            val passError = when {
                pass.length < 6 -> "La contraseña debe tener al menos 6 caracteres"
                pass.length > 16 -> "La contraseña no debe exceder los 16 caracteres"
                else -> null
            }

            if (nombre.isBlank() || user.isBlank() || (!isEdit && pass.isBlank())) {
                error = "Completa todos los campos obligatorios"
            } else if (nombre.length > 60) {
                error = "El nombre no debe exceder los 60 caracteres"
            } else if (user.length > 20) {
                error = "El usuario no debe exceder los 20 caracteres"
            } else if (!isEdit && passError != null) {
                error = passError
            } else {
                onConfirm(nombre, user, pass, rol)
            }
        }
    ) {
        OutlinedTextField(
            value = nombre,
            onValueChange = { input -> 
                val filtered = input.filter { it.isLetter() || it.isWhitespace() || it == 'ñ' || it == 'Ñ' || "áéíóúÁÉÍÓÚ".contains(it) }
                if (filtered.length <= 60) nombre = filtered
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
            ExposedDropdownMenuBox(
                expanded = rolMenuAbierto,
                onExpandedChange = { rolMenuAbierto = it }
            ) {
                OutlinedTextField(
                    value = if (rol == Rol.ADMINISTRADOR) "Administrador" else "Empleado",
                    onValueChange = {},
                    readOnly = true,
                    label = { Text("Rol") },
                    modifier = Modifier.fillMaxWidth().menuAnchor(),
                    trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = rolMenuAbierto) }
                )
                ExposedDropdownMenu(
                    expanded = rolMenuAbierto,
                    onDismissRequest = { rolMenuAbierto = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Administrador") },
                        onClick = { rol = Rol.ADMINISTRADOR; rolMenuAbierto = false }
                    )
                    DropdownMenuItem(
                        text = { Text("Empleado") },
                        onClick = { rol = Rol.EMPLEADO; rolMenuAbierto = false }
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = pass,
                onValueChange = { pass = it },
                label = { Text("Contraseña") },
                singleLine = true,
                visualTransformation = PasswordVisualTransformation(),
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
        Text("Usuario: $nombreUsuario", style = MaterialTheme.typography.bodyMedium)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = pass,
            onValueChange = { pass = it; error = null },
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
            onValueChange = { confirmPass = it; error = null },
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
