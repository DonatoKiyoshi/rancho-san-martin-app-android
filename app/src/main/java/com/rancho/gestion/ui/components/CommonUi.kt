package com.rancho.gestion.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.ui.window.Dialog
import com.rancho.gestion.data.entities.FaseGanado
import com.rancho.gestion.ui.theme.RanchoCard
import com.rancho.gestion.ui.theme.RanchoError
import com.rancho.gestion.ui.theme.RanchoErrorContainer
import com.rancho.gestion.ui.theme.RanchoPrimary
import com.rancho.gestion.ui.theme.RanchoSecondary
import com.rancho.gestion.ui.theme.RanchoSuccess
import com.rancho.gestion.ui.theme.RanchoSuccessContainer
import com.rancho.gestion.ui.theme.RanchoTextSecondary
import com.rancho.gestion.ui.theme.RanchoWarning
import com.rancho.gestion.ui.theme.RanchoWarningContainer

/** Barra superior con flecha de regreso, usada en pantallas "de detalle". */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackTopBar(title: String, onBack: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Regresar")
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Barra superior con ícono de "home" que abre el menú lateral, usada en las pantallas principales. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeTopBar(title: String, onHomeClick: () -> Unit, actions: @Composable RowScope.() -> Unit = {}) {
    var isSyncing by remember { mutableStateOf(false) }
    val infiniteTransition = rememberInfiniteTransition(label = "Sync")
    val angle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "SyncRotation"
    )

    TopAppBar(
        title = { Text(title, fontWeight = FontWeight.Bold) },
        navigationIcon = {
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = "Menú")
            }
        },
        actions = {
            IconButton(onClick = { isSyncing = true }) {
                if (isSyncing) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sincronizando",
                        modifier = Modifier.rotate(angle),
                        tint = RanchoPrimary
                    )
                    LaunchedEffect(isSyncing) {
                        kotlinx.coroutines.delay(3000)
                        isSyncing = false
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDone, 
                        contentDescription = "Sincronizado", 
                        tint = RanchoTextSecondary.copy(alpha = 0.5f)
                    )
                }
            }
            actions()
        },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background)
    )
}

/** Tarjeta de estadística tipo "Cabezas / Toneladas / Pesos" del Panel y Finanzas. */
@Composable
fun StatCard(
    titulo: String,
    valor: String,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    accentColor: Color = RanchoPrimary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = RanchoCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp)
    ) {
        Column(Modifier.padding(vertical = 20.dp, horizontal = 18.dp)) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.12f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(12.dp))
            }
            
            AnimatedContent(
                targetState = valor,
                transitionSpec = {
                    (slideInVertically { height -> height } + fadeIn() togetherWith
                     slideOutVertically { height -> -height } + fadeOut())
                        .using(SizeTransform(clip = false))
                },
                label = "StatValueAnimation"
            ) { targetValor ->
                Text(
                    text = targetValor,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            
            Spacer(Modifier.height(4.dp))
            Text(
                text = titulo, 
                style = MaterialTheme.typography.labelMedium, 
                color = RanchoTextSecondary,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/** Contenedor redondeado reutilizado en varias pantallas (Configuracion, Jaulas, etc). */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = RanchoCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 4.dp)
    ) {
        Column(
            Modifier.padding(20.dp),
            content = content
        )
    }
}

/** Etiqueta oscura tipo "MX-01002" de la lista de Ganado. */
@Composable
fun AreteTag(arete: String) {
    Box(
        Modifier
            .background(RanchoPrimary, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(arete, color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelLarge)
    }
}

/** Insignia compacta con el estado de fase de un animal. */
@Composable
fun EtapaBadge(fase: FaseGanado, modifier: Modifier = Modifier) {
    val (dotColor, containerColor, textColor, label) = when (fase) {
        FaseGanado.ACOPIO -> BadgeColors(RanchoPrimary, RanchoSecondary, RanchoPrimary, "Acopio")
        FaseGanado.DESARROLLO -> BadgeColors(RanchoSuccess, RanchoSuccessContainer, RanchoSuccess, "Desarrollo")
    }
    Surface(color = containerColor, shape = RoundedCornerShape(50), modifier = modifier) {
        Row(
            Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(dotColor))
            Spacer(Modifier.width(5.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = textColor)
        }
    }
}

private data class BadgeColors(val dot: Color, val container: Color, val text: Color, val label: String)

/** Tarjeta de opción de menú con ícono + chevron, usada en Vender y Reportes. */
@Composable
fun MenuOptionCard(
    label: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    subtitle: String? = null,
    accentColor: Color = RanchoPrimary
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = RanchoCard),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 3.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier
                .padding(vertical = 18.dp, horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(46.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.14f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = accentColor)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(subtitle, style = MaterialTheme.typography.bodySmall, color = RanchoTextSecondary)
                }
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = RanchoTextSecondary)
        }
    }
}

/** Estado vacío ilustrado (ícono + texto), usado en listas sin resultados. */
@Composable
fun EmptyState(
    icon: ImageVector,
    title: String,
    modifier: Modifier = Modifier,
    subtitle: String? = null
) {
    Column(
        modifier
            .fillMaxWidth()
            .padding(vertical = 40.dp, horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(RanchoCard),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, contentDescription = null, tint = RanchoTextSecondary, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(16.dp))
        Text(
            title,
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center
        )
        if (subtitle != null) {
            Spacer(Modifier.height(4.dp))
            Text(
                subtitle,
                style = MaterialTheme.typography.bodyMedium,
                color = RanchoTextSecondary,
                textAlign = TextAlign.Center
            )
        }
    }
}

/** Barra de progreso etiquetada, usada en la pantalla de Jaulas (Cabezas / Toneladas). */
@Composable
fun LabeledProgressBar(label: String, actual: String, total: String, progress: Float) {
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text("$actual/$total", style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier
                .fillMaxWidth()
                .height(10.dp)
                .clip(RoundedCornerShape(50)),
            color = RanchoPrimary,
            trackColor = MaterialTheme.colorScheme.surface
        )
    }
}

/** Diálogo de confirmación genérico (Sí / No), como el de Registrar Muerte. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Sí",
    dismissLabel: String = "No",
    confirmEnabled: Boolean = true
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { Text(message) },
        confirmButton = { 
            Button(
                onClick = { onConfirm() },
                enabled = confirmEnabled
            ) { 
                Text(confirmLabel) 
            } 
        },
        dismissButton = { 
            OutlinedButton(onClick = onDismiss) { 
                Text(dismissLabel) 
            } 
        }
    )
}

/** Diálogo simple para formularios cortos (Nueva Jaula, Registrar Egreso, etc). */
@Composable
fun SimpleFormDialog(
    title: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit,
    confirmLabel: String = "Guardar",
    confirmEnabled: Boolean = true,
    content: @Composable ColumnScope.() -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surface) {
            Column(Modifier.padding(20.dp)) {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Spacer(Modifier.height(12.dp))
                content()
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    TextButton(onClick = onDismiss) { Text("Cancelar") }
                    Spacer(Modifier.width(8.dp))
                    Button(onClick = onConfirm, enabled = confirmEnabled) { Text(confirmLabel) }
                }
            }
        }
    }
}

/** Diálogo de éxito con check verde, usado tras registros o ventas. */
@Composable
fun SuccessDialog(
    title: String = "¡Operación Exitosa!",
    message: String,
    onContinuar: () -> Unit,
    onSalir: () -> Unit,
    continuarLabel: String = "Continuar"
) {
    Dialog(onDismissRequest = {}) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            modifier = Modifier.width(320.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Green Check Icon
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .border(4.dp, RanchoSuccess, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Check,
                        contentDescription = null,
                        tint = RanchoSuccess,
                        modifier = Modifier.size(48.dp)
                    )
                }

                Spacer(Modifier.height(24.dp))

                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Spacer(Modifier.height(12.dp))

                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center,
                    color = RanchoTextSecondary
                )

                Spacer(Modifier.height(32.dp))

                Button(
                    onClick = onContinuar,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50),
                    colors = ButtonDefaults.buttonColors(containerColor = RanchoPrimary)
                ) {
                    Text(continuarLabel, fontWeight = FontWeight.Bold)
                }

                Spacer(Modifier.height(12.dp))

                OutlinedButton(
                    onClick = onSalir,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                    shape = RoundedCornerShape(50)
                ) {
                    Text("Salir", fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

/** Diálogo de advertencia/información (ej. validación de fase Desarrollo). */
@Composable
fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Default.Info, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(48.dp)) },
        title = { Text(title, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        text = { Text(message, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) },
        confirmButton = {
            Button(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) {
                Text("Entendido")
            }
        }
    )
}
