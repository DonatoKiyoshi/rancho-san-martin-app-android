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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rancho.gestion.R
import com.rancho.gestion.data.entities.Rol
import com.rancho.gestion.data.entities.Usuario
import com.rancho.gestion.ui.theme.RanchoError
import com.rancho.gestion.ui.theme.SanMartinOrange

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
    pendientesCount: Int = 0
) {
    val items = buildList {
        if (isAdmin) {
            add(DrawerItem("Panel", "panel", Icons.Default.Home))
        }
        
        add(DrawerItem("Ganado", "ganado", ImageVector.vectorResource(id = R.drawable.ic_menu_ganado)))
        add(DrawerItem("Vender", "vender", Icons.Default.Sell))
        
        if (isAdmin) {
            add(DrawerItem("Finanzas", "finanzas", Icons.Default.AttachMoney))
            add(DrawerItem("Reportes", "reportes_menu", Icons.Default.Assessment))
        }
        
        add(DrawerItem("Configuración", "configuracion", Icons.Default.Settings))
    }

    val surfaceColor = MaterialTheme.colorScheme.surface
    val isDark = surfaceColor.red < 0.2f

    // COLORES EXACTOS DEL DISEÑO DE MARCA
    val goldColor = Color(0xFFCBB280) // Tono dorado/crema elegante
    val darkBadgeBg = if (isDark) Color(0xFF18222D) else Color(0xFF222E3A)
    val logoTint = Color(0xFFD35400) // Terracota marca
    
    val ranchoTextColor = if (isDark) Color(0xFFE6D5B8) else goldColor
    val sanMartinTextColor = if (isDark) Color(0xFFFB923C) else goldColor
    val lineDashColor = if (isDark) Color(0xFFE6D5B8).copy(alpha = 0.6f) else goldColor

    val userCardBg = if (isDark) MaterialTheme.colorScheme.surfaceVariant else Color(0xFFE2E8ED)
    val userAvatarBg = if (isDark) Color(0xFF2A221B) else Color(0xFF222E3A)
    val userAvatarText = if (isDark) Color(0xFFE6D5B8) else goldColor
    val userNameColor = if (isDark) MaterialTheme.colorScheme.onSurface else Color(0xFF1C1C1E)
    val userRoleColor = if (isDark) MaterialTheme.colorScheme.onSurfaceVariant else Color(0xFF6A7580)

    ModalDrawerSheet(
        modifier = Modifier
            .width(280.dp)
            .fillMaxHeight(),
        drawerContainerColor = surfaceColor
    ) {
        Column(Modifier.fillMaxHeight()) {
            Column(Modifier.padding(horizontal = 20.dp, vertical = 24.dp)) {
                // LOGO RANCHO SAN MARTÍN
                Row(verticalAlignment = Alignment.CenterVertically) {
                    RanchoMarkBadge(
                        size = 52.dp,
                        backgroundColor = darkBadgeBg,
                        tint = logoTint
                    )
                    Spacer(Modifier.width(14.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "RANCHO",
                            style = MaterialTheme.typography.titleMedium.copy(
                                letterSpacing = 5.sp,
                                fontSize = 18.sp
                            ),
                            fontWeight = FontWeight.Normal,
                            textAlign = TextAlign.Center,
                            color = ranchoTextColor
                        )
                        Spacer(Modifier.height(2.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            HorizontalDivider(
                                modifier = Modifier.width(14.dp),
                                thickness = 1.dp,
                                color = lineDashColor
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(
                                text = "SAN MARTÍN",
                                style = MaterialTheme.typography.labelMedium.copy(
                                    letterSpacing = 1.2.sp,
                                    fontSize = 12.sp
                                ),
                                fontWeight = FontWeight.ExtraBold,
                                color = sanMartinTextColor
                            )
                            Spacer(Modifier.width(4.dp))
                            HorizontalDivider(
                                modifier = Modifier.width(14.dp),
                                thickness = 1.dp,
                                color = lineDashColor
                            )
                        }
                    }
                }

                // TARJETA DE USUARIO ACTUAL
                if (usuario != null) {
                    Spacer(Modifier.height(20.dp))
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(18.dp))
                            .background(userCardBg)
                            .padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(userAvatarBg),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = usuario.nombreUsuario.take(1).uppercase(),
                                color = userAvatarText,
                                fontWeight = FontWeight.Bold,
                                style = MaterialTheme.typography.titleMedium
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                text = usuario.nombre.ifBlank { usuario.nombreUsuario },
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = FontWeight.Bold,
                                color = userNameColor
                            )
                            Spacer(Modifier.height(1.dp))
                            Text(
                                text = if (usuario.rol == Rol.ADMINISTRADOR) "Administrador" else "Empleado",
                                style = MaterialTheme.typography.bodySmall,
                                color = userRoleColor
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))

            Column(Modifier.padding(horizontal = 12.dp, vertical = 12.dp)) {
                items.forEach { item ->
                    val selected = currentRoute == item.route
                    val itemBg = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent
                    val itemContent = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(itemBg)
                            .clickableNoRipple { onNavigate(item.route) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            item.icon,
                            contentDescription = null,
                            tint = itemContent,
                            modifier = Modifier.size(26.dp)
                        )
                        Spacer(Modifier.width(16.dp))
                        Text(
                            text = item.label,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.Medium,
                            color = itemContent,
                            modifier = Modifier.weight(1f)
                        )
                        
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

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.25f))
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickableNoRipple(onLogout)
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(Icons.Default.ExitToApp, contentDescription = null, tint = MaterialTheme.colorScheme.onSurface)
                Spacer(Modifier.width(10.dp))
                Text("Cerrar sesión", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Bold)
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
