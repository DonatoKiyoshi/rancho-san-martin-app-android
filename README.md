# Rancho Gestión — App móvil (prototipo funcional)

Proyecto de Android Studio (Kotlin + Jetpack Compose + Room) que implementa
el prototipo funcional descrito en **BocetoDeRequerimientos.pdf**, usando
como base visual las pantallas del boceto (`000.pdf`–`017.pdf`, `Group_1.pdf`).

Es una app 100% nativa Android, funciona **sin conexión a internet** (toda
la información vive en una base de datos local SQLite vía Room) y reproduce
las pantallas, textos y flujos del boceto: Login, Panel, Ganado, Registrar
Animal, Vender (Venta Individual / Jaula / Registrar Muerte), Finanzas,
Reportes y Configuración.

---

## 0. Novedades de esta actualización

La app pasó de prototipo visual (paleta plana, sin ícono propio) a tener
identidad de marca y varias piezas del backlog de la sección 6 ya
funcionando de extremo a extremo. Resumen de lo que cambió:

**Identidad e interfaz**
- Ícono de la app (adaptativo, `mipmap-anydpi-v26`) e ícono de notificación:
  una silueta del **arete de ganado** — el elemento central de todo el
  sistema, ya que `Animal.arete` es la llave primaria de cada registro.
- Splash screen de marca (`androidx.core:core-splashscreen`) al abrir la app.
- Paleta ampliada (`ui/theme/Theme.kt`): contenedores tonales, acento
  terracota y colores de éxito/advertencia/error para las alertas de etapa.
- Menú lateral (`AppDrawer.kt`) con encabezado de marca, iniciales/rol del
  usuario en sesión, íconos por sección y estado seleccionado resaltado.
- Login (`LoginScreen.kt`) con logo, mostrar/ocultar contraseña y mensajes
  de error en tarjeta en vez de texto plano.
- Panel (`PanelScreen.kt`): tarjetas en cuadrícula de 2 columnas con ícono
  por indicador, saludo con el nombre del usuario y una **tarjeta de
  alertas** con los animales cuya etapa de Acopio/Engorda está por vencer
  o ya venció (usa `domain/EtapaCalculator`).
- Lista de Ganado: insignia de etapa por animal y estados vacíos ilustrados
  en vez de texto plano.
- Menús "Vender" y "Reportes" con tarjetas de ícono + descripción en lugar
  de simples renglones de texto.

**Del backlog (sección 6, "Próximos pasos sugeridos")**
- **Fotografía del animal:** `RegistrarAnimalScreen` ya puede tomar la foto
  con la cámara del dispositivo (`ActivityResultContracts.TakePicture` +
  `FileProvider`) y la guarda en `Animal.fotografiaUri`; la lista de Ganado
  ya la mostraba, solo faltaba la captura.
- **Notificaciones locales:** `sync/AlertasWorker` revisa una vez al día los
  animales activos y notifica (`util/NotificationHelper`) si hay alguno
  cerca de vencer o que ya venció su etapa — sin backend, 100% local.
- **`MaintenanceWorker` real:** ahora sí purga el archivo histórico
  (> 3 años) y queda programado desde `RanchoApp.onCreate` junto con la
  sincronización y las alertas.
- **Contraseñas con salt:** `util/PasswordHasher` pasó de SHA-256 simple a
  **PBKDF2 + salt aleatorio por usuario** (ver detalle en la sección 5).
- **Pruebas unitarias:** `app/src/test/.../domain/BusinessLogicTest.kt`
  cubre `ClasificadorPeso`, `EtapaCalculator`, `MermaCalculator` y
  `JaulaCapacidad`. Correr con `./gradlew test` (no requiere emulador).

**Lo que sigue pendiente** (ver sección 6 actualizada): conectar un backend
real para la sincronización en la nube — sigue siendo la única pieza que
depende de una decisión externa (qué backend usar) — y llevar el resto de
pantallas (Finanzas, Jaulas, Configuración) al mismo nivel de pulido visual
que Panel/Ganado/Login, que fueron las que más se transformaron aquí.

---

## 1. Primeros pasos (abrir el proyecto)

1. Instala **Android Studio** (Koala/Ladybug o más reciente).
2. Abre Android Studio → **Open** → selecciona la carpeta `RanchoApp/`.
3. **Sobre el Gradle Wrapper:** este paquete no incluye el binario
   `gradle-wrapper.jar` (el entorno donde se generó el proyecto no tiene
   salida a internet para descargarlo). Al abrir el proyecto, Android
   Studio normalmente:
   - detecta que falta el wrapper y ofrece un botón para **regenerarlo
     automáticamente** (recomendado, un clic), o
   - puedes ir a **File → Settings → Build, Execution, Deployment → Gradle**
     y elegir "Use Gradle from: Specified location" apuntando a un Gradle
     8.7 instalado en tu máquina, o
   - desde una terminal, con Gradle instalado (`brew install gradle`,
     `sdkman`, etc.), corre dentro de la carpeta del proyecto:
     ```
     gradle wrapper --gradle-version 8.7
     ```
     Esto crea `gradle/wrapper/gradle-wrapper.jar` y listo.
4. Deja que **Gradle Sync** termine (descargará las dependencias de
   `google()`/`mavenCentral()` la primera vez).
5. Ejecuta ▶ sobre un emulador o dispositivo físico (minSdk 26 / Android 8+).
6. (Opcional) Corre las pruebas unitarias de la lógica de negocio con
   `./gradlew test`, o clic derecho sobre
   `app/src/test/java/.../domain/BusinessLogicTest.kt` → "Run" en Android
   Studio. No requieren emulador.

### Usuarios de prueba (precargados al iniciar por primera vez)

| Usuario    | Contraseña     | Rol            |
|------------|----------------|----------------|
| `admin`    | `admin123`     | Administrador  |
| `empleado` | `empleado123`  | Empleado       |

---

## 2. Qué pantallas incluye y cómo corresponden al boceto

| Pantalla del boceto            | Archivo Kotlin                                            |
|---------------------------------|------------------------------------------------------------|
| Inicio de Sesión                | `ui/login/LoginScreen.kt`                                  |
| Panel (dashboard)                | `ui/panel/PanelScreen.kt`                                  |
| Ganado (lista + búsqueda)        | `ui/ganado/GanadoListScreen.kt`                             |
| Registrar Animal                 | `ui/ganado/RegistrarAnimalScreen.kt`                        |
| Vender (menú)                    | `ui/vender/VenderMenuScreen.kt`                             |
| Venta Individual                 | `ui/vender/VentaIndividualScreen.kt`                        |
| Jaulas                           | `ui/vender/JaulasScreen.kt`                                 |
| Registrar Muerte                 | `ui/vender/RegistrarMuerteScreen.kt`                        |
| Finanzas                         | `ui/finanzas/FinanzasScreen.kt`                             |
| Reportes (menú Ganado/Finanzas)  | `ui/reportes/ReportesMenuScreen.kt`                         |
| Reportes de Ganado               | `ui/reportes/ReportesGanadoScreen.kt`                       |
| Reportes de Finanzas             | `ui/reportes/ReportesFinanzasScreen.kt`                     |
| Configuración                    | `ui/configuracion/ConfiguracionScreen.kt`                   |
| Menú lateral (Panel/Ganado/…)    | `ui/components/AppDrawer.kt`                                |j

La navegación completa (rutas, quién ve el menú lateral, etc.) está en
`ui/navigation/RanchoNavHost.kt`.

---

## 3. Arquitectura

```
com.rancho.gestion
├── data/
│   ├── entities/       Entidades Room + enums de negocio + TypeConverters
│   ├── dao/             Interfaces DAO (consultas SQL vía Room)
│   ├── repository/      Reglas de negocio sobre los DAOs (Auth, Ganado,
│   │                     Finanzas, Configuracion)
│   └── AppDatabase.kt   Base de datos Room + precarga de usuario admin y
│                         rangos de peso de ejemplo
├── domain/               Lógica de negocio pura (sin Android ni Compose):
│                          clasificación por peso, etapas/alertas de tiempo,
│                          cálculo de merma, capacidad de jaulas
│                          → cubierta por pruebas unitarias, ver más abajo
├── sync/                 WorkManager: SyncWorker (punto de extensión para
│                          backend en la nube), MaintenanceWorker (purga de
│                          archivo histórico) y AlertasWorker (notificación
│                          diaria de etapas por vencer)
├── util/                 Sesión de usuario, hashing de contraseña (PBKDF2 +
│                          salt), fechas, notificaciones locales y notas
│                          sobre el lector de código de barras
├── ui/
│   ├── theme/            Colores/tipografía/formas (paleta de marca)
│   ├── navigation/        Rutas y NavHost
│   ├── components/        Piezas reutilizables: tarjetas, barras, diálogos,
│   │                       estados vacíos, insignias de etapa y la marca
│   │                       (BrandMark.kt) reutilizada en Login/Drawer
│   └── <cada módulo>/     Una carpeta por sección de la app
├── AppContainer.kt        Inyección de dependencias manual (sin Hilt)
├── RanchoApp.kt            Application: crea el AppContainer y programa sync
└── MainActivity.kt          Punto de entrada; monta RanchoNavHost
```

No se usa Hilt/Dagger para mantener el prototipo simple de leer: todas las
dependencias se crean una sola vez en `AppContainer` y se pasan hacia abajo
por Compose.

---

## 4. Cómo se cubrió cada requerimiento del boceto

### Módulo 1 — Gestión de Ganado
- **Entrada de ganado / Arete como PK:** `Animal.arete` es la llave primaria
  de Room; `RegistrarAnimalScreen` captura Arete, Color, Sexo, Peso, Precio
  y Proveedor.
- **Clasificación automática por peso:** `domain/ClasificadorPeso` compara
  el peso contra los `RangoPeso` configurados en `Configuracion` y se usa en
  la lista de Ganado y al crear/llenar Jaulas.
- **Control de etapas y tiempos (Acopio / Desarrollo y Engorda):**
  `domain/EtapaCalculator` calcula días en el rancho y marca alertas según
  los rangos del boceto (8–15 días de Acopio; 60–75 días de Engorda). Es un
  cálculo puro, listo para mostrarse como badge/notificación donde se
  requiera.
- **Búsqueda por arete:** `GanadoListScreen` (búsqueda en vivo) y
  `RegistrarMuerteScreen` (búsqueda + confirmación).
- **Salida por venta o muerte:**
  - Venta individual → `VentaIndividualScreen` + `GanadoRepository.venderIndividual`.
  - Venta por jaula → `JaulasScreen` (botón "Vender" de cada jaula) +
    `GanadoRepository.venderJaula` (reparte el precio total proporcional al
    peso de cada animal y registra `VentaJaula`).
  - Muerte → `RegistrarMuerteScreen`, exige motivo, calcula la **merma**
    (`domain/MermaCalculator`: pérdida = precio de compra − valor
    recuperado, igual que el ejemplo del boceto: $20,000 − $8,000 = $12,000).
- **Jaulas con tope automático:** `domain/JaulaCapacidad` calcula cabezas y
  toneladas acumuladas y bloquea agregar más animales cuando se alcanza el
  tope (110 cabezas o 24 t + 0.5 t de tolerancia).
- **Separación de vistas activo/archivo:** `AnimalDao.getActivos()` vs.
  `getArchivados()`; al vender o morir, el animal cambia de `estado` y deja
  de aparecer en las listas activas.
- **Purga de archivo a 3 años:** `GanadoRepository.purgarArchivoAntiguo()` +
  `AnimalDao.purgarAnterioresA(...)`, listo para engancharse a un
  `MaintenanceWorker` periódico (ver `sync/SyncManager.kt`).

### Arquitectura y Plataforma
- **Offline:** toda la app lee/escribe contra Room (SQLite local); no hay
  ninguna llamada de red en el camino crítico.
- **Sincronización a la nube:** `sync/SyncManager.kt` deja programado un
  `CoroutineWorker` periódico con `Constraints(NetworkType.CONNECTED)` — el
  punto exacto donde conectar tu backend (Firebase/Firestore, API REST
  propio, etc.) cuando decidas cuál usar. Está comentado con TODOs claros.
- **Lector de código de barras:** ver `util/BarcodeScanner.kt`. La mayoría
  de "pistolitas" USB/Bluetooth funcionan en modo teclado (HID): escriben
  directo sobre el campo "Arete" sin necesitar código adicional. Se dejó
  también el punto de extensión para lectura por cámara (CameraX + ML Kit,
  dependencias comentadas en `app/build.gradle.kts`).

### Seguridad y Roles
- Login contra `usuarios` (usuario + contraseña con hash SHA-256 —
  ver nota de seguridad más abajo).
- `util/SessionManager.kt` guarda la sesión y expone `isAdmin()`.
- **Administrador:** ve y usa todo, incluyendo eliminar animales/jaulas,
  eliminar rangos de peso, y accede a **Reportes** y **Configuración**
  (ítems que ni siquiera se muestran en el menú lateral si el usuario es
  Empleado).
- **Empleado:** puede registrar animales, ventas y muertes, y egresos, pero
  no ve botones de "Eliminar" ni puede editar rangos de peso; tampoco ve
  "Reportes" ni "Configuración" en el menú.

### Módulo 2 — Control Financiero
- `FinanzasScreen` muestra Total / Nómina / Gasolina / Fármacos / Alimento /
  Otros del mes en curso y permite **Registrar Egreso** (con campos
  especiales de Nómina: nombre del empleado y días trabajados).
- `ReportesFinanzasScreen` filtra por periodo y categoría con gráfico de
  barras por categoría.

### Generación de Reportes
- `util/DateUtils.kt` centraliza el cálculo de periodos (Diario / Semanal /
  Mensual / Anual) usando **siempre el día 1 del mes como fecha de corte**,
  tal como pide la nota final del boceto.
- `ReportesGanadoScreen`: filtra por proveedor (entrada) y destino/comprador
  (salida), incluye activos + archivados, y grafica Entradas vs. Ventas vs.
  Muertes.
- `ReportesFinanzasScreen`: filtra por categoría y grafica el gasto por
  categoría.
- Los gráficos (`ui/reportes/SimpleBarChart.kt`) se dibujan con
  `Canvas` de Compose — no se agregó ninguna librería de gráficas externa
  para mantener el proyecto ligero.

---

## 5. Decisiones y supuestos (para que no haya sorpresas)

- El boceto de "Finanzas" solo dibuja 4 categorías + Total, pero el texto de
  requerimientos pide también **Alimento** como categoría obligatoria; se
  agregó una tarjeta extra para no perder ese requerimiento.
- Las 5 tarjetas del "Panel" del boceto (`200 Cabezas`, `58.4 Toneladas`,
  `200 Cabezas`, `$9,999,999 Pesos`, `-$9,999 Egresos`) traían una tarjeta de
  "Cabezas" duplicada; se reinterpretó como **Cabezas vendidas del mes**
  para que las 5 tarjetas aporten información distinta (activas, toneladas
  activas, vendidas del mes, ingresos del mes, egresos del mes).
- El hash de contraseña usa **PBKDF2WithHmacSHA256 con salt aleatorio por
  usuario** (120,000 iteraciones — ver `util/PasswordHasher.kt`), suficiente
  para un prototipo con base de datos local de un solo dispositivo. Para un
  backend multiusuario real, de todas formas se recomienda mover la
  autenticación a un servicio dedicado (no solo por el algoritmo de hash,
  sino por manejo de sesiones, rate limiting, recuperación de cuenta, etc.).
- No se integró un backend real (Firebase, API propio, etc.) porque no fue
  especificado; `sync/SyncManager.kt` deja el punto de extensión listo
  (`SyncWorker.doWork()` con los `TODO(backend)` marcados).
- El escaneo de arete por cámara (ML Kit) se dejó como dependencia opcional
  comentada — actívala solo si de verdad la vas a usar, para no inflar el
  tamaño del build. La foto del animal (distinta del escaneo del arete) sí
  quedó implementada con la cámara nativa vía `Intent`, sin ML Kit.
- Las notificaciones de alerta (`sync/AlertasWorker`) son un resumen diario
  ("N animales necesitan atención"); no listan cada arete individualmente
  para no saturar al usuario — el detalle se ve en la tarjeta de Alertas del
  Panel o en la lista de Ganado.

## 6. Próximos pasos sugeridos (fuera del alcance de un prototipo)

- [x] ~~Conectar `SyncWorker`/`MaintenanceWorker` a un backend real y
  programar `MaintenanceWorker` en `RanchoApp.onCreate`.~~ `MaintenanceWorker`
  ya purga el archivo histórico y está programado; **falta únicamente**
  decidir y conectar el backend real dentro de `SyncWorker.doWork()`.
- [x] ~~Mover contraseñas a un esquema con *salt*~~ — hecho con PBKDF2 (ver
  sección 5). Seguiría pendiente un backend de autenticación si la app deja
  de ser de un solo dispositivo.
- [x] ~~Agregar fotografía del animal (cámara)~~ — implementado en
  `RegistrarAnimalScreen` con `ActivityResultContracts.TakePicture`.
- [x] ~~Notificaciones push/locales para las alertas de `EtapaCalculator`~~
  — implementado como notificación local diaria (`sync/AlertasWorker`); no
  requirió backend porque toda la información ya vive en Room.
- [x] ~~Pruebas unitarias sobre `domain/BusinessLogic.kt`~~ — ver
  `app/src/test/java/com/rancho/gestion/domain/BusinessLogicTest.kt`.
- [ ] Conectar un backend real (Firebase/Firestore, API propio, etc.) dentro
  de `SyncWorker` — es la única pieza que depende de una decisión externa
  (qué proveedor/backend usar) y no se puede completar sin esa decisión.
- [ ] Pruebas de UI/instrumentadas (`androidTest`) para los flujos críticos:
  login, registrar animal, vender, registrar muerte.
- [ ] Llevar Finanzas, Jaulas y Configuración al mismo nivel de pulido
  visual que Panel/Ganado/Login (íconos, estados vacíos, tarjetas con
  elevación) — esta iteración priorizó las pantallas de mayor uso diario.
- [ ] Soporte de tema oscuro (hoy la app fuerza siempre el esquema claro,
  a propósito, para igualar el boceto original).
