package com.rancho.gestion.ui.reportes

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.unit.dp
import com.rancho.gestion.R
import com.rancho.gestion.ui.components.HomeTopBar
import com.rancho.gestion.ui.components.MenuOptionCard

/**
 * Menú "Reportes" — acceso a los reportes de cada módulo (Ganado / Finanzas).
 * Sección administrativa: solo visible/alcanzable para el rol Administrador.
 */
@Composable
fun ReportesMenuScreen(onOpenDrawer: () -> Unit, onGanado: () -> Unit, onFinanzas: () -> Unit) {
    Scaffold(topBar = { HomeTopBar(title = "Reportes", onHomeClick = onOpenDrawer) }) { padding ->
        Column(
            Modifier.padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Spacer(Modifier.height(4.dp))
            MenuOptionCard(
                label = "Ganado",
                icon = ImageVector.vectorResource(id = R.drawable.ic_menu_ganado),
                accentColor = MaterialTheme.colorScheme.primary,
                subtitle = "Entradas, ventas y muertes por periodo",
                onClick = onGanado
            )
            MenuOptionCard(
                label = "Finanzas",
                icon = Icons.Default.AttachMoney,
                accentColor = MaterialTheme.colorScheme.secondary,
                subtitle = "Gastos por categoría y periodo",
                onClick = onFinanzas
            )
        }
    }
}
