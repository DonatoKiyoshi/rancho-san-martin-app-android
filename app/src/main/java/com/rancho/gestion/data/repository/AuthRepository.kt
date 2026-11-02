package com.rancho.gestion.data.repository

import com.rancho.gestion.data.dao.SincronizacionDao
import com.rancho.gestion.data.dao.UsuarioDao
import com.rancho.gestion.data.entities.ColaSincronizacion
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.util.PasswordHasher
import kotlinx.coroutines.flow.Flow

class AuthRepository(private val usuarioDao: UsuarioDao, private val sincronizacionDao: SincronizacionDao) {

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
        val updatedUsuario = usuario.copy(passwordHash = PasswordHasher.hash(nueva.trim()))
        usuarioDao.update(updatedUsuario)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "usuarios",
                idEntidad = updatedUsuario.idUsuario,
                operacion = "UPDATE",
                versionLocal = updatedUsuario.version,
                idDispositivo = updatedUsuario.idUsuario
            )
        )
        return true
    }

    fun getUsuarios(): Flow<List<Usuario>> = usuarioDao.getAll()

    suspend fun getUsuarioById(id: String): Usuario? = usuarioDao.getById(id)

    suspend fun getUsuarioByUsername(username: String): Usuario? = usuarioDao.getByUsername(username.trim().lowercase())

    suspend fun registrarUsuario(usuario: Usuario) {
        val userToInsert = usuario.copy(
            nombreUsuario = usuario.nombreUsuario.trim().lowercase(),
            passwordHash = PasswordHasher.hash(usuario.passwordHash)
        )
        usuarioDao.insert(userToInsert)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "usuarios",
                idEntidad = userToInsert.idUsuario,
                operacion = "INSERT",
                versionLocal = userToInsert.version,
                idDispositivo = "sistema" // o puedes usar el admin id
            )
        )
    }

    suspend fun existeUsuario(username: String): Boolean = usuarioDao.getByUsername(username.trim().lowercase()) != null

    suspend fun actualizarUsuario(usuario: Usuario, passwordNueva: String?) {
        val hash = if (!passwordNueva.isNullOrBlank()) PasswordHasher.hash(passwordNueva) else usuario.passwordHash
        val updatedUsuario = usuario.copy(passwordHash = hash)
        usuarioDao.update(updatedUsuario)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "usuarios",
                idEntidad = updatedUsuario.idUsuario,
                operacion = "UPDATE",
                versionLocal = updatedUsuario.version,
                idDispositivo = updatedUsuario.idUsuario
            )
        )
    }

    suspend fun eliminarUsuario(usuario: Usuario) {
        usuarioDao.delete(usuario)
        
        sincronizacionDao.insert(
            ColaSincronizacion(
                entidad = "usuarios",
                idEntidad = usuario.idUsuario,
                operacion = "DELETE",
                versionLocal = usuario.version,
                idDispositivo = "sistema"
            )
        )
    }

    suspend fun asegurarAdmin() {
        android.util.Log.d("AuthRepository", "Ejecutando asegurarAdmin() para verificar cuenta de administrador...")
        val nuevaContrasenaDefault = "RanchoSanMartin_a5rCNc!Q"
        
        val todosLosUsuarios = usuarioDao.getAllOnce()
        val admins = todosLosUsuarios.filter { it.nombreUsuario.trim().lowercase() == "admin" }
        
        if (admins.isEmpty()) {
            val adminUser = Usuario(
                nombre = "Administrador",
                nombreUsuario = "admin",
                passwordHash = PasswordHasher.hash(nuevaContrasenaDefault),
                rol = com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
            )
            usuarioDao.insert(adminUser)
            
            sincronizacionDao.insert(
                ColaSincronizacion(
                    entidad = "usuarios",
                    idEntidad = adminUser.idUsuario,
                    operacion = "INSERT",
                    versionLocal = 1,
                    idDispositivo = "sistema"
                )
            )
        } else {
            // Si ya existe, nos aseguramos de que tenga la contraseña correcta
            val adminPrincipal = admins.first()
            if (!PasswordHasher.matches(nuevaContrasenaDefault, adminPrincipal.passwordHash)) {
                val updatedAdmin = adminPrincipal.copy(
                    nombre = "Administrador",
                    nombreUsuario = "admin",
                    passwordHash = PasswordHasher.hash(nuevaContrasenaDefault),
                    rol = com.rancho.gestion.data.entities.Rol.ADMINISTRADOR
                )
                usuarioDao.update(updatedAdmin)
                sincronizacionDao.insert(
                    ColaSincronizacion(
                        entidad = "usuarios",
                        idEntidad = updatedAdmin.idUsuario,
                        operacion = "UPDATE",
                        versionLocal = updatedAdmin.version,
                        idDispositivo = "sistema"
                    )
                )
            }
            // Eliminar duplicados si los hubiera
            if (admins.size > 1) {
                admins.drop(1).forEach { 
                    usuarioDao.delete(it)
                    sincronizacionDao.insert(
                        ColaSincronizacion(
                            entidad = "usuarios",
                            idEntidad = it.idUsuario,
                            operacion = "DELETE",
                            versionLocal = it.version,
                            idDispositivo = "sistema"
                        )
                    )
                }
            }
        }

        // Ya no aseguramos el usuario EMPLEADO por defecto (RF-FIX)
        val empleados = todosLosUsuarios.filter { it.nombreUsuario.trim().lowercase() == "empleado" }
        if (empleados.isNotEmpty()) {
            empleados.forEach { usuarioDao.delete(it) }
        }
    }
}
