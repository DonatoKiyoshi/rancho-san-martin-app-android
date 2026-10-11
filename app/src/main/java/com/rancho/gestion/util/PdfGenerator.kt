package com.rancho.gestion.util

import android.content.Context
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.text.StaticLayout
import android.text.TextPaint
import com.rancho.gestion.data.entities.CategoriaEgreso
import com.rancho.gestion.data.entities.Egreso
import com.rancho.gestion.ui.finanzas.categoriaLabel
import java.io.File
import java.io.FileOutputStream
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max

object PdfGenerator {

    private val colorPrimary = Color.parseColor("#0A2540")
    private val colorAccent = Color.parseColor("#0891B2")
    private val colorTextDark = Color.parseColor("#1E293B")
    private val colorTextMuted = Color.parseColor("#64748B")
    private val colorBgLight = Color.parseColor("#F8FAFC")
    private val colorHeaderBg = Color.parseColor("#F1F5F9")
    private val colorBorder = Color.parseColor("#CBD5E1")

    private fun getCatColor(cat: CategoriaEgreso) = when (cat) {
        CategoriaEgreso.NOMINA -> Color.parseColor("#10B981")
        CategoriaEgreso.GASOLINA -> Color.parseColor("#EA580C")
        CategoriaEgreso.ALIMENTO -> Color.parseColor("#D97706")
        CategoriaEgreso.FARMACOS -> Color.parseColor("#8B5CF6")
        CategoriaEgreso.OTROS -> Color.parseColor("#64748B")
    }

    fun generatePdf(
        context: Context,
        file: File,
        egresos: List<Egreso>,
        desde: Long,
        hasta: Long,
        filtroCategorias: String,
        incluirDescripcion: Boolean
    ): Boolean {
        val data = ReporteFinancieroData.preparar(egresos, desde, hasta, filtroCategorias, incluirDescripcion)
        val document = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create() // A4

        val currency = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("es-MX")).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
        val dateFormat = SimpleDateFormat("dd/MM/yyyy", Locale.forLanguageTag("es-MX"))
        val timestampFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("es-MX"))
        val nowFormatted = timestampFormat.format(Date())

        var pageNum = 1
        var page = document.startPage(pageInfo)
        var canvas = page.canvas

        // --- PÁGINA 1: Encabezado, Total General y comienzo del detalle tabular ---
        var yPos = drawHeaderAndKpi(canvas, data, currency, dateFormat)

        // Tabla de movimientos comienza inmediatamente en la página 1
        yPos = drawTableHeaderAt(canvas, yPos + 15f, data.incluirDescripcion)

        val textPaint = TextPaint().apply {
            color = colorTextDark
            textSize = 10f
            isAntiAlias = true
        }
        val amountPaint = TextPaint().apply {
            color = colorPrimary
            textSize = 10f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val margin = 40f
        val colDateW = 75f
        val colCatW = 95f
        val colMontoW = 85f
        val colDescW = if (data.incluirDescripcion) 260f else 0f

        val bgEvenPaint = Paint().apply { color = Color.WHITE }
        val bgOddPaint = Paint().apply { color = colorBgLight }
        val linePaint = Paint().apply { color = colorBorder; strokeWidth = 0.5f }

        for ((index, egreso) in data.egresos.withIndex()) {
            val desc = if (data.incluirDescripcion) egreso.descripcion.trim() else ""
            val staticLayout = if (data.incluirDescripcion && desc.isNotBlank()) {
                StaticLayout.Builder.obtain(desc, 0, desc.length, textPaint, colDescW.toInt()).build()
            } else null

            val layoutHeight = staticLayout?.height?.toFloat() ?: 0f
            val rowHeight = max(24f, layoutHeight + 10f)

            // Salto de página si excede el límite vertical de la hoja
            if (yPos + rowHeight > 760f) {
                drawPageFooter(canvas, pageNum, nowFormatted)
                document.finishPage(page)
                pageNum++
                page = document.startPage(pageInfo)
                canvas = page.canvas
                yPos = drawTableHeaderAt(canvas, 45f, data.incluirDescripcion)
            }

            val rowBg = if (index % 2 == 0) bgEvenPaint else bgOddPaint
            canvas.drawRect(margin, yPos, 595f - margin, yPos + rowHeight, rowBg)

            var xPos = margin + 5f
            val textY = yPos + 15f

            canvas.drawText(dateFormat.format(Date(egreso.fechaEgreso)), xPos, textY, textPaint)
            xPos += colDateW

            canvas.drawText(categoriaLabel(egreso.categoria), xPos, textY, textPaint)
            xPos += colCatW

            canvas.drawText(currency.format(egreso.montoTotal), xPos + colMontoW - 10f, textY, amountPaint)
            xPos += colMontoW

            if (data.incluirDescripcion && staticLayout != null) {
                canvas.save()
                canvas.translate(xPos, yPos + 5f)
                staticLayout.draw(canvas)
                canvas.restore()
            }

            canvas.drawLine(margin, yPos + rowHeight, 595f - margin, yPos + rowHeight, linePaint)
            yPos += rowHeight
        }

        if (data.egresos.isEmpty()) {
            val emptyPaint = Paint().apply { color = colorTextMuted; textSize = 11f; textAlign = Paint.Align.CENTER }
            canvas.drawText("No se encontraron movimientos registrados en este periodo.", 595f / 2f, yPos + 30f, emptyPaint)
            yPos += 50f
        }

        // Fila de Total General al final de la tabla
        if (yPos + 40f > 760f) {
            drawPageFooter(canvas, pageNum, nowFormatted)
            document.finishPage(page)
            pageNum++
            page = document.startPage(pageInfo)
            canvas = page.canvas
            yPos = drawTableHeaderAt(canvas, 45f, data.incluirDescripcion)
        }

        yPos += 15f
        val totalBgPaint = Paint().apply { color = colorHeaderBg }
        canvas.drawRect(margin, yPos - 12f, 595f - margin, yPos + 22f, totalBgPaint)

        val totalLabelPaint = Paint().apply {
            color = colorPrimary
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }
        val totalValuePaint = Paint().apply {
            color = colorPrimary
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        val totalLabelX = margin + colDateW + colCatW
        canvas.drawText("Total General:", totalLabelX, yPos + 6f, totalLabelPaint)
        canvas.drawText(currency.format(data.totalGeneral), totalLabelX + colMontoW - 5f, yPos + 6f, totalValuePaint)

        drawPageFooter(canvas, pageNum, nowFormatted)
        document.finishPage(page)

        // --- ÚLTIMA HOJA DEDICADA A LOS DOS GRÁFICOS Y ANÁLISIS ---
        pageNum++
        page = document.startPage(pageInfo)
        canvas = page.canvas

        drawChartsPage(canvas, data, currency, dateFormat, nowFormatted, pageNum)
        document.finishPage(page)

        return try {
            FileOutputStream(file).use { out ->
                document.writeTo(out)
            }
            true
        } catch (e: Exception) {
            e.printStackTrace()
            android.util.Log.e("PdfGenerator", "Error al crear PDF", e)
            false
        } finally {
            document.close()
        }
    }

    private fun drawHeaderAndKpi(
        canvas: Canvas,
        data: ReporteFinancieroData,
        currency: NumberFormat,
        dateFormat: SimpleDateFormat
    ): Float {
        val margin = 40f
        var yPos = 45f

        val titlePaint = Paint().apply {
            color = colorPrimary
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subTitlePaint = Paint().apply {
            color = colorAccent
            textSize = 13f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val metaPaint = Paint().apply {
            color = colorTextMuted
            textSize = 10f
            isAntiAlias = true
        }

        // Bloque del Encabezado
        canvas.drawText("Rancho San Martín", margin, yPos, titlePaint)
        yPos += 20f
        canvas.drawText("Informe Financiero", margin, yPos, subTitlePaint)
        yPos += 16f
        canvas.drawText("Periodo: ${dateFormat.format(Date(data.desde))} al ${dateFormat.format(Date(data.hasta))}", margin, yPos, metaPaint)
        yPos += 14f
        canvas.drawText("Categorías: ${data.filtroCategoriasTexto}", margin, yPos, metaPaint)

        yPos += 12f
        val linePaint = Paint().apply { color = colorBorder; strokeWidth = 1f }
        canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint)

        // Tarjeta KPI: Total General de Egresos
        yPos += 15f
        val cardRect = RectF(margin, yPos, 595f - margin, yPos + 68f)
        val cardBgPaint = Paint().apply { color = colorBgLight; style = Paint.Style.FILL }
        val cardBorderPaint = Paint().apply { color = colorBorder; style = Paint.Style.STROKE; strokeWidth = 1f }
        canvas.drawRoundRect(cardRect, 8f, 8f, cardBgPaint)
        canvas.drawRoundRect(cardRect, 8f, 8f, cardBorderPaint)

        val kpiLabelPaint = Paint().apply { color = colorTextMuted; textSize = 10f; isAntiAlias = true }
        val kpiValuePaint = Paint().apply {
            color = colorPrimary
            textSize = 24f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val kpiSubPaint = Paint().apply { color = colorTextMuted; textSize = 10f; isAntiAlias = true }

        canvas.drawText("TOTAL GENERAL DE EGRESOS", margin + 16f, yPos + 22f, kpiLabelPaint)
        canvas.drawText(currency.format(data.totalGeneral), margin + 16f, yPos + 50f, kpiValuePaint)

        val countText = "${data.totalMovimientos} movimientos incluidos"
        val countWidth = kpiSubPaint.measureText(countText)
        canvas.drawText(countText, 595f - margin - 16f - countWidth, yPos + 40f, kpiSubPaint)

        return yPos + 68f
    }

    private fun drawChartsPage(
        canvas: Canvas,
        data: ReporteFinancieroData,
        currency: NumberFormat,
        dateFormat: SimpleDateFormat,
        nowFormatted: String,
        pageNum: Int
    ) {
        val margin = 40f
        var yPos = 45f

        val titlePaint = Paint().apply {
            color = colorPrimary
            textSize = 20f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val subTitlePaint = Paint().apply {
            color = colorAccent
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawText("Rancho San Martín", margin, yPos, titlePaint)
        yPos += 18f
        canvas.drawText("Análisis Financiero y Gráficos por Categoría", margin, yPos, subTitlePaint)
        yPos += 14f

        val metaPaint = Paint().apply { color = colorTextMuted; textSize = 9.5f; isAntiAlias = true }
        canvas.drawText("Periodo: ${dateFormat.format(Date(data.desde))} al ${dateFormat.format(Date(data.hasta))} | Categorías: ${data.filtroCategoriasTexto}", margin, yPos, metaPaint)

        yPos += 12f
        val linePaint = Paint().apply { color = colorBorder; strokeWidth = 1f }
        canvas.drawLine(margin, yPos, 595f - margin, yPos, linePaint)

        yPos += 25f

        if (data.totalGeneral.toDouble() > 0 && data.resumenCategorias.isNotEmpty()) {
            val sectionHeaderPaint = Paint().apply {
                color = colorPrimary
                textSize = 14f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }

            // --- GRÁFICO 1: COMPARATIVA POR CATEGORÍA (BARRAS HORIZONTALES) ---
            canvas.drawText("Comparativa por Categoría", margin, yPos, sectionHeaderPaint)
            yPos += 22f

            val labelPaint = Paint().apply { color = colorTextDark; textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); isAntiAlias = true }
            val valuePaint = Paint().apply { color = colorTextMuted; textSize = 10f; textAlign = Paint.Align.RIGHT; isAntiAlias = true }
            val amountBarPaint = Paint().apply { color = colorPrimary; textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.RIGHT; isAntiAlias = true }
            val barBgPaint = Paint().apply { color = colorHeaderBg }

            val barMaxWidth = 250f
            val maxVal = data.resumenCategorias.maxOfOrNull { it.total.toDouble() } ?: 1.0

            for (catRes in data.resumenCategorias) {
                if (catRes.total.toDouble() <= 0) continue

                canvas.drawText(catRes.label, margin, yPos + 12f, labelPaint)

                val pctStr = String.format(Locale.US, "%.1f%%", catRes.porcentaje.toDouble())
                val amountStr = currency.format(catRes.total)

                canvas.drawText(pctStr, margin + 90f + barMaxWidth + 45f, yPos + 12f, valuePaint)
                canvas.drawText(amountStr, 595f - margin, yPos + 12f, amountBarPaint)

                // Barra
                val barW = max(4f, ((catRes.total.toDouble() / maxVal) * barMaxWidth).toFloat())
                val barRectBg = RectF(margin + 90f, yPos, margin + 90f + barMaxWidth, yPos + 14f)
                val barRectFill = RectF(margin + 90f, yPos, margin + 90f + barW, yPos + 14f)

                canvas.drawRoundRect(barRectBg, 6f, 6f, barBgPaint)
                canvas.drawRoundRect(barRectFill, 6f, 6f, Paint().apply { color = getCatColor(catRes.categoria); isAntiAlias = true })

                yPos += 28f
            }

            yPos += 35f

            // --- GRÁFICO 2: DISTRIBUCIÓN POR CATEGORÍA (DONA Y LEYENDA LIMPIA) ---
            canvas.drawText("Distribución por Categoría", margin, yPos, sectionHeaderPaint)
            yPos += 30f

            val donutRadius = 65f
            val donutCenterX = margin + 75f
            val donutCenterY = yPos + donutRadius
            val donutRect = RectF(
                donutCenterX - donutRadius,
                donutCenterY - donutRadius,
                donutCenterX + donutRadius,
                donutCenterY + donutRadius
            )

            var startAngle = -90f
            val totalDouble = data.totalGeneral.toDouble()

            for (catRes in data.resumenCategorias) {
                if (catRes.total.toDouble() <= 0) continue
                val sweep = ((catRes.total.toDouble() / totalDouble) * 360f).toFloat()
                val arcPaint = Paint().apply {
                    color = getCatColor(catRes.categoria)
                    style = Paint.Style.STROKE
                    strokeWidth = 26f
                    isAntiAlias = true
                }
                canvas.drawArc(donutRect, startAngle, sweep, false, arcPaint)
                startAngle += sweep
            }

            // Texto central dentro de la Dona
            val centerLabelPaint = Paint().apply { color = colorTextMuted; textSize = 9f; textAlign = Paint.Align.CENTER; isAntiAlias = true }
            val centerValPaint = Paint().apply { color = colorPrimary; textSize = 11f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.CENTER; isAntiAlias = true }
            canvas.drawText("Total", donutCenterX, donutCenterY - 4f, centerLabelPaint)
            canvas.drawText(currency.format(data.totalGeneral), donutCenterX, donutCenterY + 12f, centerValPaint)

            // Leyenda en la derecha - TEXTO ALINEADO A LA IZQUIERDA (Align.LEFT) PARA EVITAR TRASLAPE
            val legendX = margin + 190f
            var legendY = yPos + 10f

            val legLabelPaint = Paint().apply { color = colorTextDark; textSize = 10f; typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD); textAlign = Paint.Align.LEFT; isAntiAlias = true }
            val legValuePaint = Paint().apply { color = colorTextMuted; textSize = 9.5f; textAlign = Paint.Align.LEFT; isAntiAlias = true }

            for (catRes in data.resumenCategorias) {
                if (catRes.total.toDouble() <= 0) continue
                // Punto de color
                canvas.drawCircle(legendX, legendY - 3f, 5f, Paint().apply { color = getCatColor(catRes.categoria); isAntiAlias = true })
                canvas.drawText(catRes.label, legendX + 14f, legendY, legLabelPaint)

                val pctStr = String.format(Locale.US, "%.1f%%", catRes.porcentaje.toDouble())
                val legendDetail = "${currency.format(catRes.total)} ($pctStr)"
                canvas.drawText(legendDetail, legendX + 14f, legendY + 13f, legValuePaint)

                legendY += 32f
            }
        } else {
            val emptyPaint = Paint().apply { color = colorTextMuted; textSize = 12f; textAlign = Paint.Align.CENTER }
            canvas.drawText("No hay datos de egresos para mostrar gráficos en este periodo.", 595f / 2f, yPos + 60f, emptyPaint)
        }

        drawPageFooter(canvas, pageNum, nowFormatted)
    }

    private fun drawTableHeaderAt(canvas: Canvas, yPos: Float, incluirDescripcion: Boolean): Float {
        val margin = 40f
        val headerBgPaint = Paint().apply { color = colorHeaderBg }
        val headerTextPaint = Paint().apply {
            color = colorTextDark
            textSize = 11f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }

        canvas.drawRect(margin, yPos, 595f - margin, yPos + 26f, headerBgPaint)

        var xPos = margin + 5f
        val textY = yPos + 17f

        canvas.drawText("Fecha", xPos, textY, headerTextPaint)
        xPos += 75f

        canvas.drawText("Categoría", xPos, textY, headerTextPaint)
        xPos += 95f

        val rightAlignHeader = Paint(headerTextPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Monto", xPos + 85f - 10f, textY, rightAlignHeader)
        xPos += 85f

        if (incluirDescripcion) {
            canvas.drawText("Descripción", xPos, textY, headerTextPaint)
        }

        val linePaint = Paint().apply { color = colorBorder; strokeWidth = 1f }
        canvas.drawLine(margin, yPos + 26f, 595f - margin, yPos + 26f, linePaint)

        return yPos + 31f
    }

    private fun drawPageFooter(canvas: Canvas, pageNum: Int, timestamp: String) {
        val footerPaint = Paint().apply { color = colorTextMuted; textSize = 9f; isAntiAlias = true }
        canvas.drawText("Generado el: $timestamp", 40f, 810f, footerPaint)

        val pagePaint = Paint(footerPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Página $pageNum", 595f - 40f, 810f, pagePaint)
    }
}
