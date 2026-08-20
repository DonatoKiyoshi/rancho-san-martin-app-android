package com.rancho.gestion.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rancho.gestion.R

/**
 * Marca de la app (silueta del arete de ganado) reutilizada dentro de la UI
 * de Compose. Es exactamente el mismo dibujo vectorial que el ícono del
 * launcher (res/drawable/ic_launcher_foreground.xml) y el de las
 * notificaciones, para que la marca sea consistente en todos lados: ícono,
 * splash, pantalla de Login y encabezado del menú lateral.
 */
@Composable
fun RanchoMark(modifier: Modifier = Modifier, tint: Color = Color.White) {
    Icon(
        painter = painterResource(id = R.drawable.ic_logo_marca),
        contentDescription = null,
        tint = tint,
        modifier = modifier
    )
}

/** [RanchoMark] dentro de un círculo de color — usado como "logo" en Login y en el menú. */
@Composable
fun RanchoMarkBadge(
    modifier: Modifier = Modifier,
    size: Dp = 64.dp,
    backgroundColor: Color = MaterialTheme.colorScheme.primary,
    tint: Color = Color.White
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(backgroundColor),
        contentAlignment = Alignment.Center
    ) {
        RanchoMark(modifier = Modifier.size(size * 0.52f), tint = tint)
    }
}
