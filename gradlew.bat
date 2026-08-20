@rem Gradle startup script for Windows.
@rem NOTA: gradle-wrapper.jar no está incluido en este paquete; abre el
@rem proyecto en Android Studio para que lo regenere, o ejecuta
@rem "gradle wrapper --gradle-version 8.7" con un Gradle instalado localmente.
@echo off
gradle %* --project-dir "%~dp0"
