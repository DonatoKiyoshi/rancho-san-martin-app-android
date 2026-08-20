package com.rancho.gestion

import android.content.Context
import com.rancho.gestion.data.AppDatabase
import com.rancho.gestion.data.repository.*

class AppContainer(context: Context) {
    private val database = AppDatabase.getInstance(context)

    val authRepository = AuthRepository(database.usuarioDao())
    
    val ganadoRepository = GanadoRepository(
        animalDao = database.animalDao(),
        rangoPesoDao = database.rangoPesoDao(),
        mortalidadDao = database.mortalidadDao()
    )
    
    val finanzasRepository = FinanzasRepository(
        egresoDao = database.egresoDao()
    )
    
    val ventaRepository = VentaRepository(
        ventaDao = database.ventaDao(),
        animalDao = database.animalDao(),
        mortalidadDao = database.mortalidadDao()
    )
    
    val configuracionRepository = ConfiguracionRepository(
        rangoPesoDao = database.rangoPesoDao(),
        configuracionDao = database.configuracionDao()
    )
}
