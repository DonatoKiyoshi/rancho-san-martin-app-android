package com.rancho.gestion.util

import android.util.Base64
import java.security.SecureRandom
import java.security.spec.KeySpec
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

/**
 * Hash de contraseñas con salt aleatorio por usuario, usando PBKDF2 con
 * HMAC-SHA256 (viene incluido en el JDK/Android; no requiere agregar
 * dependencias externas tipo BCrypt/Argon2 para lograr un hash seguro).
 *
 * Formato guardado en Usuario.passwordHash:
 *   "<iteraciones>:<salt en base64>:<hash en base64>"
 *
 * Antes este prototipo usaba SHA-256 sin salt (ver nota en README, sección
 * "Decisiones y supuestos"); con salt por usuario se evita que una tabla
 * arcoíris o dos usuarios con la misma contraseña compartan el mismo hash.
 */
object PasswordHasher {
    private const val ITERACIONES = 120_000
    private const val LONGITUD_CLAVE_BITS = 256
    private const val ALGORITMO = "PBKDF2WithHmacSHA256"

    fun hash(raw: String): String {
        val salt = ByteArray(16).also { SecureRandom().nextBytes(it) }
        val hashBytes = derivar(raw, salt, ITERACIONES)
        return "$ITERACIONES:${encode(salt)}:${encode(hashBytes)}"
    }

    fun matches(raw: String, stored: String): Boolean {
        val partes = stored.split(":")
        if (partes.size != 3) return false
        val iteraciones = partes[0].toIntOrNull() ?: return false
        val salt = decodeOrNull(partes[1]) ?: return false
        val hashEsperado = decodeOrNull(partes[2]) ?: return false
        val hashCalculado = derivar(raw, salt, iteraciones)
        return hashCalculado.contentEquals(hashEsperado)
    }

    private fun derivar(raw: String, salt: ByteArray, iteraciones: Int): ByteArray {
        val spec: KeySpec = PBEKeySpec(raw.toCharArray(), salt, iteraciones, LONGITUD_CLAVE_BITS)
        val factory = SecretKeyFactory.getInstance(ALGORITMO)
        return factory.generateSecret(spec).encoded
    }

    private fun encode(bytes: ByteArray): String = Base64.encodeToString(bytes, Base64.NO_WRAP)

    private fun decodeOrNull(value: String): ByteArray? = try {
        Base64.decode(value, Base64.NO_WRAP)
    } catch (e: IllegalArgumentException) {
        null
    }
}
