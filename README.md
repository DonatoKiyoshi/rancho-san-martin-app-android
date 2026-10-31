# Rancho San M — Sistema Integral de Gestión Ganadera y Control Financiero

**Rancho Gestión** es una aplicación móvil nativa para Android (Kotlin + Jetpack Compose + Room) diseñada para el control operativo, inventario bovino, gestión de ventas/mortalidad, trazabilidad por arete y control financiero en ranchos ganaderos.

La aplicación está construida bajo una filosofía **Offline-First**, permitiendo operar al 100% en campo sin conexión a internet, almacenando la información en una base de datos SQLite local cifrada/optimizada con Room y sincronizando en segundo plano mediante `WorkManager`.

---


---

## ✨ Características Principales

-  **UI Moderna y Reactiva:** 100% Jetpack Compose con Material Design 3.
-  **100% Offline-First:** Persistencia completa en base de datos local con Room (SQLite).
-  **Captura y Compresión de Fotos:** Integración nativa con cámara, conversión a formato **WebP 1024x1024** y tamaño comprimido inferior a 500 KB.
-  **Escaneo Dual de Aretes:** Escáner por cámara en tiempo real mediante **CameraX + Google ML Kit** y compatibilidad universal *plug-and-play* con lectores de código de barras físicos (USB/Bluetooth HID).
-  **Clasificación y Etapas Automatizadas:** Categorización dinámica por peso y semaforización de tiempos en rancho (Acopio 8–15 días; Engorda 60–75 días).
-  **Ventas por Jaula con Control de Carga:** Reparto proporcional de montos de venta según el peso de cada cabeza y tope inteligente por capacidad (110 cabezas o 24.5 toneladas).
-  **Control de Mortalidad y Cálculo de Merma:** Cálculo inmediato de la pérdida financiera en muertes (Precio de compra − Salvamento).
-  **Workflow de Aprobaciones:** Separación estricta de permisos entre **Administrador** y **Empleado** con bandeja de aprobaciones pendientes.
-  **Seguridad Avanzada:** Hash de contraseñas con **PBKDF2 + Salt aleatorio**, autenticación biométrica (**BiometricPrompt**) y almacenamiento cifrado (**EncryptedSharedPreferences** + AES-256 GCM).
- 📊 **Exportación Profesional:** Generación de reportes ejecutivos en **PDF** y hojas multilengüeta de **Excel (.xlsx)** con gráficos nativos utilizando Apache POI.

---

## 🏗️ Arquitectura del Sistema

El proyecto implementa una arquitectura limpia (*Clean Architecture*) orientada a Compose y desacoplada en 4 capas principales:

```
com.rancho.gestion
├── data/              # Capa de Datos (Room Entities, DAOs, Repositorios)
├── domain/            # Capa de Dominio (Lógica pura de negocio, sin Android)
├── ui/                # Capa de Presentación (Compose, ViewModels, Components, Navigation)
├── sync/              # Capa de Sincronización (WorkManager para tareas en background)
└── util/              # Capa de Utilidades (Cámara, Escáner, PDF, Excel POI, Biometría)
```

- **Inyección de Dependencias:** Gestionada de forma liviana y explícita mediante `AppContainer.kt` (sin la sobrecarga de Hilt/Dagger), lo que facilita la lectura y depuración del código.
- **Flujo de Datos:** Estado unidireccional utilizando `StateFlow` y `Flow` de Kotlin Coroutines desde Room hacia las pantallas en Jetpack Compose.

---

##  Módulos y Funcionalidades

### 1. Gestión de Ganado
- **Registro Único por Arete:** La llave primaria (`Animal.arete`) identifica de forma unívoca a cada animal (SADER o Arete Interno).
- **Clasificación por Peso:** La clase `ClasificadorPeso` categoriza automáticamente el animal comparando su peso contra los rangos configurados en el sistema (ej. Cátedras, Destete, Engorda, etc.).
- **Calculadora de Etapas (`EtapaCalculator`):** Monitorea los días transcurridos desde el ingreso del animal y emite alertas visuales y de notificación cuando el animal está por superar o ha superado el tiempo recomendado en Acopio o Engorda.

### 2. Captura y Optimización de Fotografías
- **Captura Nativa:** `RegistrarAnimalScreen` activa la cámara del dispositivo mediante `ActivityResultContracts.TakePicture()` utilizando un contenedor temporal de archivos `FileProvider`.
- **Procesamiento e `ImageOptimizer`:**
  1. Detecta y corrige automáticamente la orientación EXIF de la toma.
  2. Redimensiona proporcionalmente la imagen a un máximo de 1024x1024 píxeles.
  3. Comprime el archivo resultante en formato **WebP** al 70% de calidad.
  4. Garantiza un tamaño final optimizado (< 500 KB) almacenado en el directorio interno `animal_photos/` para consumo fluido con **Coil**.

### 3. Lector de Código de Barras (Aretes)
El sistema soporta dos modalidades de escaneo para agilizar la entrada y salida de datos:

#### A. Escáner por Cámara (ML Kit + CameraX)
- Implementado en `ui/components/BarcodeScanner.kt`.
- Utiliza **CameraX** para la vista previa y **Google ML Kit BarcodeScanning** en tiempo real.
- Incluye:
  - Superposición visual (*ScannerOverlay*) con guía de encuadre.
  - Control de linterna/flash para ambientes oscuros.
  - Carga alternativa desde la galería de imágenes del dispositivo.
  - Gestión rigurosa del ciclo de vida (`DisposableEffect`) apagando el flash y desvinculando la cámara al salir.

#### B. Lectores Físicos (Pistolas USB / Bluetooth)
- Los lectores industriales operan habitualmente en modo **HID (Human Interface Device)** actuando como un teclado externo.
- Al enfocar cualquier campo de texto (Arete en Registro, Muerte o Búsqueda) y disparar el lector, este escribe automáticamente la serie del arete y envía una tecla `ENTER`, procesando la búsqueda o inserción sin requerir interacción táctil.

### 4. Ventas y Salidas (Jaulas, Individual y Muerte)
- **Venta Individual:** Transacción directa de un animal registrando comprador, precio final y peso de salida.
- **Venta por Jaulas (Lotes):**
  - **Capacidad Restringida (`JaulaCapacidad`):** Aplica un límite de carga automático de máximo 110 cabezas o 24.5 toneladas (24 t + 0.5 t de tolerancia).
  - **Reparto Proporcional:** Al vender la jaula por un monto global, `VentaRepository.venderJaula` calcula y distribuye el monto exacto asignado a cada animal proporcionalmente a su peso individual.
- **Registrar Muerte y Cálculo de Merma:**
  - Registra fecha, causa y valor recuperado por salvamento.
  - `MermaCalculator` determina de inmediato el impacto financiero:
    $$\text{Merma (Pérdida)} = \text{Precio de Compra} - \text{Valor Recuperado}$$

### 5. Workflow de Aprobaciones y Roles
- **Roles:** `ADMINISTRADOR` y `EMPLEADO`.
- **Modo Empleado:**
  - Los registros de ganado, ventas o registros de mortalidad ingresados por empleados quedan almacenados con estado `PENDIENTE`.
  - El usuario Empleado no tiene acceso a las pantallas de Configuración, Reportes ni a botones de eliminación directa.
- **Modo Administrador (Bandeja de Aprobaciones):**
  - La pantalla `AprobacionesScreen.kt` enumera las solicitudes pendientes divididas por pestañas (Animales, Muertes, Ventas).
  - El Administrador puede inspeccionar el detalle (`DetalleAprobacionAnimalScreen.kt`) y autorizar o rechazar la transacción con un solo toque.

### 6. Control Financiero
- Registro de egresos operacionales clasificados en 5 categorías obligatorias:
  1. **Nómina:** Incluye desglose especial de días trabajados y nombre del empleado.
  2. **Gasolina**
  3. **Fármacos**
  4. **Alimento**
  5. **Otros**
- Dashboard financiero dinámico con cálculo automático del balance (Ingresos por ventas − Egresos operativos).

### 7. Reportes y Exportación (PDF y Excel POI)
- **Visualización en App:** Gráficos de barras nativos renderizados con Jetpack Compose `Canvas` (`SimpleBarChart.kt`).
- **Filtros por Fecha:** Cumple con la fecha de corte estándar (día 1 del mes) vía `DateUtils.kt`.
- **Exportación a PDF (`PdfGanadoGenerator` & `PdfGenerator`):**
  - Diseña documentos nativos en formato A4 Horizontal (`PdfDocument`).
  - Incluye encabezados institucionales, resumen de métricas clave (KPIs), tablas de detalle formateadas con alternancia de filas y paleta de colores de marca.
- **Exportación a Excel (`ExcelGanadoGenerator` & `ExcelGenerator`):**
  - Construidos con **Apache POI (`XSSFWorkbook`)**.
  - Genera archivos `.xlsx` estructurados en múltiples pestañas:
    1. **Resumen:** KPIs ejecutivos + gráficos nativos de Excel incrustados (`XSSFChart`).
    2. **Registros:** Tabla detallada de animales/ventas con formatos numéricos y monetarios.
    3. **Análisis:** Tablas dinámicas y datos de origen para gráficos.
    4. **Catálogos y Criterios:** Leyendas de configuración y rangos de peso.

### 8. Seguridad y Biometría
- **Hashing Criptográfico (`PasswordHasher`):**
  - Implementa **PBKDF2WithHmacSHA256** con 120,000 iteraciones y *salt* aleatorio de 16 bytes por usuario.
- **Autenticación Biométrica (`BiometricAuthManager`):**
  - Permite inicio de sesión rápido mediante huella dactilar o reconocimiento facial (`BiometricPrompt`).
  - Almacena de forma segura el token y las credenciales del usuario usando **EncryptedSharedPreferences** respaldado por la Android KeyStore con algoritmos **AES-256 GCM** y **AES-256 SIV**.

### 9. Tareas en Segundo Plano (WorkManager)
Gestionadas centralmente en `sync/SyncManager.kt`:
- **`SyncWorker`:** Programado cada 30 minutos con restricción de red conectada. Punto de extensión para la sincronización remota con un backend Cloud (Firebase/REST API).
- **`MaintenanceWorker`:** Se ejecuta cada 24 horas para purgar de la base de datos local los registros del archivo histórico con más de 3 años de antigüedad.
- **`AlertasWorker`:** Analiza diariamente el inventario activo y envía notificaciones push/locales (`NotificationHelper`) si existen animales próximos a vencer o vencidos en sus etapas de Acopio o Engorda.

---

## 📁 Estructura del Proyecto

```
RanchoApp/
├── app/
│   ├── build.gradle.kts           # Dependencias de Compose, Room, CameraX, ML Kit, POI, WorkManager
│   └── src/
│       ├── main/
│       │   ├── java/com/rancho/gestion/
│       │   │   ├── data/          # Entidades Room, DAOs, Repositorios
│       │   │   ├── domain/        # Reglas de negocio puras (Clasificador, Etapas, Merma, Jaulas)
│       │   │   ├── sync/          # WorkManager Workers (Sync, Mantenimiento, Alertas)
│       │   │   ├── ui/            # Pantallas Compose, componentes, navegación y temas
│       │   │   │   ├── components/    # BarcodeScannerScreen, AppDrawer, Diálogos, Tarjetas
│       │   │   │   ├── configuracion/ # Pantallas de usuarios y rangos de peso
│       │   │   │   ├── finanzas/      # Pantalla de control de egresos
│       │   │   │   ├── ganado/        # Registro, lista y detalle de animales
│       │   │   │   ├── login/         # Inicio de sesión y biometría
│       │   │   │   ├── navigation/    # RanchoNavHost y definición de rutas
│       │   │   │   ├── panel/         # Dashboard principal con métricas y alertas
│       │   │   │   ├── reportes/      # Menú de reportes y visualizadores de gráficos
│       │   │   │   └── vender/        # Venta individual, jaulas y registro de muertes
│       │   │   ├── util/          # BarcodeScanner, BiometricAuth, ImageOptimizer, PDF/Excel Generators
│       │   │   ├── AppContainer.kt# Contenedor para inyección de dependencias manual
│       │   │   ├── MainActivity.kt# Entrada principal de la aplicación
│       │   │   └── RanchoApp.kt   # Clase Application e inicialización de WorkManager
│       │   └── res/               # Drawables, mipmaps (ícono arete), strings, xml (FileProvider)
│       └── test/                  # Pruebas unitarias de la lógica de negocio en domain/
├── build.gradle.kts
└── settings.gradle.kts
```

---

## 🛠️ Integraciones Técnicas Clave

| Componente / Funcionalidad | Tecnología Utilizada | Archivo Principal de Referencia |
|----------------------------|----------------------|-----------------------------------|
| **Base de Datos Local** | Room (SQLite) + TypeConverters | `data/AppDatabase.kt` |
| **Escáner de Aretes (Cámara)** | CameraX 1.4.0 + ML Kit Barcode Scanning 17.3.0 | `ui/components/BarcodeScanner.kt` |
| **Escáner de Aretes (Físico)** | Teclado HID (USB / Bluetooth) | `util/BarcodeScanner.kt` |
| **Toma y Optimización de Fotos** | ActivityResultContracts.TakePicture + WebP | `util/ImageOptimizer.kt` |
| **Exportación de Reportes Excel** | Apache POI 5.2.3 (XSSF) + Charts Incrustados | `util/ExcelGanadoGenerator.kt` |
| **Exportación de Reportes PDF** | Android PdfDocument (A4 Landscape) | `util/PdfGanadoGenerator.kt` |
| **Autenticación Biométrica** | BiometricPrompt + EncryptedSharedPreferences | `util/BiometricAuthManager.kt` |
| **Cifrado de Contraseñas** | PBKDF2WithHmacSHA256 (120k iters + Salt) | `util/PasswordHasher.kt` |
| **Tareas en Background** | AndroidX WorkManager | `sync/SyncManager.kt` |

---

## 🚀 Requisitos e Instalación

### Requisitos del Sistema
- **Android Studio:** Ladybug (2024.2.1) o superior.
- **JDK:** Java 17 o superior.
- **Dispositivo / Emulador:** Android 8.0 (API Level 26) o superior (minSdk 26, targetSdk 36).

### Pasos para Compilar y Ejecutar

1. **Clonar o Abrir el Proyecto:**
   Abre Android Studio y selecciona **Open**, luego navega a la carpeta raíz `RanchoApp/`.

2. **Gradle Sync & Wrapper:**
   Si el proyecto no incluye la carpeta `gradle/wrapper/`, ejecuta en una terminal dentro del directorio raíz:
   ```bash
   gradle wrapper --gradle-version 8.7
   ```
   Luego realiza un **Sync Project with Gradle Files** en Android Studio.

3. **Ejecutar la App:**
   Selecciona un emulador o dispositivo físico conectado y presiona el botón **Run (▶)**.

---

## 👤 Usuarios de Prueba

La base de datos se precarga automáticamente en el primer inicio con dos usuarios por defecto:

| Usuario | Contraseña | Rol | Accesos y Permisos |
|---------|------------|-----|--------------------|
| `admin` | `admin123` | **Administrador** | Acceso total: Gestión de usuarios, rangos de peso, bandeja de aprobaciones, reportes, exportaciones y eliminación. |
| `empleado` | `empleado123` | **Empleado** | Registro de ganado, registros de venta y mortalidad, registro de egresos. Los registros quedan pendientes de aprobación por el admin. |

---

## 🧪 Pruebas Unitarias

La lógica del módulo de dominio (`domain/`) está desacoplada de las dependencias de Android y cuenta con cobertura mediante pruebas unitarias en JUnit 4.

Para ejecutar la suite de pruebas unitarias desde la terminal:
```bash
./gradlew test
```
O bien, en Android Studio: Clic derecho sobre `app/src/test/java/com/rancho/gestion/domain/BusinessLogicTest.kt` → **Run 'BusinessLogicTest'**.

---

## 📌 Estado de la Sincronización Cloud

La aplicación es 100% operativa en modo local. La infraestructura para la sincronización con la nube está completamente estructurada en `sync/SyncWorker.kt`. Al seleccionar un proveedor Cloud (Firebase, AWS, o una API REST propia), únicamente se requiere implementar la llamada HTTP/SDK en el método `doWork()` de `SyncWorker`.
