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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.border
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.ui.window.Dialog
import com.rancho.gestion.data.entities.FaseGanado
import com.rancho.gestion.data.entities.TipoArete
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
        title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
        navigationIcon = {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "Regresar", tint = MaterialTheme.colorScheme.onBackground)
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
        title = { Text(title, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onBackground) },
        navigationIcon = {
            IconButton(onClick = onHomeClick) {
                Icon(Icons.Default.Home, contentDescription = "Menú", tint = MaterialTheme.colorScheme.onBackground)
            }
        },
        actions = {
            IconButton(onClick = { isSyncing = true }) {
                if (isSyncing) {
                    Icon(
                        imageVector = Icons.Default.Sync,
                        contentDescription = "Sincronizando",
                        modifier = Modifier.rotate(angle),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    LaunchedEffect(isSyncing) {
                        kotlinx.coroutines.delay(3000)
                        isSyncing = false
                    }
                } else {
                    Icon(
                        imageVector = Icons.Default.CloudDone, 
                        contentDescription = "Sincronizado", 
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
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
    accentColor: Color = MaterialTheme.colorScheme.primary
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(vertical = 20.dp, horizontal = 18.dp)) {
            if (icon != null) {
                Box(
                    Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(accentColor.copy(alpha = 0.15f)),
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )
        }
    }
}

/** Contenedor redondeado reutilizado en varias pantallas. */
@Composable
fun SectionCard(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            Modifier.padding(20.dp),
            content = content
        )
    }
}

/** Etiqueta que indica el tipo de arete y su número con colores distintivos. */
@Composable
fun AreteTag(arete: String, tipo: TipoArete = TipoArete.SADER) {
    val (backgroundColor, textColor) = when (tipo) {
        TipoArete.SADER -> Color(0xFFFFD54F) to Color(0xFF0F172A)
        TipoArete.OTRO -> Color(0xFF80CBC4) to Color(0xFF0F172A)
    }

    Box(
        Modifier
            .background(backgroundColor, RoundedCornerShape(50))
            .padding(horizontal = 10.dp, vertical = 4.dp)
    ) {
        Text(
            text = arete, 
            color = textColor, 
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}

/** Insignia compacta con el estado de fase de un animal. */
@Composable
fun EtapaBadge(fase: FaseGanado, modifier: Modifier = Modifier) {
    val (dotColor, containerColor, textColor, label) = when (fase) {
        FaseGanado.ACOPIO -> BadgeColors(MaterialTheme.colorScheme.primary, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer, "Acopio")
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
    accentColor: Color = MaterialTheme.colorScheme.primary,
    tint: Color = accentColor
) {
    ElevatedCard(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.elevatedCardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        elevation = CardDefaults.elevatedCardElevation(defaultElevation = 2.dp),
        onClick = onClick
    ) {
        Row(
            modifier = Modifier.padding(18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(accentColor.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(24.dp))
            }

            Spacer(Modifier.width(16.dp))

            Column(Modifier.weight(1f)) {
                Text(
                    text = label, 
                    style = MaterialTheme.typography.titleMedium, 
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (subtitle != null) {
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = subtitle, 
                        style = MaterialTheme.typography.bodySmall, 
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

/** Barra de progreso con etiqueta usada en Jaulas. */
@Composable
fun LabeledProgressBar(label: String, val1: String, val2: String, progress: Float) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("$val1 / $val2", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        }
        Spacer(Modifier.height(4.dp))
        LinearProgressIndicator(
            progress = { progress.coerceIn(0f, 1f) },
            modifier = Modifier.fillMaxWidth().height(8.dp).clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f)
        )
    }
}

/** Diálogo de confirmación genérico con ícono de advertencia. */
@Composable
fun ConfirmDialog(
    title: String,
    message: String,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    confirmLabel: String = "Sí",
    dismissLabel: String = "No",
    confirmEnabled: Boolean = true,
    icon: ImageVector? = Icons.Default.Warning
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (icon != null) {
                    Icon(
                        imageVector = icon,
                        contentDescription = "Advertencia",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(28.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                }
                Text(
                    title,
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            }
        },
        text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = { 
            Button(
                onClick = { onConfirm() },
                enabled = confirmEnabled
            ) { 
                Text(confirmLabel, fontWeight = FontWeight.Bold) 
            } 
        },
        dismissButton = { 
            OutlinedButton(onClick = onDismiss) { 
                Text(dismissLabel) 
            } 
        }
    )
}

/** Diálogo informativo simple con un solo botón de aceptación. */
@Composable
fun InfoDialog(title: String, message: String, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, color = MaterialTheme.colorScheme.onSurface) },
        text = { Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Aceptar")
            }
        }
    )
}

/** Diálogo modal con barra superior de confirmación para mensajes de éxito. */
@Composable
fun SuccessDialog(
    title: String,
    message: String,
    onContinuar: () -> Unit,
    onSalir: () -> Unit,
    continuarLabel: String = "Continuar",
    salirLabel: String = "Salir a Ganado"
) {
    AlertDialog(
        onDismissRequest = { },
        icon = {
            Box(
                Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(RanchoSuccessContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.Check,
                    contentDescription = null,
                    tint = RanchoSuccess,
                    modifier = Modifier.size(32.dp)
                )
            }
        },
        title = { 
            Text(
                title, 
                style = MaterialTheme.typography.titleLarge, 
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.fillMaxWidth()
            ) 
        },
        text = { 
            Text(
                message, 
                style = MaterialTheme.typography.bodyMedium,
                textAlign = TextAlign.Center,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) 
        },
        confirmButton = {
            Button(
                onClick = onContinuar,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(continuarLabel)
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onSalir,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(salirLabel)
            }
        }
    )
}

/** Pantalla o tarjeta de estado vacío cuando no existen registros. */
@Composable
fun EmptyState(icon: ImageVector, title: String, subtitle: String = "") {
    Column(
        Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(icon, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f))
        Spacer(Modifier.height(16.dp))
        Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface)
        if (subtitle.isNotBlank()) {
            Spacer(Modifier.height(4.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        }
    }
}

/** Formulario dentro de un diálogo modal simple. */
@Composable
fun SimpleFormDialog(
    title: String,
    onDismiss: () -> Unit,
    confirmLabel: String = "Guardar",
    confirmEnabled: Boolean = true,
    onConfirm: () -> Unit,
    content: @Composable ColumnScope.() -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface) },
        text = {
            Column(Modifier.fillMaxWidth()) {
                content()
            }
        },
        confirmButton = {
            Button(onClick = onConfirm, enabled = confirmEnabled) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar")
            }
        }
    )
}

/** Transformación visual para separador de miles con comas. */
class ThousandsSeparatorVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val originalText = text.text
        if (originalText.isEmpty()) return TransformedText(text, OffsetMapping.Identity)

        val parts = originalText.split('.')
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1) "." + parts[1] else ""

        val formattedInteger = StringBuilder()
        var count = 0
        for (i in integerPart.length - 1 downTo 0) {
            formattedInteger.append(integerPart[i])
            count++
            if (count % 3 == 0 && i > 0) {
                formattedInteger.append(',')
            }
        }
        val out = formattedInteger.reverse().toString() + decimalPart

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset <= 0) return 0
                val integerLength = integerPart.length
                val realOffset = offset.coerceAtMost(originalText.length)
                
                return if (realOffset <= integerLength) {
                    var commas = 0
                    for (i in 1 until realOffset) {
                        if ((integerLength - i) % 3 == 0) commas++
                    }
                    realOffset + commas
                } else {
                    val commasInInteger = if (integerLength > 0) (integerLength - 1) / 3 else 0
                    realOffset + commasInInteger
                }
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset <= 0) return 0
                val transformedStr = out
                val integerLength = integerPart.length
                val commasInInteger = if (integerLength > 0) (integerLength - 1) / 3 else 0
                val transformedIntegerLength = integerLength + commasInInteger

                return if (offset <= transformedIntegerLength) {
                    var commasBefore = 0
                    for (i in 0 until offset.coerceAtMost(transformedStr.length)) {
                        if (transformedStr[i] == ',') commasBefore++
                    }
                    (offset - commasBefore).coerceIn(0, integerLength)
                } else {
                    (offset - commasInInteger).coerceIn(0, originalText.length)
                }
            }
        }

        return TransformedText(AnnotatedString(out), offsetMapping)
    }
}
