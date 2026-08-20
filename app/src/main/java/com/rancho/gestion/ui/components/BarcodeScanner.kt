package com.rancho.gestion.ui.components

import android.annotation.SuppressLint
import android.util.Log
import android.view.ViewGroup
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@SuppressLint("UnsafeOptInUsageError")
@Composable
fun BarcodeScannerScreen(
    onResult: (String) -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraProviderFuture = remember { ProcessCameraProvider.getInstance(context) }
    val scope = rememberCoroutineScope()
    
    var hasFlash by remember { mutableStateOf(false) }
    var isFlashOn by remember { mutableStateOf(false) }
    var cameraControl by remember { mutableStateOf<CameraControl?>(null) }
    var isProcessed by remember { mutableStateOf(false) }

    // LIMPIEZA AGRESIVA: Al destruir el componente, forzar el apagado y desvincular todo
    DisposableEffect(lifecycleOwner) {
        onDispose {
            try {
                val cameraProvider = cameraProviderFuture.get()
                // Apagar flash explícitamente si está encendido
                cameraControl?.enableTorch(false)
                // Desvincular todos los casos de uso para liberar el hardware
                cameraProvider.unbindAll()
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Error en onDispose cleanup", e)
            }
        }
    }
    
    val snackbarHostState = remember { SnackbarHostState() }

    val scanner = remember {
        BarcodeScanning.getClient(
            BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                .build()
        )
    }

    val galleryLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null && !isProcessed) {
            try {
                val image = InputImage.fromFilePath(context, uri)
                scanner.process(image)
                    .addOnSuccessListener { barcodes ->
                        val barcode = barcodes.firstOrNull()
                        if (barcode != null) {
                            barcode.rawValue?.let { value ->
                                isProcessed = true
                                cameraControl?.enableTorch(false)
                                onResult(value)
                            }
                        } else {
                            scope.launch {
                                snackbarHostState.showSnackbar("No se encontró ningún código.")
                            }
                        }
                    }
            } catch (e: Exception) {
                Log.e("BarcodeScanner", "Gallery error", e)
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                    scaleType = PreviewView.ScaleType.FILL_CENTER
                }

                val executor = Executors.newSingleThreadExecutor()
                cameraProviderFuture.addListener({
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        
                        val preview = Preview.Builder().build().apply {
                            setSurfaceProvider(previewView.surfaceProvider)
                        }

                        val scannerForAnalysis = BarcodeScanning.getClient(
                            BarcodeScannerOptions.Builder()
                                .setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS)
                                .build()
                        )

                        val imageAnalysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()

                        imageAnalysis.setAnalyzer(executor) { imageProxy ->
                            if (isProcessed) {
                                imageProxy.close()
                                return@setAnalyzer
                            }
                            
                            val mediaImage = imageProxy.image
                            if (mediaImage != null) {
                                val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                                scannerForAnalysis.process(image)
                                    .addOnSuccessListener { barcodes ->
                                        val barcode = barcodes.firstOrNull()
                                        if (barcode != null && !isProcessed) {
                                            barcode.rawValue?.let { value ->
                                                isProcessed = true
                                                // Apagar flash antes de devolver resultado
                                                cameraControl?.enableTorch(false)
                                                onResult(value)
                                            }
                                        }
                                    }
                                    .addOnCompleteListener {
                                        imageProxy.close()
                                    }
                            } else {
                                imageProxy.close()
                            }
                        }

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                        hasFlash = camera.cameraInfo.hasFlashUnit()
                    } catch (e: Exception) {
                        Log.e("BarcodeScanner", "Camera bind error", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        ScannerOverlay()

        Column(modifier = Modifier.fillMaxSize()) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = {
                    cameraControl?.enableTorch(false)
                    onBack()
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Atrás", tint = Color.White)
                }
                Text("Escanear arete", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                
                if (hasFlash) {
                    IconButton(onClick = {
                        isFlashOn = !isFlashOn
                        cameraControl?.enableTorch(isFlashOn)
                    }) {
                        Icon(
                            imageVector = if (isFlashOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                            contentDescription = "Flash",
                            tint = Color.White
                        )
                    }
                } else {
                    Spacer(Modifier.size(48.dp))
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Surface(color = Color.Black.copy(alpha = 0.5f), shape = RoundedCornerShape(12.dp)) {
                    Text(
                        "Coloca el código de barras del arete\ndentro del recuadro",
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        fontSize = 14.sp
                    )
                }
            }

            Spacer(Modifier.weight(1f))

            Row(Modifier.fillMaxWidth().padding(24.dp)) {
                IconButton(
                    onClick = { galleryLauncher.launch("image/*") },
                    modifier = Modifier.background(Color.DarkGray.copy(alpha = 0.5f), CircleShape)
                ) {
                    Icon(Icons.Default.PhotoLibrary, contentDescription = "Galería", tint = Color.White)
                }
            }
        }
        
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 100.dp)
        )
    }
}

@Composable
fun ScannerOverlay() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height
        val rectSize = 250.dp.toPx()
        val left = (width - rectSize) / 2
        val top = (height - rectSize) / 2
        val rect = Rect(left, top, left + rectSize, top + rectSize)

        drawPath(
            path = Path().apply {
                addRect(Rect(0f, 0f, width, height))
                addRoundRect(RoundRect(rect, CornerRadius(20.dp.toPx())))
                fillType = PathFillType.EvenOdd
            },
            color = Color.Black.copy(alpha = 0.5f)
        )

        val cornerLength = 40.dp.toPx()
        val strokeWidth = 4.dp.toPx()
        val radius = 20.dp.toPx()

        val cornerPath = Path().apply {
            moveTo(left, top + cornerLength)
            lineTo(left, top + radius)
            arcTo(Rect(left, top, left + radius * 2, top + radius * 2), 180f, 90f, false)
            lineTo(left + cornerLength, top)

            moveTo(left + rectSize - cornerLength, top)
            lineTo(left + rectSize - radius, top)
            arcTo(Rect(left + rectSize - radius * 2, top, left + rectSize, top + radius * 2), 270f, 90f, false)
            lineTo(left + rectSize, top + cornerLength)

            moveTo(left + rectSize, top + rectSize - cornerLength)
            lineTo(left + rectSize, top + rectSize - radius)
            arcTo(Rect(left + rectSize - radius * 2, top + rectSize - radius * 2, left + rectSize, top + rectSize), 0f, 90f, false)
            lineTo(left + rectSize - cornerLength, top + rectSize)

            moveTo(left + cornerLength, top + rectSize)
            lineTo(left + radius, top + rectSize)
            arcTo(Rect(left, top + rectSize - radius * 2, left + radius * 2, top + rectSize), 90f, 90f, false)
            lineTo(left, top + rectSize - cornerLength)
        }

        drawPath(path = cornerPath, color = Color.White, style = Stroke(width = strokeWidth, cap = StrokeCap.Round))
        drawLine(color = Color.Green, start = Offset(left + 10.dp.toPx(), top + rectSize / 2), end = Offset(left + rectSize - 10.dp.toPx(), top + rectSize / 2), strokeWidth = 2.dp.toPx())
    }
}
