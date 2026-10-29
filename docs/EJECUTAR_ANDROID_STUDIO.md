# Estado comprobado para ejecutar desde Android Studio

Revisión del 27 de octubre de 2026, posterior a `CORRECCION_SHARED.md`.

## Configuración y compilación

- `shared` es una biblioteca Android (`com.android.library`) con Compose.
- `settings.gradle.kts` incluye app, admin, repartidor y shared.
- Las tres aplicaciones consumen `implementation(project(":shared"))`.
- Ninguna aplicación registra `shared/src/main/java` ni sus pruebas en sus
  sourceSets. Las seis fuentes de integración conservan sus paquetes y funciones.
- El registro de módulos de `.idea/gradle.xml` incluye los cuatro módulos.
- No fue necesario modificar la lógica de pagos, GPS, videos o QR en esta revisión.
- La configuración Run existente `app` apunta a `Munch.app`, abre la actividad
  predeterminada y tiene `CLEAR_APP_STORAGE=false`.

Se ejecutó realmente:

```powershell
.\gradlew.bat :app:assembleDebug :admin:assembleDebug :repartidor:assembleDebug --console=plain --max-workers=2
```

Resultado: **BUILD SUCCESSFUL en 9 segundos**, 130 tareas (9 ejecutadas y 121
UP-TO-DATE). Se generaron/verificaron los tres APK. No se ejecutaron nuevamente
las suites de pruebas unitarias ni de reglas en esta revisión.

## Instalación y arranque comprobados

El emulador `emulator-5554` (AVD `Pixel_6_Pro`) aparecía conectado, pero la
instalación, las órdenes shell y la transferencia de archivos no respondían.
Reiniciar el servidor ADB no bastó: quedó offline. Se cerró normalmente ese AVD
y se arrancó con `-no-snapshot-load`, conservando su disco y aplicaciones.
No se usó Wipe Data, no se desinstaló la aplicación ni se borraron sus datos.

Después del arranque se comprobó `sys.boot_completed=1`. La instalación del APK
actual con `adb install -r` terminó en **Success**. La orden `am start -W` devolvió:

```text
Status: ok
LaunchState: COLD
Activity: com.example.munch/.MainActivity
```

El proceso Munch permaneció activo (PID 3144 en esta ejecución) y MainActivity
quedó como actividad visible. Se verificó la pantalla de acceso con correo,
contraseña, Entrar y Crear cuenta de cliente. No se introdujeron credenciales,
no se crearon cuentas y no se modificó Firebase remoto.

Durante el arranque del AVD apareció un aviso **System UI isn't responding**.
Se eligió Wait y desapareció. Era un aviso del sistema Android; Munch continuó
ejecutándose. El buffer de crashes y los errores recientes filtrados por el PID
de Munch quedaron vacíos. El buffer general contenía fallos del servicio UWB
del emulador, que no corresponden al proceso de Munch.

Captura local de la pantalla de acceso: `build/smoke/cliente.png` (ignorada por
Git). Esta verificación confirma instalación/arranque, no la prueba funcional
completa de pagos, GPS, videos y QR, que mantiene los requisitos de configuración
externa de `CONFIGURACION_Y_PRUEBAS.md`.

## Paso exacto en Android Studio

1. En este proyecto abierto, pulsa **File → Sync Project with Gradle Files**.
2. Al terminar, selecciona **app** en el desplegable de configuración Run.
3. Selecciona **Pixel 6 Pro / emulator-5554** en el desplegable de dispositivos.
4. Pulsa **Run ▶** o **Shift+F10**. Debe abrir la pantalla de acceso de Munch.

`shared` no se ejecuta: es una biblioteca. Para las otras aplicaciones usa una
configuración Android App con módulo admin o repartidor y Launch Default Activity.
Si el IDE mantiene avisos antiguos tras sincronizar, revisa el error real en la
ventana Sync; la compilación e instalación aquí descritas sí se comprobaron.

Se conservaron los cambios locales. No se hizo push ni despliegue de Firebase.

## Revalidación posterior

Se volvió a revisar el estado sin alterar la lógica ni los cambios existentes.
La configuración de `:shared` seguía correcta y sin sourceSets superpuestos.
La misma compilación de los tres módulos terminó con **BUILD SUCCESSFUL en 8 s**:
130 tareas, 7 ejecutadas y 123 UP-TO-DATE.

El emulador estaba conectado. `adb install -r` volvió a devolver **Success** y
Munch quedó como `topResumedActivity` en `.MainActivity`, con proceso activo
(PID 15448 en esta ejecución). Los buffers de errores y crashes filtrados por
ese proceso estaban vacíos. No hizo falta reiniciar el AVD en esta revalidación.

Al abrir justo después de actualizar el APK, `am start` mostró transitoriamente
PackageUpdateActivity de System UI; la comprobación posterior confirmó
MainActivity en primer plano. UI Automator devolvió `null root node` en este
intento: no se utilizó el XML anterior como evidencia de una captura nueva.
La verificación del arranque actual se apoya en actividad visible, proceso y
Logcat. No se reejecutaron pruebas unitarias, instrumentadas ni flujos con Firebase.
