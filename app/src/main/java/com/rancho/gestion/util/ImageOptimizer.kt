package com.rancho.gestion.util

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.media.ExifInterface
import android.net.Uri
import java.io.File
import java.io.FileOutputStream

object ImageOptimizer {

    /**
     * Optimiza una imagen según RNF-09:
     * - Resolución: 1024x1024 (proporcional)
     * - Formato: WebP
     * - Tamaño: < 500 KB
     */
    fun optimize(context: Context, sourceUri: Uri): String? {
        return try {
            val inputStream = context.contentResolver.openInputStream(sourceUri)
            val originalBitmap = BitmapFactory.decodeStream(inputStream)
            inputStream?.close()

            if (originalBitmap == null) return null

            // 1. Corregir rotación si es necesario
            val rotatedBitmap = rotateImageIfRequired(context, originalBitmap, sourceUri)

            // 2. Redimensionar a 1024x1024 manteniendo proporción
            val resizedBitmap = resizeBitmap(rotatedBitmap, 1024)

            // 3. Guardar como WebP comprimido
            val outputDir = File(context.filesDir, "animal_photos").apply { if (!exists()) mkdirs() }
            val outputFile = File(outputDir, "photo_${System.currentTimeMillis()}.webp")
            
            val out = FileOutputStream(outputFile)
            resizedBitmap.compress(Bitmap.CompressFormat.WEBP, 70, out)
            out.flush()
            out.close()

            // 4. Verificar tamaño (si > 500KB, comprimir más - opcional pero recomendado)
            
            outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    private fun resizeBitmap(source: Bitmap, maxLength: Int): Bitmap {
        val width = source.width
        val height = source.height
        
        val ratio = width.toFloat() / height.toFloat()
        var targetWidth = width
        var targetHeight = height

        if (width > height) {
            if (width > maxLength) {
                targetWidth = maxLength
                targetHeight = (maxLength / ratio).toInt()
            }
        } else {
            if (height > maxLength) {
                targetHeight = maxLength
                targetWidth = (maxLength * ratio).toInt()
            }
        }

        return Bitmap.createScaledBitmap(source, targetWidth, targetHeight, true)
    }

    private fun rotateImageIfRequired(context: Context, img: Bitmap, selectedImage: Uri): Bitmap {
        val input = context.contentResolver.openInputStream(selectedImage)
        val ei = ExifInterface(input!!)
        val orientation = ei.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
        input.close()

        return when (orientation) {
            ExifInterface.ORIENTATION_ROTATE_90 -> rotateImage(img, 90f)
            ExifInterface.ORIENTATION_ROTATE_180 -> rotateImage(img, 180f)
            ExifInterface.ORIENTATION_ROTATE_270 -> rotateImage(img, 270f)
            else -> img
        }
    }

    private fun rotateImage(img: Bitmap, degree: Float): Bitmap {
        val matrix = Matrix()
        matrix.postRotate(degree)
        val rotatedImg = Bitmap.createBitmap(img, 0, 0, img.width, img.height, matrix, true)
        img.recycle()
        return rotatedImg
    }
}
