package com.rancho.gestion.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
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
 * acento terracota inspirado en el cuero / fierro de marcar.
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

// --- PALETA OSCURA PARA PANTALLAS OLED / AMOLED DE ALTO CONTRASTE ---
val RanchoDarkBackground = Color(0xFF000000) // Negro puro OLED
val RanchoDarkSurface = Color(0xFF111827)    // Gris oscuro nocturno
val RanchoDarkCard = Color(0xFF1F2937)       // Contenedor tarjeta elevado
val RanchoDarkPrimary = Color(0xFF38BDF8)    // Azul San Martín brillante para alto contraste
val RanchoDarkPrimaryVariant = Color(0xFF0284C7)
val RanchoDarkPrimaryContainer = Color(0xFF1E3A8A)
val RanchoDarkSecondary = Color(0xFFFB923C)  // Naranja San Martín brillante
val RanchoDarkTextPrimary = Color(0xFFF8FAFC) // Blanco crema
val RanchoDarkTextSecondary = Color(0xFF94A3B8) // Gris neutro secundario

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

private val RanchoDarkColorScheme = darkColorScheme(
    primary = RanchoDarkPrimary,
    onPrimary = Color.Black,
    primaryContainer = RanchoDarkPrimaryContainer,
    onPrimaryContainer = Color.White,
    secondary = RanchoDarkSecondary,
    onSecondary = Color.Black,
    secondaryContainer = Color(0xFF374151),
    onSecondaryContainer = Color.White,
    tertiary = RanchoDarkSecondary,
    onTertiary = Color.Black,
    tertiaryContainer = Color(0xFF451A03),
    onTertiaryContainer = Color(0xFFFFEDD5),
    background = RanchoDarkBackground,
    onBackground = RanchoDarkTextPrimary,
    surface = RanchoDarkSurface,
    onSurface = RanchoDarkTextPrimary,
    surfaceVariant = RanchoDarkCard,
    onSurfaceVariant = RanchoDarkTextSecondary,
    outline = Color(0xFF475569),
    outlineVariant = Color(0xFF334155),
    error = Color(0xFFF87171),
    onError = Color.Black,
    errorContainer = Color(0xFF7F1D1D),
    onErrorContainer = Color(0xFFFEE2E2)
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
fun RanchoTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    val colorScheme = if (darkTheme) RanchoDarkColorScheme else RanchoColorScheme
    MaterialTheme(
        colorScheme = colorScheme,
        typography = RanchoTypography,
        shapes = RanchoShapes,
        content = content
    )
}
