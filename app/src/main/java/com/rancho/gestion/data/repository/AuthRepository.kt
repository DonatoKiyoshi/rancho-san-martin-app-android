package com.rancho.gestion.data.repository

import com.rancho.gestion.data.dao.UsuarioDao
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.util.PasswordHasher
import kotlinx.coroutines.flow.Flow

class AuthRepository(private val usuarioDao: UsuarioDao) {

    suspend fun login(username: String, password: String): Usuario? {
        val userClean = username.trim().lowercase()
        val passClean = password.trim()
        
        // Log para depuración
        android.util.Log.d("AuthRepository", "Intento de login para: '$userClean'")

        val usuario = usuarioDao.getByUsername(userClean)
        if (usuario == null) {
            android.util.Log.e("AuthRepository", "Login fallido: Usuario '$userClean' no encontrado en la DB")
            return null
        }
        
        val match = PasswordHasher.matches(passClean, usuario.passwordHash)
        if (!match) {
            android.util.Log.e("AuthRepository", "Login fallido: La contraseña proporcionada no coincide con el hash guardado para '$userClean'")
        }
        
        return if (match) usuario else null
    }

    suspend fun cambiarContrasena(usuario: Usuario, actual: String, nueva: String): Boolean {
        if (!PasswordHasher.matches(actual.trim(), usuario.passwordHash)) return false
        usuarioDao.update(usuario.copy(passwordHash = PasswordHasher.hash(nueva.trim())))
        return true
    }

    fun getUsuarios(): Flow<List<Usuario>> = usuarioDao.getAll()

    suspend fun getUsuarioById(id: Long): Usuario? = usuarioDao.getById(id)

    suspend fun registrarUsuario(usuario: Usuario) {
        usuarioDao.insert(usuario.copy(
            nombreUsuario = usuario.nombreUsuario.trim().lowercase(),
            passwordHash = PasswordHasher.hash(usuario.passwordHash)
        ))
    }

    suspend fun existeUsuario(username: String): Boolean = usuarioDao.getByUsername(username.trim().lowercase()) != null

    suspend fun actualizarUsuario(usuario: Usuario, passwordNueva: String?) {
        val hash = if (!passwordNueva.isNullOrBlank()) PasswordHasher.hash(passwordNueva) else usuario.passwordHash
        usuarioDao.update(usuario.copy(passwordHash = hash))
    }

    suspend fun eliminarUsuario(usuario: Usuario) {
        usuarioDao.delete(usuario)
    }

    suspend fun asegurarAdmin() {
        android.util.Log.d("AuthRepository", "Ejecutando asegurarAdmin() para verificar cuenta de administrador...")
        val nuevaContrasenaDefault = "RanchoSanMartin_a5rCNc!Q"
        
        val todosLosUsuarios = usuarioDao.getAllOnce()
        val admins = todosLosUsuarios.filter { it.nombreUsuario.trim().lowercase() == "admin" }
        
        if (admins.isEmpty()) {
            usuarioDao.insert(Usuario(
                nombre = "Administrador",
                nombreUsuario = "admin",
                passwordHash = PasswordHasher.hash(nuevaContrasenaDefault),
                rol = com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
            ))
        } else {
            // Si ya existe, nos aseguramos de que tenga la contraseña correcta
            val adminPrincipal = admins.first()
            if (!PasswordHasher.matches(nuevaContrasenaDefault, adminPrincipal.passwordHash)) {
                usuarioDao.update(adminPrincipal.copy(
                    nombre = "Administrador",
                    nombreUsuario = "admin",
                    passwordHash = PasswordHasher.hash(nuevaContrasenaDefault),
                    rol = com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
                ))
            }
            // Eliminar duplicados si los hubiera
            if (admins.size > 1) {
                admins.drop(1).forEach { usuarioDao.delete(it) }
            }
        }

        // Ya no aseguramos el usuario EMPLEADO por defecto (RF-FIX)
        val empleados = todosLosUsuarios.filter { it.nombreUsuario.trim().lowercase() == "empleado" }
        if (empleados.isNotEmpty()) {
            empleados.forEach { usuarioDao.delete(it) }
        }
    }
}
