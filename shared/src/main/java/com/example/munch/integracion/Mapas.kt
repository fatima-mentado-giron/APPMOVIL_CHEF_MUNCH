package com.example.munch.integracion

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.firebase.firestore.FirebaseFirestore
import com.google.maps.android.compose.*
import kotlinx.coroutines.delay
import java.text.DateFormat
import java.util.Date

@Composable
fun MapaPunto(lat: Double?, lng: Double?, alElegir: ((Double, Double) -> Unit)? = null) {
    val context = LocalContext.current
    val key = remember { context.packageManager.getApplicationInfo(context.packageName, PackageManager.GET_META_DATA).metaData?.getString("com.google.android.geo.API_KEY") }
    if (key.isNullOrBlank()) {
        Text("Mapa no disponible: falta configurar MAPS_API_KEY. Puedes escribir las coordenadas del punto de entrega.")
        return
    }
    val punto = if (lat != null && lng != null) LatLng(lat,lng) else null
    // Neutral world view until the customer explicitly selects a destination.
    val camera = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(punto ?: LatLng(0.0,0.0), if (punto == null) 1f else 15f) }
    LaunchedEffect(punto) { punto?.let { camera.position = CameraPosition.fromLatLngZoom(it,15f) } }
    GoogleMap(Modifier.fillMaxWidth().height(250.dp), cameraPositionState = camera,
        onMapClick = { alElegir?.invoke(it.latitude,it.longitude) }) {
        punto?.let { Marker(state = MarkerState(position = it), title = if (alElegir == null) "Última posición recibida" else "Punto de entrega") }
    }
}

@Composable
fun PuntoEntrega(lat: String, lng: String, referencias: String, cambiar: (String,String,String) -> Unit) {
    Text("Punto de entrega (opcional)", style = MaterialTheme.typography.titleMedium)
    Text("Selecciona en el mapa el destino o escribe coordenadas. Conservaremos tu dirección y referencias; no usamos automáticamente tu ubicación actual.")
    MapaPunto(lat.toDoubleOrNull(),lng.toDoubleOrNull()) { a,b -> cambiar(a.toString(),b.toString(),referencias) }
    OutlinedTextField(lat, { cambiar(it,lng,referencias) }, label = { Text("Latitud, de -90 a 90") }, singleLine = true)
    OutlinedTextField(lng, { cambiar(lat,it,referencias) }, label = { Text("Longitud, de -180 a 180") }, singleLine = true)
    OutlinedTextField(referencias, { cambiar(lat,lng,it) }, label = { Text("Referencias de entrega") })
}

@Composable
fun SeguimientoPedido(id: String) {
    var lat by remember(id) { mutableStateOf<Double?>(null) }
    var lng by remember(id) { mutableStateOf<Double?>(null) }
    var precision by remember(id) { mutableStateOf<Double?>(null) }
    var fecha by remember(id) { mutableStateOf<Long?>(null) }
    var error by remember(id) { mutableStateOf("") }
    var cache by remember(id) { mutableStateOf(false) }
    var ahora by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) { while(true) { ahora = System.currentTimeMillis(); delay(15000) } }
    DisposableEffect(id) {
        val listener = FirebaseFirestore.getInstance().collection("pedidos").document(id).collection("seguimiento").document("actual")
            .addSnapshotListener(com.google.firebase.firestore.MetadataChanges.INCLUDE) { doc,e ->
                error = e?.localizedMessage ?: ""
                lat = doc?.getDouble("latitud"); lng = doc?.getDouble("longitud")
                precision = doc?.getDouble("precision")
                fecha = (doc?.getTimestamp("medidoEn") ?: doc?.getTimestamp("actualizadoEn"))?.toDate()?.time
                cache = doc?.metadata?.isFromCache == true
            }
        onDispose { listener.remove() }
    }
    Text("Ubicación del repartidor", style = MaterialTheme.typography.titleMedium)
    if (lat != null && lng != null) MapaPunto(lat,lng)
    if (fecha == null) Text("Aún no hay ubicación disponible. Se compartirá al iniciar la entrega.")
    else {
        Text("Última actualización: ${DateFormat.getDateTimeInstance().format(Date(fecha!!))}")
        Text("Precisión: ${precision?.toInt() ?: 0} m")
        if (ahora - fecha!! > 120000) Text("Ubicación desactualizada; puede haber problemas de GPS o conexión.", color = MaterialTheme.colorScheme.error)
    }
    if (cache) Text("Datos guardados en el dispositivo; esperando conexión con Firebase.")
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
}

fun abrirDestino(context: android.content.Context, direccion: String, lat: Double?, lng: Double?): Boolean {
    val destino = if (lat != null && lng != null) "$lat,$lng" else direccion
    return try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destino)}")))
        true
    } catch (_: android.content.ActivityNotFoundException) { false }
}
