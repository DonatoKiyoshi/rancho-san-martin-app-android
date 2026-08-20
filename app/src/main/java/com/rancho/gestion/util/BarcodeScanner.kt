package com.rancho.gestion.util

/**
 * Punto de integración con hardware de captura de aretes (sección
 * "Integración de Hardware" del boceto de requerimientos).
 *
 * IMPORTANTE:
 * La gran mayoría de "pistolitas" lectoras de código de barras USB o
 * Bluetooth funcionan en modo HID (Human Interface Device), es decir, se
 * comportan como un teclado externo: al escanear, "escriben" la serie
 * directamente sobre el campo de texto que tenga el foco (por ejemplo el
 * campo "Arete" en RegistrarAnimalScreen o RegistrarMuerteScreen) seguido
 * de un ENTER. Por eso, en este prototipo NINGÚN campo de Arete requiere
 * código adicional para funcionar con este tipo de lector: basta con que
 * el TextField tenga el foco antes de disparar el gatillo del lector.
 *
 * Como alternativa (cuando no se cuenta con lector físico), se deja este
 * objeto como punto de extensión para integrar escaneo por cámara usando
 * CameraX + ML Kit Barcode Scanning (dependencias comentadas en
 * app/build.gradle.kts). La firma de la función queda lista para
 * conectarse a un botón "Escanear" junto al campo Arete.
 */
object BarcodeScanner {
    /**
     * TODO: implementar con CameraX + ML Kit cuando se agreguen las
     * dependencias opcionales. Debe abrir una vista de cámara, detectar el
     * código de barras/QR del arete y devolver el valor leído mediante
     * [onResult].
     */
    fun escanearConCamara(onResult: (String) -> Unit) {
        // Punto de extensión — sin implementación en este prototipo.
        onResult("")
    }
}
