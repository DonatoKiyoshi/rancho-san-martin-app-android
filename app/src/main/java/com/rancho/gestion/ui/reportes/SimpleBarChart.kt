package com.rancho.gestion.ui.reportes

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Gráfico de barras simple dibujado con Canvas — no requiere ninguna
 * librería externa. Cumple el requisito de "generar gráficos estadísticos"
 * de los reportes sin agregar dependencias pesadas al proyecto.
 */
@Composable
fun SimpleBarChart(
    data: List<Pair<String, Double>>,
    modifier: Modifier = Modifier,
    barColor: Color = MaterialTheme.colorScheme.primary
) {
    val maxValue = (data.maxOfOrNull { it.second } ?: 0.0).coerceAtLeast(1.0)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val labelReservePx = with(density) { 22.dp.toPx() }

    Column(modifier.fillMaxWidth()) {
        Canvas(
            Modifier
                .fillMaxWidth()
                .height(190.dp)
                .padding(vertical = 8.dp)
        ) {
            if (data.isEmpty()) return@Canvas
            val chartBottom = size.height - labelReservePx
            val barSpacing = size.width / data.size
            val barWidth = barSpacing * 0.55f

            // Eje base
            drawLine(
                color = Color.Gray,
                start = Offset(0f, chartBottom),
                end = Offset(size.width, chartBottom),
                strokeWidth = 2f
            )

            data.forEachIndexed { index, (label, value) ->
                val barHeight = (value / maxValue).toFloat() * (chartBottom - 8f)
                val left = index * barSpacing + (barSpacing - barWidth) / 2
                drawRoundRect(
                    color = barColor,
                    topLeft = Offset(left, chartBottom - barHeight),
                    size = Size(barWidth, barHeight),
                    cornerRadius = CornerRadius(8f, 8f)
                )
                val measured = textMeasurer.measure(
                    text = label,
                    style = TextStyle(fontSize = 10.sp, color = Color.DarkGray)
                )
                drawText(
                    textLayoutResult = measured,
                    topLeft = Offset(
                        (left + barWidth / 2 - measured.size.width / 2).coerceAtLeast(0f),
                        chartBottom + 4f
                    )
                )
            }
        }
    }
}
