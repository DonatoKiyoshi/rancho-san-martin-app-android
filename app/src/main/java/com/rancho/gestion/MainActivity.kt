package com.rancho.gestion

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.fragment.app.FragmentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import com.rancho.gestion.ui.navigation.RanchoNavHost
import com.rancho.gestion.ui.theme.RanchoTheme

class MainActivity : FragmentActivity() {

    private val solicitarPermisoNotificaciones =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        pedirPermisoDeNotificacionesSiHaceFalta()

        val container = (application as RanchoApp).container
        setContent {
            RanchoTheme {
                RanchoNavHost(container = container)
            }
        }
    }

    private fun pedirPermisoDeNotificacionesSiHaceFalta() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val yaConcedido = ContextCompat.checkSelfPermission(
                this, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
            if (!yaConcedido) {
                solicitarPermisoNotificaciones.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }
}
