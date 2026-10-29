# Munch: cliente, administración y repartidor

## Comparación e integración de la referencia

Proyecto local revisado: `C:\Users\Fatima\AndroidStudioProjects\Munch`.
La rama inicial era `main`, commit `724e4bd`, sin cambios pendientes.
Referencia descargada: `https://github.com/Yagel-ls/APPMOVIL_CHEF_MUNCH`,
`feature-pagos-admin`, commit `46a5fe0ae2d15bfdaba693a536f73fd1900ca290`.
El commit local es su antecesor común; no estaban los pagos Firebase ni `admin`.
No se encontraron archivos AGENTS.md dentro del proyecto ni en la referencia.

Se creó `feature-entregas-videos` conservando `main`. Se verificó con
`git apply --check` y aplicó el parche de `app/src`, `admin/src`, los dos Gradle
de módulo, settings, Gradle raíz y catálogo de dependencias. Se excluyeron los
APK, clases, cachés y cambios de IDE de la referencia. No se hizo reset, merge
que importara artefactos, commit, push ni despliegue. Los archivos Firebase
de app y admin de la referencia se reutilizaron solo como archivos locales
ignorados. No se copió una aplicación de ejemplo sobre el proyecto.

Se conserva la interfaz crema/coral/verde, comprobante de transferencia,
verificación/rechazo por admin, preparación, estados de pedidos y notificaciones
admin de la referencia. Se amplía el flujo con inicio/finalización desde el
repartidor. El botón de entregar a repartidor de admin se sustituye por asignación:
el estado en ruta se activa cuando el repartidor inicia efectivamente su recorrido.
Admin conserva la posibilidad de marcar una entrega finalizada y eso detiene
también el seguimiento del repartidor conectado.

## Archivos y arquitectura

- `app/src/main/java/com/example/munch/MainActivity.kt`: sesiones de cliente,
  pedidos propios, recuperación del historial, UUID, dirección/referencias/punto,
  seguimiento y videos después de Montaje.
- `admin/src/main/java/com/example/munch/admin/`: interfaz original de pagos,
  `AdminIntegracion.kt` con secciones de pedidos/videos y repartidores registrados.
- Los modelos y repositorios de `app` y `admin` conservan la lectura de documentos
  y comprobantes antiguos. Los campos nuevos tienen valores predeterminados.
- `repartidor/`: tercera aplicación, autenticación con rol, pedidos asignados,
  mapas externos, inicio/reanudación/cancelación/finalización y servicio GPS real.
- `shared/src/main/java/com/example/munch/integracion/`: sesión, transacciones,
  mapas, seguimiento, datos bancarios y videos. Pertenece a la biblioteca Android
  `:shared`, consumida por las tres aplicaciones con `implementation(project(":shared"))`.
  No se registra como raíz de fuentes de las aplicaciones ni es una cuarta app.
  `DatosBanco` conserva su paquete `com.example.munch.data` dentro de esa biblioteca.
- `shared/src/test/`: pruebas de política de asignación, transiciones y permisos.
- `firebase/`: reglas, índices, pruebas de acceso y herramienta explícita de roles.
- Gradle, catálogo y manifiestos: dependencias, aplicaciones y permisos.

## Configuración externa necesaria (no ejecutada automáticamente)

1. Abre el proyecto Firebase existente `munchapp-9829d`. No crees otro para cada
   aplicación. Verifica las inscripciones Android:
   `com.example.munch`, `com.example.munch.admin`,
   `com.example.munch.repartidor` (esta última debe registrarse en consola).
2. Descarga el `google-services.json` correspondiente a cada paquete y colócalo
   en `app/`, `admin/` y `repartidor/`. Están ignorados por Git. El plugin se aplica
   solo si existe el archivo: se puede compilar sin configuración, pero al abrir
   la app mostrará que falta Firebase y no inventará datos ni iniciará sesión.
   Nunca renombres el JSON de cliente para hacerlo pasar por el del repartidor.
3. Authentication: habilita Email/Password. El cliente puede crear su cuenta.
   Crea las cuentas admin y repartidor desde un entorno de confianza; la app
   no permite autoconceder esos roles. Anota sus UID.
4. Crea Firestore (base `(default)`) y Storage en ese mismo proyecto. Verifica el
   bucket de los archivos locales. Storage requiere plan Blaze y cuenta de
   facturación según la política vigente; el uso de almacenamiento y descargas,
   especialmente videos compartidos, puede generar cargos. Configura presupuestos.
5. Roles: utiliza custom claims `admin: true` y `repartidor: true` con Admin SDK.
   Hay una herramienta **manual** `firebase/scripts/roles.mjs`. Requiere una
   estación de confianza con Application Default Credentials y permisos IAM,
   y `GOOGLE_CLOUD_PROJECT` explícito. Ejemplo en PowerShell:

   ```powershell
   $env:GOOGLE_CLOUD_PROJECT = 'munchapp-9829d'
   npm run roles -- UID_ADMIN admin
   npm run roles -- UID_REPARTIDOR repartidor 'Nombre del repartidor'
   ```

   No se ejecutó esta herramienta durante la implementación. Conserva claims
   existentes y, para repartidor, registra `repartidores/{UID}` con `nombre` y
   `activo: true`. Las aplicaciones móviles no pueden escribir ese directorio.
   Para revocar, usa `revocar-admin` o `revocar-repartidor`. Cierra y vuelve a
   abrir sesión para obtener un token nuevo. La revocación de claims por sí sola
   puede tardar hasta que expire/renueve el token; un operador puede revocar
   sesiones con Admin SDK si necesita invalidarlas de forma urgente.
   No guardes credenciales de servicio en el repositorio.
6. Datos bancarios reales: crea desde consola o Admin SDK
   `configuracion/pagos` con strings `banco`, `clabe` (18 dígitos), `titular`,
   `concepto`. Se retiró la CLABE ficticia de la referencia. Si faltan o no se
   pueden consultar, transferencia queda deshabilitada y se explica el problema;
   pago al recibir sigue disponible. El estado heredado `PAGO_CONFIRMADO` para
   pago al recibir significa pedido aceptado, no que el efectivo ya se cobró.
7. Revisa y, cuando decidas hacerlo, despliega manualmente `firebase/firestore.rules`,
   `firebase/storage.rules` y los índices de `firebase/firestore.indexes.json`.
   `firebase.json` deja declaradas sus rutas. **No se desplegaron reglas**.
   Concede la integración de reglas Storage/Firestore cuando Firebase lo solicite.
   Las reglas restringen clientes a sus UID, repartidores a sus asignaciones,
   transiciones/campos específicos y admin a sus custom claims. Los permisos
   efectivos dependen de publicar estas reglas; el código cliente no reemplaza
   el control del servidor. `comprobanteUri` se excluye de índices por su tamaño.
8. Los pedidos antiguos sin `clienteUid` siguen disponibles a admin. Asocia un
   UID solo tras verificar la identidad del comprador con un operador de confianza;
   no hay reclamación pública de pedidos por número. No se borran ni se migran
   automáticamente. Los nuevos usan UUID completo y se consultan por UID.
9. Maps: habilita Maps SDK for Android y la facturación correspondiente en Google
   Cloud. Crea una clave restringida por paquete Android y SHA-1 del certificado
   de firma, y por API (Maps SDK for Android). Añade a `local.properties`:

   ```properties
   MAPS_API_KEY=tu_clave_restringida
   ```

   No subas ese archivo a Git. La clave de un SDK Android termina en el APK;
   las restricciones de aplicación/API son necesarias. Si está vacía, la UI
   muestra un mensaje y permite escribir coordenadas; no construye el mapa.
   Una clave inválida puede producir un mapa sin teselas: revisa Logcat,
   restricciones, facturación y habilitación de la API. El cliente selecciona
   un destino explícito; no se solicita su ubicación ni se asume que vive donde está.

## Ejecutar en Android Studio

Abre esta carpeta y sincroniza Gradle con el JDK incluido en Android Studio.
Se conserva AGP 9.0.1/Gradle 9.1 y minSdk 24/targetSdk 36. En la barra Run,
crea/selecciona configuraciones Android App para `app`, `admin` y `repartidor`,
con Launch Default Activity. Cada paquete instala una aplicación independiente.
Cliente y admin ya tienen configuración Firebase local recuperada de referencia;
repartidor necesita su inscripción/JSON. Usa dispositivos con Google Play Services
para Maps. No hace falta cambiar de proyecto para ejecutar otro módulo.

Comandos equivalentes:

```powershell
.\gradlew.bat :app:assembleDebug :admin:assembleDebug :repartidor:assembleDebug
.\gradlew.bat :shared:testDebugUnitTest :app:testDebugUnitTest :admin:testDebugUnitTest :repartidor:testDebugUnitTest
npm ci --ignore-scripts
npm run test:rules
```

Las pruebas de reglas usan exclusivamente `demo-munch` y los emuladores de
Firestore/Storage; no requieren credenciales ni cambian el proyecto remoto.
Si Windows restringe las carpetas de usuario, ejecuta desde una terminal con
acceso a tu caché normal de Gradle/npm. No borres cachés ni bloqueos para resolverlo.
Firebase CLI/emuladores requieren Node compatible y Java 21; usa el JDK de Studio.

## Prueba completa en dos dispositivos

1. Termina la configuración anterior y usa reglas revisadas en un entorno de
   pruebas. En A instala `app` y, si no tienes un tercer dispositivo, también
   `admin`. En B instala `repartidor`. Son paquetes y sesiones independientes.
2. En A crea una cuenta cliente. Elige un kit, conserva dirección escrita y
   referencias, y pulsa un destino en el mapa o escribe coordenadas. Puedes
   dejar el punto vacío y entregar usando la dirección. No se toma tu GPS.
3. Completa fecha/horario. Para probar transferencia, sube un comprobante y
   envía; comprueba que no cambia a pagado antes de la revisión de admin. Prueba
   también rechazo de comprobante y el flujo de pago al recibir. Los comprobantes
   heredados se mantienen como imágenes comprimidas; los videos no usan Base64.
4. En admin entra con la cuenta autorizada, revisa el comprobante, aprueba y
   marca preparación. Selecciona el repartidor registrado y pulsa Asignar pedido.
   Un pedido sin pago aceptado no puede asignarse; una ruta activa no puede
   reasignarse. Una cuenta sin rol admin no entra al panel ni tiene permisos servidor.
5. En B entra con la cuenta repartidor. Debe aparecer solo su asignación, con
   destino, referencias, horario y método de pago. Comprueba que otra cuenta
   repartidor no puede verla. Abre el destino en mapas.
6. Pulsa Iniciar entrega: solo entonces se piden permisos y arranca el servicio
   location con notificación. Acepta primero ubicación aproximada y comprueba
   el aviso/precisión; repite con precisa. Android 13+ solicita notificaciones.
   Denegarlas no concede acceso GPS; Android conserva el aviso del servicio en
   su administrador de tareas aunque oculte la notificación del cajón.
7. Muévete físicamente con B. En A abre Tu paquete (también desde Mis pedidos tras
   cerrar la app): observa la posición real, precisión, hora y estado. Las lecturas
   se solicitan cada 15 s con 20 m y se limitan además en aplicación; nunca se
   envía más de una cada 15 s. Un movimiento menor se publica como máximo cada
   60 s si el proveedor entrega una lectura nueva. Solo se aceptan mediciones de
   menos de 60 s; la UI marca desactualización tras 2 min.
8. Manda B al fondo y apaga su pantalla: comprueba la continuidad y notificación.
   Rotar la Activity no debe detener el servicio. Prueba denegar/revocar permisos,
   GPS apagado y desconexión. Sin red no se encolan nuevas ubicaciones; se vuelve
   a enviar una lectura reciente al recuperarla. El último punto se conserva y
   se muestra su antigüedad. No hay ubicación simulada de respaldo; las lecturas
   marcadas mock se rechazan explícitamente.
9. Cancela el recorrido: debe parar el servicio y volver a preparación; no marca
   entregado al cliente. Reanuda explícitamente. Finaliza: estado entregado,
   servicio y notificación detenidos, sin nuevas escrituras GPS. Si falla el cambio
   remoto por falta de red, se explica que el seguimiento paró y se puede reintentar
   finalizar o reanudar. Prueba también finalizar desde admin.
10. Si Android mata el proceso o el usuario fuerza cierre/detiene el servicio,
    no hay reinicio GPS silencioso: el pedido sigue en curso y B permite Reanudar
    seguimiento desde la pantalla. Las restricciones de batería del fabricante
    pueden afectar la continuidad; verifica el equipo real y las políticas del negocio.

## Subir los tres videos y comprobar QR

En admin → Videos de cocina, usa Seleccionar video (selector de documentos):

| Archivo del dispositivo | Kit | Título sugerido |
|---|---|---|
| PASTA.mp4 | Date Night | Pasta paso a paso |
| PIZZA.mp4 | Family | Pizza en familia |
| TACOS.mp4 | Friends | Tacos con amigos |

Los archivos no vienen incluidos. Puedes subir varios por kit. Escribe título,
elige kit y pulsa Subir y publicar. Se conserva permiso de lectura persistente;
Storage usa `putFile`, sin leer el archivo entero a RAM. Límite de **1 GiB por
archivo**, validado en cliente y reglas, sin límite de 15 minutos ni de duración.
Si el proveedor no indica el tamaño, copia el archivo al almacenamiento local.
Para reproducción amplia en Android y navegadores usa MP4 con H.264/AAC. No se
realiza transcodificación; otros formatos pueden depender del teléfono.

Prueba cancelar durante transferencia, interrumpir red, reintentar y rotar la
pantalla: el progreso vive en ViewModel. Al publicar metadatos se desactiva la
cancelación para no prometer cancelar una operación ya confirmándose. Si muere
el proceso, selecciona de nuevo o vuelve a subir; no se promete reanudar tras
forzar cierre. Un archivo sin metadatos publicados no aparece a clientes.

En app → Cocina paso a paso se mantienen Preparación, Cocción y Montaje;
después aparecen los videos publicados del kit. Abre el reproductor, reproduce,
pausa, sal y vuelve: Media3 pausa al ir al fondo y libera recursos al cerrar.
Prueba una desconexión para ver carga/error y reintento. Sin videos hay un estado vacío.

Escanea el QR con el otro teléfono (incluso sin Munch) y abre la URL en el navegador.
Comprueba también Abrir enlace en este teléfono. El QR se codifica con ZXing
localmente y contiene el HTTPS de descarga Firebase, nunca una ruta del dispositivo
ni una petición a un generador QR externo. Verifica búsqueda temporal/avance del
MP4 con el navegador concreto y prueba al menos un archivo de más de 15 minutos.

**Los enlaces de descarga son compartibles**: quien tenga el enlace/QR puede ver
el archivo, incluso sin autenticarse. Las reglas protegen la consulta y descarga
por SDK; el token de descarga es una capacidad compartida. Dejar de publicar lo
oculta del catálogo, pero no revoca enlaces ya conocidos. Reemplazar crea otro
objeto y cambia metadatos solo cuando la subida termina; el video anterior queda
retenido para no romper QR impresos. Esto consume almacenamiento. Para revocar
definitivamente enlaces anteriores, un operador debe revocar/rotar tokens o borrar
el objeto antiguo en consola, tras revisar qué QR necesita conservar. No hay
borrado remoto automático ni una promesa de acceso privado para QR compartibles.

## Documentación oficial consultada

- [Permisos de ubicación Android](https://developer.android.com/develop/sensors-and-location/location/permissions).
- [Servicios foreground y tipo location](https://developer.android.com/develop/background-work/services/fgs/service-types).
- [Kotlin integrado en AGP 9 y directorios de fuentes](https://developer.android.com/build/migrate-to-built-in-kotlin).
- [Media3: creación y liberación del reproductor](https://developer.android.com/media/media3/exoplayer/hello-world) y [versiones](https://developer.android.com/jetpack/androidx/releases/media3).
- [Maps Android, claves y facturación](https://developers.google.com/maps/documentation/android-sdk/start) y [Maps Compose oficial](https://github.com/googlemaps/android-maps-compose).
- [Firebase custom claims](https://firebase.google.com/docs/auth/admin/custom-claims) y [notas SDK Android](https://firebase.google.com/support/release-notes/android).
- [Storage: subida de archivos](https://firebase.google.com/docs/storage/android/upload-files) y [requisitos Blaze](https://firebase.google.com/docs/storage/faqs-storage-changes-announced-sept-2024).
- [StorageReference.putFile](https://firebase.google.com/docs/reference/android/com/google/firebase/storage/StorageReference) y [UploadTask, progreso y cancelación](https://firebase.google.com/docs/reference/android/com/google/firebase/storage/UploadTask).

Se conservan versiones estables compatibles con el proyecto y el BOM de Firebase
de referencia; no se usan módulos Firebase KTX descontinuados. Las dependencias
nuevas están fijadas en Gradle para que las compilaciones sean reproducibles.

Los resultados y las comprobaciones pendientes se registran en `VERIFICACION.md`.
Si una publicación se interrumpe mientras Firestore la está confirmando, se
conserva el objeto de Storage: evita borrar un video cuyos metadatos pudieran
haberse confirmado. Un operador puede revisar objetos huérfanos en consola.
