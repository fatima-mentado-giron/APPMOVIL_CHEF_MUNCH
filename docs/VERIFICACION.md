# Verificación del 26 de octubre de 2026

Registro histórico: desde el 27 de octubre el código compartido pertenece a una
biblioteca Android `:shared`. Consulta `CORRECCION_SHARED.md` para la configuración
y la compilación más recientes; las pruebas compartidas ya no se repiten por app.
La instalación y el arranque del cliente, antes pendientes por bloqueo del
emulador, se verificaron posteriormente: consulta `EJECUTAR_ANDROID_STUDIO.md`.

Rama de trabajo: `feature-entregas-videos`. Referencia comparada:
`feature-pagos-admin` de Yagel-ls, commit `46a5fe0`.
Los cambios permanecen en el árbol de trabajo; no se hizo commit ni push.

## Resultados comprobados

Última ejecución Gradle: **BUILD SUCCESSFUL**, 158 tareas, 1 min 39 s.

```text
:app:assembleDebug
:admin:assembleDebug
:repartidor:assembleDebug
:app:testDebugUnitTest
:admin:testDebugUnitTest
:repartidor:testDebugUnitTest
:app:lintDebug
:admin:lintDebug
:repartidor:lintDebug
```

| Módulo | Compilación APK | Pruebas JVM | Fallos/errores | Lint |
|---|---|---:|---:|---|
| app | Pasó | 6 | 0 | Sin errores; 37 avisos y 3 sugerencias |
| admin | Pasó | 5 | 0 | Sin errores; 25 avisos |
| repartidor | Pasó | 5 | 0 | Sin errores; 23 avisos |

Las cinco pruebas compartidas se ejecutan en cada módulo: tres sobre política
de asignación/transiciones/permisos/coordenadas y dos sobre QR. La prueba QR
genera y decodifica un QR real con ZXing y verifica que conserva todo el HTTPS,
incluido su token. Se conserva también una prueba aritmética preexistente en app.
No se presentan las 16 ejecuciones como 16 escenarios distintos.

Los avisos Lint se refieren principalmente a versiones más nuevas de dependencias,
uso del catálogo TOML/KTX, recursos sin usar y metadatos de aplicaciones. No hay
errores MissingPermission ni ForegroundServicePermission. Informes completos en
`app/build/reports/lint-results-debug.html`, y las rutas equivalentes admin/repartidor.
Resultados JVM en `*/build/test-results/testDebugUnitTest/`. Esos artefactos no
se versionan.

`npm run test:rules`: **7 pruebas pasaron, 0 fallaron**, en Firestore y Storage
Emulator Suite, proyecto ficticio `demo-munch`. Se comprobaron:

- Creación de pedidos propios, UUID y rechazo de cambios de pago/asignación/rol
  desde cliente.
- Lecturas propias y consultas filtradas; rechazo de pedidos y seguimiento ajenos.
- Asignación solo por admin a repartidor registrado; rechazo durante recorrido.
- Inicio, cancelación y finalización del repartidor, y campos que puede actualizar.
- Ubicación solo en entrega activa, con coordenadas/precisión/fecha válidas;
  rechazo de escrituras tras cancelación.
- Pedidos antiguos visibles para admin y protegidos frente a apropiación por clientes.
- Subida solo admin y tipo video; lectura Storage/Firestore publicada, rechazo
  antes de publicación, tras retirarlo y para usuarios no autenticados por SDK.

Los mensajes PERMISSION_DENIED del emulador en pruebas negativas son esperados.
Los emuladores se cerraron tras las pruebas. No se desplegaron reglas ni se tocó
la base remota. `npm install` avisó de dependencias transitivas antiguas del CLI
y del rango Node de superstatic; la suite se ejecutó correctamente con el Node
24 instalado. Para reproducir con su rango declarado se recomienda Node 22.

`git diff --check`: pasó. Solo hubo aviso de conversión LF/CRLF de un archivo IDE
preexistente; no se sobrescribieron archivos IDE. No se copiaron APK/cachés de la
referencia ni se borraron cachés o archivos de bloqueo.

## Comprobaciones que no se pudieron completar

Se detectó `emulator-5554`, con Android iniciado (`sys.boot_completed=1`).
El intento de `adb install -r` no devolvió resultado y se interrumpió la espera.
Un reintento `adb install --no-streaming -r` terminó por **timeout de 60 s**.
No se reinició ni limpió el emulador y no se desinstaló la app existente. No se
puede afirmar que las tres nuevas aplicaciones hayan pasado una prueba de
arranque/interfaz en ese emulador; la secuencia se detuvo en la instalación cliente.

Quedan pendientes pruebas instrumentadas/manuales de interfaz, un pedido completo
con dos teléfonos físicos y GPS real, permisos del sistema por versión Android,
ubicación aproximada, GPS apagado, desconexión, continuidad con pantalla apagada,
cancelación y finalización con Firebase configurado. Las pruebas JVM verifican la
política de permisos, no un diálogo real del sistema Android.

También quedan pendientes las subidas/reproducción de los archivos reales PASTA,
PIZZA y TACOS, un video real de más de 15 minutos y escanear sus QR de Storage
desde otro teléfono. Los archivos no estaban disponibles y no se inventaron ni
subieron reemplazos. La prueba de Storage de 4 bytes verifica reglas, no reproducción.

Antes de estas pruebas se necesitan el JSON Firebase del repartidor, Authentication,
cuentas/claims/directorio de repartidores, datos bancarios reales, Storage/Blaze,
Maps con clave/facturación y publicación manual de reglas/índices. La guía
`CONFIGURACION_Y_PRUEBAS.md` incluye los pasos. No se considera completada la
validación funcional integral hasta hacer estas comprobaciones.
