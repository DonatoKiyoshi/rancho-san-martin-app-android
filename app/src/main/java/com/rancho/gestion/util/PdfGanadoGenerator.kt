package com.rancho.gestion.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.TextPaint
import com.rancho.gestion.data.entities.EstadoInventario
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

object PdfGanadoGenerator {

    private val colorPrimary = Color.parseColor("#0F172A")
    private val colorAccent = Color.parseColor("#D35400")
    private val colorTextDark = Color.parseColor("#1E293B")
    private val colorTextMuted = Color.parseColor("#64748B")
    private val colorBgLight = Color.parseColor("#F8FAFC")
    private val colorHeaderBg = Color.parseColor("#1E293B")
    private val colorBorder = Color.parseColor("#CBD5E1")

    // Colores de estados
    private val colorActivo = Color.parseColor("#166534")
    private val colorVendido = Color.parseColor("#1E40AF")
    private val colorMuerto = Color.parseColor("#991B1B")

    fun generatePdf(
        context: Context,
        file: File,
        exportData: ReporteGanadoExportData
    ): Boolean {
        return try {
            val document = PdfDocument()
            // A4 Horizontal (842 x 595 pt)
            val pageWidth = 842
            val pageHeight = 595
            val margin = 35f
            val tableWidth = pageWidth - (margin * 2)

            val textPaint = TextPaint().apply {
                color = colorTextDark
                textSize = 9f
                isAntiAlias = true
            }
            val boldPaint = TextPaint().apply {
                color = colorTextDark
                textSize = 9f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val headerPaint = TextPaint().apply {
                color = Color.WHITE
                textSize = 9.5f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val bgEvenPaint = Paint().apply { color = Color.WHITE }
            val bgOddPaint = Paint().apply { color = colorBgLight }
            val borderPaint = Paint().apply { color = colorBorder; strokeWidth = 0.5f }

            var pageNum = 1
            val totalPaginasEstimadas = calcularPaginasTotales(exportData.tablaRows.size)

            var pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            var page = document.startPage(pageInfo)
            var canvas = page.canvas

            // --- PÁGINA 1: Encabezado y comienzo de Tabla Tabular ---
            var yPos = drawReportHeader(canvas, exportData, margin, tableWidth)
            yPos = drawTableHeader(canvas, yPos + 10f, margin, headerPaint)

            val rowHeight = 22f

            for ((index, row) in exportData.tablaRows.withIndex()) {
                if (yPos + rowHeight > pageHeight - 45f) {
                    drawFooter(canvas, pageNum, totalPaginasEstimadas, exportData, margin, pageWidth, pageHeight)
                    document.finishPage(page)

                    pageNum++
                    pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                    page = document.startPage(pageInfo)
                    canvas = page.canvas

                    yPos = drawSecondaryHeader(canvas, exportData, margin, tableWidth)
                    yPos = drawTableHeader(canvas, yPos + 8f, margin, headerPaint)
                }

                val rowBg = if (index % 2 == 0) bgEvenPaint else bgOddPaint
                canvas.drawRect(margin, yPos, margin + tableWidth, yPos + rowHeight, rowBg)
                canvas.drawLine(margin, yPos + rowHeight, margin + tableWidth, yPos + rowHeight, borderPaint)

                val textY = yPos + 14f

                // Columna 1: Número Arete
                canvas.drawText(row.numeroArete, margin + 5f, textY, boldPaint)
                // Columna 2: Tipo Arete
                canvas.drawText(row.tipoArete, margin + 120f, textY, textPaint)
                // Columna 3: Fecha Entrada
                canvas.drawText(row.fechaEntradaTexto, margin + 185f, textY, textPaint)
                // Columna 4: Color
                canvas.drawText(row.color, margin + 255f, textY, textPaint)
                // Columna 5: Sexo
                canvas.drawText(row.sexo, margin + 320f, textY, textPaint)

                // Columna 6: Estado (Con color distintivo)
                val estadoPaint = Paint(textPaint).apply {
                    color = when (row.estado) {
                        "Activo" -> colorActivo
                        "Vendido" -> colorVendido
                        else -> colorMuerto
                    }
                    typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                }
                canvas.drawText(row.estado, margin + 375f, textY, estadoPaint)

                // Columna 7: Peso Entrada
                canvas.drawText(row.pesoEntradaTexto, margin + 445f, textY, textPaint)
                // Columna 8: Peso Salida
                canvas.drawText(row.pesoSalidaTexto, margin + 520f, textY, textPaint)
                // Columna 9: Rango Peso
                canvas.drawText(row.rangoPeso, margin + 595f, textY, textPaint)
                // Columna 10: Precio Compra
                canvas.drawText(row.precioCompraTexto, margin + 680f, textY, textPaint)

                yPos += rowHeight
            }

            // Fila de Totales de la Tabla
            if (yPos + 25f > pageHeight - 45f) {
                drawFooter(canvas, pageNum, totalPaginasEstimadas, exportData, margin, pageWidth, pageHeight)
                document.finishPage(page)

                pageNum++
                pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
                page = document.startPage(pageInfo)
                canvas = page.canvas
                yPos = 40f
            }

            drawTableTotals(canvas, yPos, margin, tableWidth, exportData, boldPaint)

            drawFooter(canvas, pageNum, totalPaginasEstimadas, exportData, margin, pageWidth, pageHeight)
            document.finishPage(page)

            // --- SECCIÓN FINAL EN NUEVA PÁGINA: "GRÁFICOS Y RESUMEN" ---
            pageNum++
            pageInfo = PdfDocument.PageInfo.Builder(pageWidth, pageHeight, pageNum).create()
            page = document.startPage(pageInfo)
            canvas = page.canvas

            drawAnalyticsSectionPage(canvas, exportData, margin, pageWidth, pageHeight)
            drawFooter(canvas, pageNum, totalPaginasEstimadas, exportData, margin, pageWidth, pageHeight)
            document.finishPage(page)

            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            document.close()
            true
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("PdfGanadoGenerator", "Error al generar PDF de Ganado", e)
            false
        }
    }

    private fun calcularPaginasTotales(numRegistros: Int): Int {
        val registrosPrimeraPagina = 18
        val registrosPaginaIntermedia = 22
        val paginasTabla = if (numRegistros <= registrosPrimeraPagina) 1 else {
            1 + kotlin.math.ceil((numRegistros - registrosPrimeraPagina) / registrosPaginaIntermedia.toDouble()).toInt()
        }
        return paginasTabla + 1 // +1 página final para Gráficos y Resumen
    }

    private fun drawReportHeader(canvas: Canvas, data: ReporteGanadoExportData, margin: Float, width: Float): Float {
        var y = margin + 15f

        // Nombre Rancho
        val titlePaint = Paint().apply {
            color = colorPrimary
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText(data.nombreRancho, margin, y, titlePaint)

        // Título del Reporte
        val subtitlePaint = Paint().apply {
            color = colorAccent
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Reporte de Ganado e Inventario", margin, y + 18f, subtitlePaint)

        // Metadata de Generación (Alineada a la derecha)
        val metaPaint = Paint().apply {
            color = colorTextMuted
            textSize = 9f
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        canvas.drawText("Generado: ${data.fechaGeneracionTxt}", margin + width, y, metaPaint)
        canvas.drawText("Periodo: ${data.periodoTxt}", margin + width, y + 14f, metaPaint)

        y += 38f

        // Caja de Filtros Aplicados
        val filterBoxPaint = Paint().apply { color = Color.parseColor("#F1F5F9") }
        canvas.drawRoundRect(margin, y, margin + width, y + 26f, 6f, 6f, filterBoxPaint)

        val filterTextPaint = Paint().apply {
            color = colorTextDark
            textSize = 8.5f
            isAntiAlias = true
        }
        canvas.drawText("Filtros Aplicados: ${data.filtrosAplicadosTxt}  |  Total Registros: ${data.totalRegistros}", margin + 10f, y + 16f, filterTextPaint)

        return y + 32f
    }

    private fun drawSecondaryHeader(canvas: Canvas, data: ReporteGanadoExportData, margin: Float, width: Float): Float {
        val y = margin + 10f
        val paint = Paint().apply {
            color = colorTextMuted
            textSize = 8.5f
            isAntiAlias = true
        }
        canvas.drawText("${data.nombreRancho} — Reporte de Ganado (Continuación)", margin, y, paint)
        canvas.drawLine(margin, y + 5f, margin + width, y + 5f, Paint().apply { color = colorBorder; strokeWidth = 0.5f })
        return y + 12f
    }

    private fun drawTableHeader(canvas: Canvas, y: Float, margin: Float, headerPaint: TextPaint): Float {
        val height = 22f
        val bgPaint = Paint().apply { color = colorHeaderBg }
        canvas.drawRect(margin, y, margin + 772f, y + height, bgPaint)

        val textY = y + 14f
        canvas.drawText("Número Arete", margin + 5f, textY, headerPaint)
        canvas.drawText("Tipo Arete", margin + 120f, textY, headerPaint)
        canvas.drawText("Fecha Ent.", margin + 185f, textY, headerPaint)
        canvas.drawText("Color", margin + 255f, textY, headerPaint)
        canvas.drawText("Sexo", margin + 320f, textY, headerPaint)
        canvas.drawText("Estado", margin + 375f, textY, headerPaint)
        canvas.drawText("Peso Ent.", margin + 445f, textY, headerPaint)
        canvas.drawText("Peso Sal.", margin + 520f, textY, headerPaint)
        canvas.drawText("Rango Peso", margin + 595f, textY, headerPaint)
        canvas.drawText("P. Compra", margin + 680f, textY, headerPaint)

        return y + height
    }

    private fun drawTableTotals(
        canvas: Canvas,
        y: Float,
        margin: Float,
        width: Float,
        data: ReporteGanadoExportData,
        boldPaint: TextPaint
    ) {
        val bgPaint = Paint().apply { color = Color.parseColor("#E2E8F0") }
        canvas.drawRect(margin, y, margin + width, y + 24f, bgPaint)

        val textY = y + 16f
        val locale = Locale.getDefault()
        canvas.drawText("TOTALES ACUMULADOS", margin + 10f, textY, boldPaint)

        val pesoEntTotalTxt = "${String.format(locale, "%,.0f", data.stats.pesoTotal)} kg"
        canvas.drawText(pesoEntTotalTxt, margin + 445f, textY, boldPaint)

        val pesoSalTotal = data.tablaRows.mapNotNull { it.pesoSalida }.sum()
        val pesoSalTxt = if (data.tablaRows.any { it.pesoSalida != null }) "${String.format(locale, "%,.0f", pesoSalTotal)} kg" else "N/A"
        canvas.drawText(pesoSalTxt, margin + 520f, textY, boldPaint)

        val precioCompraTotal = data.tablaRows.sumOf { it.precioCompra }
        val precioCompraTxt = "$${String.format(locale, "%,.2f", precioCompraTotal)}"
        canvas.drawText(precioCompraTxt, margin + 680f, textY, boldPaint)
    }

    private fun drawAnalyticsSectionPage(
        canvas: Canvas,
        data: ReporteGanadoExportData,
        margin: Float,
        pageWidth: Int,
        pageHeight: Int
    ) {
        var y = margin + 15f
        val locale = Locale.getDefault()

        // Título Sección
        val secTitlePaint = Paint().apply {
            color = colorPrimary
            textSize = 16f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("Gráficos y Resumen Analítico del Hato", margin, y, secTitlePaint)

        val subPaint = Paint().apply {
            color = colorTextMuted
            textSize = 9f
            isAntiAlias = true
        }
        canvas.drawText("Análisis estadístico basado en los ${data.totalRegistros} animales incluidos en el filtro.", margin, y + 14f, subPaint)

        y += 35f

        // BLOQUES KPIS
        val kpiBoxWidth = 115f
        val kpiBoxHeight = 45f
        val stats = data.stats

        drawKpiBox(canvas, margin, y, kpiBoxWidth, kpiBoxHeight, "Total Animales", "${stats.totalAnimales}", "cabezas")
        drawKpiBox(canvas, margin + 125f, y, kpiBoxWidth, kpiBoxHeight, "Peso Total", "${String.format(locale, "%,.0f", stats.pesoTotal)} kg", "acumulado")
        drawKpiBox(canvas, margin + 250f, y, kpiBoxWidth, kpiBoxHeight, "Peso Promedio", "${String.format(locale, "%,.1f", stats.pesoPromedio)} kg", "por cabeza")
        drawKpiBox(canvas, margin + 375f, y, kpiBoxWidth, kpiBoxHeight, "Activos", "${stats.totalActivos}", "${String.format(locale, "%.1f", stats.pctActivos)}%")
        drawKpiBox(canvas, margin + 500f, y, kpiBoxWidth, kpiBoxHeight, "Vendidos", "${stats.totalVendidos}", "${String.format(locale, "%.1f", stats.pctVendidos)}%")
        drawKpiBox(canvas, margin + 625f, y, kpiBoxWidth, kpiBoxHeight, "Muertes", "${stats.totalMuertos}", "${String.format(locale, "%.1f", stats.pctMuertos)}%")

        y += 60f

        // DIBUJO DE GRÁFICO 1: RANGOS DE PESO (Barras Horizontales)
        canvas.drawText("Distribución por Rango de Peso", margin, y, secTitlePaint.apply { textSize = 12f })
        y += 12f

        val barHeight = 12f
        val maxBarWidth = 350f

        stats.distribucionRangos.take(5).forEach { rango ->
            y += 8f
            canvas.drawText(rango.nombreRango, margin, y + 10f, subPaint.apply { color = colorTextDark; typeface = Typeface.DEFAULT_BOLD })

            val pct = (rango.porcentaje / 100.0).toFloat().coerceIn(0f, 1f)
            val barW = maxBarWidth * pct

            // Track background
            canvas.drawRoundRect(margin + 120f, y, margin + 120f + maxBarWidth, y + barHeight, 6f, 6f, Paint().apply { color = Color.parseColor("#E2E8ED") })
            // Fill bar
            if (barW > 0f) {
                canvas.drawRoundRect(margin + 120f, y, margin + 120f + barW, y + barHeight, 6f, 6f, Paint().apply { color = colorPrimary })
            }

            val valTxt = "${rango.cantidad} cab. (${String.format(locale, "%.1f", rango.porcentaje)}%)"
            canvas.drawText(valTxt, margin + 120f + maxBarWidth + 10f, y + 10f, subPaint)

            y += barHeight + 4f
        }

        y += 25f

        // DIBUJO DE GRÁFICO 2: DONA DE ESTADO
        canvas.drawText("Distribución por Estado e Identificación", margin, y, secTitlePaint.apply { textSize = 12f })
        y += 20f

        val donutX = margin + 80f
        val donutY = y + 50f
        val donutRadius = 45f

        val strokePaint = Paint().apply {
            style = Paint.Style.STROKE
            strokeWidth = 16f
            isAntiAlias = true
        }

        var startAngle = -90f
        val totalAnims = stats.totalAnimales.toFloat()

        if (totalAnims > 0f) {
            stats.distribucionEstado.forEach { item ->
                val sweepAngle = (item.cantidad.toFloat() / totalAnims) * 360f
                if (sweepAngle > 0f) {
                    strokePaint.color = item.color.toArgb()
                    canvas.drawArc(
                        donutX - donutRadius, donutY - donutRadius,
                        donutX + donutRadius, donutY + donutRadius,
                        startAngle, sweepAngle, false, strokePaint
                    )
                    startAngle += sweepAngle
                }
            }
        }

        // Texto central de la dona
        canvas.drawText("${stats.totalAnimales}", donutX - 10f, donutY + 4f, secTitlePaint.apply { textSize = 14f })

        // Leyenda Estado (A la derecha de la dona)
        var legY = y + 15f
        stats.distribucionEstado.forEach { item ->
            canvas.drawCircle(donutX + 80f, legY + 4f, 5f, Paint().apply { color = item.color.toArgb() })
            canvas.drawText("${item.label}: ${item.cantidad} (${String.format(locale, "%.1f", item.porcentaje)}%)", donutX + 92f, legY + 8f, subPaint)
            legY += 18f
        }

        // Resumen Tipo Arete (SADER vs OTRO)
        val areteX = margin + 450f
        canvas.drawText("Resumen Tipo Arete:", areteX, y + 15f, secTitlePaint.apply { textSize = 11f })
        var areteY = y + 32f
        stats.distribucionTipoArete.forEach { item ->
            canvas.drawText("${item.label}: ${item.cantidad} cab. (${String.format(locale, "%.1f", item.porcentaje)}%)", areteX, areteY, subPaint)
            areteY += 16f
        }
    }

    private fun androidx.compose.ui.graphics.Color.toArgb(): Int {
        return android.graphics.Color.argb(
            (this.alpha * 255).toInt(),
            (this.red * 255).toInt(),
            (this.green * 255).toInt(),
            (this.blue * 255).toInt()
        )
    }

    private fun drawKpiBox(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, title: String, value: String, sub: String) {
        val bgPaint = Paint().apply { color = Color.parseColor("#F8FAFC") }
        val borderPaint = Paint().apply { color = Color.parseColor("#CBD5E1"); style = Paint.Style.STROKE; strokeWidth = 1f }

        canvas.drawRoundRect(x, y, x + w, y + h, 8f, 8f, bgPaint)
        canvas.drawRoundRect(x, y, x + w, y + h, 8f, 8f, borderPaint)

        val tPaint = Paint().apply { color = colorTextMuted; textSize = 7.5f; isAntiAlias = true }
        val vPaint = Paint().apply { color = colorTextDark; textSize = 11f; typeface = Typeface.DEFAULT_BOLD; isAntiAlias = true }

        canvas.drawText(title, x + 6f, y + 12f, tPaint)
        canvas.drawText(value, x + 6f, y + 27f, vPaint)
        canvas.drawText(sub, x + 6f, y + 38f, tPaint)
    }

    private fun drawFooter(
        canvas: Canvas,
        pageNum: Int,
        totalPaginas: Int,
        data: ReporteGanadoExportData,
        margin: Float,
        pageWidth: Int,
        pageHeight: Int
    ) {
        val y = pageHeight - 20f
        val paint = Paint().apply {
            color = colorTextMuted
            textSize = 8f
            isAntiAlias = true
        }
        canvas.drawLine(margin, y - 10f, pageWidth - margin, y - 10f, Paint().apply { color = colorBorder; strokeWidth = 0.5f })
        canvas.drawText("${data.nombreRancho} — Reporte de Ganado e Inventario", margin, y, paint)

        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText("Página $pageNum de $totalPaginas", pageWidth - margin, y, paint)
    }
}
