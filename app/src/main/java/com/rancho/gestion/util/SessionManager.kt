package com.rancho.gestion.util

import com.rancho.gestion.data.entities.Rol
import com.rancho.gestion.data.entities.Usuario
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SessionManager {
    private val _usuarioActual = MutableStateFlow<Usuario?>(null)
    val usuarioActual: StateFlow<Usuario?> = _usuarioActual.asStateFlow()

    fun login(usuario: Usuario) { _usuarioActual.value = usuario }
    fun logout() { _usuarioActual.value = null }

    fun isAdmin(): Boolean = _usuarioActual.value?.rol == Rol.ADMINISTRADOR
    fun isLoggedIn(): Boolean = _usuarioActual.value != null
    fun currentUsername(): String = _usuarioActual.value?.nombreUsuario ?: ""
    fun currentUserId(): Long = _usuarioActual.value?.idUsuario ?: 0
}
