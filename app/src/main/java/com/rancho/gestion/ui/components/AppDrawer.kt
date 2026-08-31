package com.rancho.gestion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Pets
import androidx.compose.material.icons.filled.Rule
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.vectorResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.Rol
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.ui.theme.RanchoOnPrimary
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoPrimaryVariant
import com.rancho.gestion.ui.theme.RanchoSecondary
import com.rancho.gestion.ui.theme.RanchoError
import com.rancho.gestion.ui.theme.RanchoAccent

private data class DrawerItem(
    val label: String, 
    val route: String, 
    val icon: ImageVector,
    val badgeCount: Int = 0
)

@Composable
fun AppDrawerContent(
    currentRoute: String,
    isAdmin: Boolean,
    usuario: Usuario?,
    onNavigate: (String) -> Unit,
    onLogout: () -> Unit,
    pendientesCount: Int = 0 // Nuevo parámetro para el badge global
) {
    val items = buildList {
        if (isAdmin) {
            add(DrawerItem("Panel", "panel", Icons.Default.Home))
        }
        
        add(DrawerItem("Ganado", "ganado", ImageVector.vectorResource(id = R.drawable.ic_menu_ganado)))
        add(DrawerItem("Vender", "vender", Icons.Default.Sell))
        
        if (isAdmin) {
            add(DrawerItem("Finanzas", "finanzas", Icons.Default.AttachMoney))
            // add(DrawerItem("Aprobaciones", "aprobaciones", Icons.Default.Rule, badgeCount = pendientesCount))
            add(DrawerItem("Reportes", "reportes_menu", Icons.Default.Assessment))
        }
        
        add(DrawerItem("Configuración", "configuracion", Icons.Default.Settings))
    }

    ModalDrawerSheet(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        drawerContainerColor = RanchoSecondary
    ) {
        Column(Modifier.fillMaxHeight()) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RanchoMarkBadge(size = 44.dp, backgroundColor = RanchoPrimary)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            "RANCHO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 2.sp,
                                fontSize = 18.sp
                            ),
                            fontWeight = FontWeight.Black,
                            color = RanchoPrimary
                        )
                        Text(
                            "SAN MARTÍN",
                            style = MaterialTheme.typography.labelMedium.copy(
                                letterSpacing = 1.sp,
                                fontSize = 12.sp
                            ),
                            fontWeight = FontWeight.ExtraBold,
                            color = RanchoAccent
                        )
                    }
                }
                if (usuario != null) {
                    Spacer(Modifier.height(16.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(14.dp))
                            .background(RanchoOnPrimary.copy(alpha = 0.55f))
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(RanchoPrimary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                usuario.nombreUsuario.take(1).uppercase(),
                                color = RanchoOnPrimary,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleSmall
                            )
                        }
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                usuario.nombre.ifBlank { usuario.nombreUsuario },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = RanchoPrimaryVariant
                            )
                            Text(
                                if (usuario.rol == Rol.ADMINISTRADOR) "Administrador" else "Empleado",
                                style = MaterialTheme.typography.bodySmall,
                                color = RanchoPrimaryVariant
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = RanchoPrimaryVariant.copy(alpha = 0.12f))

            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(if (selected) RanchoOnPrimary.copy(alpha = 0.7f) else Color.Transparent)
                            .clickableNoRipple { onNavigate(item.route) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = if (selected) RanchoPrimary else RanchoPrimaryVariant.copy(alpha = 0.7f),
                            modifier = Modifier.size(30.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = RanchoPrimaryVariant,
                            modifier = Modifier.weight(1f)
                        )
                        
                        // Badge de alerta (PROC-11 / Visual)
                        if (item.badgeCount > 0) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(RanchoError),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = item.badgeCount.toString(),
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            HorizontalDivider(color = RanchoPrimaryVariant.copy(alpha = 0.12f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple(onLogout)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = RanchoPrimaryVariant)
                Spacer(Modifier.width(10.dp))
                Text("Cerrar sesión", color = RanchoPrimaryVariant, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Modifier.clickableNoRipple(onClick: () -> Unit): Modifier {
    val interactionSource = remember { MutableInteractionSource() }
    return this.clickable(
        interactionSource = interactionSource,
        indication = null,
        onClick = onClick
    )
}
