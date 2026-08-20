package com.rancho.gestion.ui.vender

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HeartBroken
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.rancho.gestion.ui.components.BackTopBar
import com.rancho.gestion.ui.components.MenuOptionCard
import com.rancho.gestion.ui.theme.RanchoAccent
import com.rancho.gestion.ui.theme.RanchoError
import com.rancho.gestion.ui.theme.RanchoPrimary

/**
 * Menú "Vender" — tres salidas de ganado según el boceto:
 * Venta Individual, Jaula (venta por lote) y Registrar Muerte.
 */
@Composable
fun VenderMenuScreen(
    onBack: () -> Unit,
    onVentaIndividual: () -> Unit,
    onJaula: () -> Unit,
    onRegistrarMuerte: () -> Unit
) {
    Scaffold(topBar = { BackTopBar(title = "Vender", onBack = onBack) }) { padding ->
        Column(
            Modifier
                .padding(padding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            MenuOptionCard(
                label = "Venta Individual",
                icon = Icons.Default.Sell,
                accentColor = RanchoPrimary,
                subtitle = "Vender un solo animal por su arete",
                onClick = onVentaIndividual
            )
            MenuOptionCard(
                label = "Jaula",
                icon = Icons.Default.Inventory2,
                accentColor = RanchoAccent,
                subtitle = "Vender todos los animales de un lote",
                onClick = onJaula
            )
            MenuOptionCard(
                label = "Registrar Muerte",
                icon = Icons.Default.HeartBroken,
                accentColor = RanchoError,
                subtitle = "Da de baja un animal y calcula la merma",
                onClick = onRegistrarMuerte
            )
        }
    }
}
