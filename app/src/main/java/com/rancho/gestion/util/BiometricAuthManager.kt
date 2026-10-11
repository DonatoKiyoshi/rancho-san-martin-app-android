package com.rancho.gestion.util

import android.content.Context
import android.util.Log
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

class BiometricAuthManager(private val context: Context) {

    private val tag = "BiometricAuthManager"

    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val prefs by lazy {
        try {
            EncryptedSharedPreferences.create(
                context,
                "rancho_secure_biometric_prefs",
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
        } catch (e: Exception) {
            Log.e(tag, "Error inicializando EncryptedSharedPreferences", e)
            context.getSharedPreferences("rancho_secure_biometric_prefs_fallback", Context.MODE_PRIVATE)
        }
    }

    /**
     * Verifica si el dispositivo cuenta con hardware biométrico disponible y si el usuario
     * tiene registrado al menos un método de bloqueo biométrico (huella dactilar o rostro).
     */
    fun isBiometricAvailable(): Boolean {
        return try {
            val biometricManager = BiometricManager.from(context)
            val canAuth = biometricManager.canAuthenticate(
                BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
            )
            canAuth == BiometricManager.BIOMETRIC_SUCCESS
        } catch (e: Exception) {
            Log.e(tag, "Error verificando disponibilidad biométrica", e)
            false
        }
    }

    /**
     * Retorna verdadero si el usuario ha habilitado la opción de inicio de sesión biométrico.
     */
    fun isBiometricEnabled(): Boolean {
        return try {
            prefs.getBoolean("biometric_enabled", false)
        } catch (e: Exception) {
            Log.e(tag, "Error leyendo preferencia biometric_enabled", e)
            false
        }
    }

    /**
     * Retorna verdadero si la app ya le preguntó al usuario si deseaba activar la biometría tras el login.
     */
    fun hasBeenAskedBiometric(): Boolean {
        return try {
            prefs.getBoolean("has_asked_biometric", false)
        } catch (e: Exception) {
            Log.e(tag, "Error leyendo preferencia has_asked_biometric", e)
            false
        }
    }

    /**
     * Guarda las credenciales del usuario de forma cifrada en el dispositivo y activa la biometría.
     */
    fun saveCredentials(username: String, passwordHash: String) {
        try {
            prefs.edit()
                .putString("saved_username", username.trim().lowercase())
                .putString("saved_pass_hash", passwordHash)
                .putBoolean("biometric_enabled", true)
                .putBoolean("has_asked_biometric", true)
                .apply()
        } catch (e: Exception) {
            Log.e(tag, "Error guardando credenciales biométricas", e)
        }
    }

    /**
     * Marca que ya se le preguntó al usuario sobre la biometría aunque haya rechazado.
     */
    fun markAskedBiometric(enabled: Boolean = false) {
        try {
            prefs.edit()
                .putBoolean("has_asked_biometric", true)
                .putBoolean("biometric_enabled", enabled)
                .apply()
        } catch (e: Exception) {
            Log.e(tag, "Error marcando preferencia de biometría", e)
        }
    }

    fun getSavedUsername(): String? = try {
        prefs.getString("saved_username", null)
    } catch (e: Exception) {
        Log.e(tag, "Error leyendo saved_username", e)
        null
    }

    fun getSavedPasswordHash(): String? = try {
        prefs.getString("saved_pass_hash", null)
    } catch (e: Exception) {
        Log.e(tag, "Error leyendo saved_pass_hash", e)
        null
    }

    /**
     * Borra las credenciales guardadas de forma segura y deshabilita la opción biométrica.
     */
    fun clearCredentials() {
        try {
            prefs.edit()
                .remove("saved_username")
                .remove("saved_pass_hash")
                .putBoolean("biometric_enabled", false)
                .apply()
        } catch (e: Exception) {
            Log.e(tag, "Error limpiando credenciales biométricas", e)
        }
    }

    /**
     * Muestra la ventana nativa de BiometricPrompt para validar la huella o rostro.
     */
    fun autenticar(
        activity: FragmentActivity,
        title: String = "Autenticación Biométrica",
        subtitle: String = "Confirma tu identidad para continuar",
        onSuccess: () -> Unit,
        onError: (String) -> Unit = {},
        onCancel: () -> Unit = {}
    ) {
        try {
            val executor = ContextCompat.getMainExecutor(activity)

            val biometricPrompt = BiometricPrompt(
                activity,
                executor,
                object : BiometricPrompt.AuthenticationCallback() {
                    override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                        super.onAuthenticationSucceeded(result)
                        onSuccess()
                    }

                    override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                        super.onAuthenticationError(errorCode, errString)
                        if (errorCode == BiometricPrompt.ERROR_USER_CANCELED || errorCode == BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                            onCancel()
                        } else {
                            onError(errString.toString())
                        }
                    }

                    override fun onAuthenticationFailed() {
                        super.onAuthenticationFailed()
                        onError("No se reconoció la biometría. Inténtalo de nuevo.")
                    }
                }
            )

            val promptInfo = BiometricPrompt.PromptInfo.Builder()
                .setTitle(title)
                .setSubtitle(subtitle)
                .setNegativeButtonText("Cancelar")
                .setAllowedAuthenticators(
                    BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.BIOMETRIC_WEAK
                )
                .build()

            biometricPrompt.authenticate(promptInfo)
        } catch (e: Exception) {
            Log.e(tag, "Error al lanzar la autenticación biométrica", e)
            onError("No se pudo iniciar la autenticación biométrica: ${e.localizedMessage}")
        }
    }
}
