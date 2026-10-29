# Conexión de shared corregida — 27 de octubre de 2026

## Diagnóstico

Los archivos y funciones de `com.example.munch.integracion` existían y sus paquetes
eran correctos. No faltaban Sesion, PuntoEntrega, PoliticaEntrega, datosBancarios,
SeguimientoPedido ni VideosKit.

La configuración anterior añadía físicamente `shared/src/main/java` y
`shared/src/test/java` a los sourceSets de app, admin y repartidor a la vez.
Eso producía múltiples propietarios de las mismas raíces en el modelo de Android
Studio y el aviso Duplicate content roots. La compilación de las tres aplicaciones
antes de corregirlo terminó con BUILD SUCCESSFUL (12 segundos), aunque el IDE
mostraba referencias no resueltas. Por tanto, eran errores de importación/indexación
provocados por la configuración del proyecto; no un fallo del compilador Kotlin.

## Cambio

- `shared/build.gradle.kts`: nueva biblioteca Android `com.android.library`, con
  Compose, minSdk 24, compileSdk 36, Java 11 y las dependencias que utiliza su código.
- `shared/src/main/AndroidManifest.xml`: manifiesto mínimo de biblioteca.
- `settings.gradle.kts`: incluye `:shared` como módulo real.
- `gradle/libs.versions.toml` y Gradle raíz: declaran el plugin de biblioteca con
  el mismo AGP 9.0.1 existente. Kotlin integrado de AGP se conserva.
- Los tres Gradle de aplicación: eliminan los sourceSets externos y añaden
  `implementation(project(":shared"))`. Las fuentes compartidas tienen un solo dueño.
- `DatosBanco`: se extrajo de los tres ModeloPedido.kt a
  `shared/src/main/java/com/example/munch/data/DatosBanco.kt`, con exactamente los
  mismos valores y el mismo paquete. Las tres copias eran idénticas. Así Banco.kt
  no depende de ninguna aplicación y no se introduce un ciclo de dependencias.
- Las seis fuentes de integración permanecen en su ruta y paquete originales.
  No se quitaron funciones de GPS, pagos, videos, QR ni autenticación.
- Las cinco pruebas compartidas ahora pertenecen solo a `:shared`, en lugar de
  compilarse tres veces como pruebas de las aplicaciones.

No se editaron los metadatos de `.idea`, no se borraron cachés ni bloqueos y no se
hizo push. Se preservaron los cambios locales, incluidos los valores de DatosBanco.

## Actualizar Android Studio

Ejecuta **File → Sync Project with Gradle Files** y espera a que termine la
indexación. Deben aparecer app, admin, repartidor y shared, con shared como biblioteca.
No añadas manualmente shared/src/main/java como Sources Root de una aplicación.
Si sigue mostrando un diagnóstico anterior tras una sincronización correcta,
cierra y vuelve a abrir el proyecto. No es necesario empezar borrando cachés.
La comprobación del índice visual de Android Studio requiere observar el IDE;
el resultado de Gradle se verifica por separado con la compilación de los tres APK.

## Verificación final

La siguiente ejecución terminó con **BUILD SUCCESSFUL en 4 min 35 s**, con
**154 tareas ejecutadas** (sin depender de UP-TO-DATE para la compilación):

```powershell
.\gradlew.bat :app:assembleDebug :admin:assembleDebug :repartidor:assembleDebug :shared:testDebugUnitTest :app:testDebugUnitTest :admin:testDebugUnitTest :repartidor:testDebugUnitTest --console=plain --max-workers=2
```

Se generaron los tres APK y se comprobó que el JAR de compilación de `:shared`
contiene SesionKt, MapasKt, BancoKt, VideosKt, PoliticaEntrega y DatosBanco.
Las cinco pruebas compartidas pasaron en `:shared`, y la prueba preexistente de
app también pasó, sin fallos ni errores. Admin y repartidor no tienen pruebas
unitarias propias; sus tareas terminaron sin casos. No se ejecutaron pruebas
de interfaz ni se comprobó visualmente el índice del IDE.

`git diff --check` pasó, con un aviso preexistente LF/CRLF en `.idea/misc.xml`.
No se reprodujo un fallo real de Gradle antes ni después de corregir el modelo;
sí se eliminó la causa de raíces duplicadas de la configuración Gradle.

## Fuentes de la configuración

- [Bibliotecas Android y dependencias entre módulos](https://developer.android.com/studio/projects/android-library).
- [Kotlin integrado en AGP 9 y directorios de fuentes](https://developer.android.com/build/migrate-to-built-in-kotlin).

## Revisión posterior del registro del IDE

En la revisión siguiente, la biblioteca `:shared` y las dependencias de las tres
aplicaciones ya estaban correctamente declaradas. No se encontraron sourceSets
externos apuntando a `shared/src/main/java`. Sin embargo, `.idea/gradle.xml`
todavía enumeraba únicamente el proyecto raíz y app. Se añadieron admin,
repartidor y shared a ese registro, conservando las demás opciones del IDE.
Esta modificación posterior es la única edición de metadatos IDE de la corrección.

Se ejecutó nuevamente:

```powershell
.\gradlew.bat :app:assembleDebug :admin:assembleDebug :repartidor:assembleDebug :shared:testDebugUnitTest --console=plain --max-workers=2
```

Resultado real: **BUILD SUCCESSFUL en 12 segundos**, con **136 tareas UP-TO-DATE**.
Fue una validación incremental del estado actual; no se presenta como una
recompilación forzada ni como una nueva ejecución de los casos de prueba.
Los paquetes y todas las funciones de integración permanecen presentes.
Se verificó también el XML del registro IDE y `git diff --check`.

Hace falta que Android Studio vuelva a cargar el modelo con **Sync Project with
Gradle Files**. El registro actualizado no demuestra por sí solo que su índice
en memoria ya se haya refrescado. Si los errores persisten después de sincronizar,
hay que revisar el error concreto de esa sincronización. No se borraron cachés,
no se hizo push y no se modificó Firebase remoto.
