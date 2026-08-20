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

    suspend fun registrarUsuario(usuario: Usuario) {
        usuarioDao.insert(usuario.copy(passwordHash = PasswordHasher.hash(usuario.passwordHash)))
    }

    suspend fun actualizarUsuario(usuario: Usuario, passwordNueva: String?) {
        val hash = if (!passwordNueva.isNullOrBlank()) PasswordHasher.hash(passwordNueva) else usuario.passwordHash
        usuarioDao.update(usuario.copy(passwordHash = hash))
    }

    suspend fun eliminarUsuario(usuario: Usuario) {
        usuarioDao.delete(usuario)
    }

    suspend fun asegurarAdmin() {
        android.util.Log.d("AuthRepository", "Ejecutando asegurarAdmin() para verificar cuentas por defecto...")
        // 1. Limpieza de posibles duplicados accidentales de 'admin'
        // (En entornos de desarrollo sin índices únicos pueden ocurrir)
        val todosLosUsuarios = usuarioDao.getAllOnce()
        val admins = todosLosUsuarios.filter { it.nombreUsuario.trim().lowercase() == "admin" }
        
        if (admins.isEmpty()) {
            usuarioDao.insert(Usuario(
                nombre = "Administrador",
                nombreUsuario = "admin",
                passwordHash = PasswordHasher.hash("admin123"),
                rol = com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
            ))
        } else {
            // Si ya existe uno o más, nos aseguramos de que el primero sea válido y tenga admin123
            val adminPrincipal = admins.first()
            if (!PasswordHasher.matches("admin123", adminPrincipal.passwordHash)) {
                usuarioDao.update(adminPrincipal.copy(
                    nombre = "Administrador",
                    nombreUsuario = "admin",
                    passwordHash = PasswordHasher.hash("admin123"),
                    rol = com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
                ))
            }
            // Eliminar duplicados si los hubiera
            if (admins.size > 1) {
                admins.drop(1).forEach { usuarioDao.delete(it) }
            }
        }

        // 2. Asegurar usuario EMPLEADO (para pruebas del sistema)
        val empleados = todosLosUsuarios.filter { it.nombreUsuario.trim().lowercase() == "empleado" }
        if (empleados.isEmpty()) {
            usuarioDao.insert(Usuario(
                nombre = "Empleado de Prueba",
                nombreUsuario = "empleado",
                passwordHash = PasswordHasher.hash("empleado123"),
                rol = com.rancho.gestion.data.entities.Rol.EMPLEADO
            ))
        } else {
            val empPrincipal = empleados.first()
            if (!PasswordHasher.matches("empleado123", empPrincipal.passwordHash)) {
                usuarioDao.update(empPrincipal.copy(
                    passwordHash = PasswordHasher.hash("empleado123")
                ))
            }
            if (empleados.size > 1) {
                empleados.drop(1).forEach { usuarioDao.delete(it) }
            }
        }
    }
}
