package com.rancho.gestion

import android.content.Context
import com.rancho.gestion.data.AppDatabase
import com.rancho.gestion.data.repository.*

import com.rancho.gestion.util.BiometricAuthManager
import com.rancho.gestion.util.ThemeManager

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val biometricAuthManager = BiometricAuthManager(context)
    val themeManager = ThemeManager(context)

    val authRepository = AuthRepository(database.usuarioDao(), database.sincronizacionDao())
    
    val ganadoRepository = GanadoRepository(
        context = context,
        animalDao = database.animalDao(),
        rangoPesoDao = database.rangoPesoDao(),
        mortalidadDao = database.mortalidadDao(),
        sincronizacionDao = database.sincronizacionDao()
    )
    
    val finanzasRepository = FinanzasRepository(
        egresoDao = database.egresoDao(),
        sincronizacionDao = database.sincronizacionDao(),
        context = context
    )
    
    val ventaRepository = VentaRepository(
        ventaDao = database.ventaDao(),
        animalDao = database.animalDao(),
        mortalidadDao = database.mortalidadDao(),
        sincronizacionDao = database.sincronizacionDao(),
        context = context
    )
    
    val configuracionRepository = ConfiguracionRepository(
        rangoPesoDao = database.rangoPesoDao(),
        configuracionDao = database.configuracionDao(),
        sincronizacionDao = database.sincronizacionDao()
    )
}
