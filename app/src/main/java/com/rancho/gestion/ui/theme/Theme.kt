package com.rancho.gestion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Paleta de marca de Rancho San Martín.
 * Base: grises azulados + marino (tomados del boceto original) más un
 * acento terracota inspirado en el cuero / fierro de marcar, para que la
 * app tenga una identidad propia y ya no se sienta como un boceto gris.
 */
val RanchoBackground = Color(0xFFEEF1F3)
val RanchoSurface = Color(0xFFFFFFFF)
val RanchoCard = Color(0xFFE1E7EC)
val RanchoPrimary = Color(0xFF2C4A5E)
val RanchoPrimaryVariant = Color(0xFF1B3040)
val RanchoPrimaryContainer = Color(0xFFD7E3EA)
val RanchoSecondary = Color(0xFFC9D3DC)
val RanchoInputFill = Color(0xFFF3F5F6)
val RanchoOnPrimary = Color(0xFFFFFFFF)
val RanchoError = Color(0xFFB3261E)
val RanchoErrorContainer = Color(0xFFF9DEDC)
val RanchoTextPrimary = Color(0xFF1C1C1E)
val RanchoTextSecondary = Color(0xFF5B6670)

/** Colores Rancho San Martín */
val SanMartinBlack = Color(0xFF0F0F0F)
val SanMartinDarkGray = Color(0xFF23272A)
val SanMartinCream = Color(0xFFE6D5B8)
val SanMartinOrange = Color(0xFFD35400)

/** Acento cálido (terracota) — CTAs destacados, marca, alertas intermedias. */
val RanchoAccent = Color(0xFFB5652A)
val RanchoAccentContainer = Color(0xFFF3DCC7)

/** Estados usados por EtapaCalculator (Acopio/Engorda) y las notificaciones locales. */
val RanchoWarning = Color(0xFFB8862E)
val RanchoWarningContainer = Color(0xFFF6E6C6)
val RanchoSuccess = Color(0xFF3D7A54)
val RanchoSuccessContainer = Color(0xFFD9EEDF)

private val RanchoColorScheme = lightColorScheme(
    primary = RanchoPrimary,
    onPrimary = RanchoOnPrimary,
    primaryContainer = RanchoPrimaryContainer,
    onPrimaryContainer = RanchoPrimaryVariant,
    secondary = SanMartinOrange,
    onSecondary = Color.White,
    secondaryContainer = RanchoSecondary,
    onSecondaryContainer = RanchoPrimaryVariant,
    tertiary = SanMartinOrange,
    onTertiary = Color.White,
    tertiaryContainer = RanchoAccentContainer,
    onTertiaryContainer = Color(0xFF5A2E0E),
    background = RanchoBackground,
    onBackground = RanchoTextPrimary,
    surface = RanchoSurface,
    onSurface = RanchoTextPrimary,
    surfaceVariant = RanchoCard,
    onSurfaceVariant = RanchoTextSecondary,
    outline = Color(0xFFA9B4BC),
    outlineVariant = Color(0xFFD3D9DD),
    error = RanchoError,
    onError = Color.White,
    errorContainer = RanchoErrorContainer,
    onErrorContainer = Color(0xFF410E0B)
)

private val RanchoTypography = Typography(
    displaySmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 34.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 24.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Bold, fontSize = 20.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 14.sp),
    bodyLarge = TextStyle(fontSize = 16.sp),
    bodyMedium = TextStyle(fontSize = 14.sp),
    bodySmall = TextStyle(fontSize = 12.sp, color = RanchoTextSecondary),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Bold, fontSize = 11.sp, letterSpacing = 0.4.sp)
)

private val RanchoShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun RanchoTheme(content: @Composable () -> Unit) {
    // El prototipo usa siempre el esquema claro, igual que el boceto original.
    val useDark = isSystemInDarkTheme() && false
    MaterialTheme(
        colorScheme = RanchoColorScheme,
        typography = RanchoTypography,
        shapes = RanchoShapes,
        content = content
    )
}
