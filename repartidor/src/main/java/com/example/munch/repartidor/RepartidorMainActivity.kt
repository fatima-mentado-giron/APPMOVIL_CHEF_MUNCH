package com.example.munch.repartidor

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.munch.integracion.*
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

class RepartidorMainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFFD95740), secondary = Color(0xFF486039), background = Color(0xFFFFF8ED))) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    Sesion("repartidor", alSalir = { stopService(Intent(this,SeguimientoService::class.java)) }) { EntregasAsignadas() }
                }
            }
        }
    }
}
private data class Asignado(val id: String, val direccion: String, val referencias: String, val kit: String, val fecha: String, val horario: String, val pago: String, val estado: String, val lat: Double?, val lng: Double?)

@Composable
private fun EntregasAsignadas() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var lista by remember { mutableStateOf(emptyList<Asignado>()) }
    var error by remember { mutableStateOf("") }
    var cargando by remember { mutableStateOf(true) }
    var ocupado by remember { mutableStateOf(false) }
    var pendiente by remember { mutableStateOf<String?>(null) }
    var intento by remember { mutableIntStateOf(0) }
    val mensaje by SeguimientoService.mensaje.collectAsState()
    val activo by SeguimientoService.pedidoActivo.collectAsState()
    fun iniciar(id: String) {
        try {
            if (!context.getSystemService(LocationManager::class.java).isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                error = "GPS desactivado. Actívalo y pulsa Iniciar entrega de nuevo."
                return
            }
            ContextCompat.startForegroundService(context,Intent(context,SeguimientoService::class.java).putExtra("pedido",id))
            error = ""
        } catch (e: Exception) { error = e.localizedMessage ?: "No se pudo iniciar el servicio GPS" }
    }
    val permisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { _ ->
        val fina = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val gruesa = ContextCompat.checkSelfPermission(context,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (PoliticaEntrega.permisoUbicacion(fina,gruesa)) pendiente?.let { iniciar(it) }
        else error = "Permiso denegado. Concede ubicación en Ajustes para iniciar el recorrido."
        pendiente = null
    }
    DisposableEffect(intento) {
        cargando = true
        val uid = FirebaseAuth.getInstance().currentUser!!.uid
        val listener = FirebaseFirestore.getInstance().collection("pedidos").whereEqualTo("repartidorUid",uid).addSnapshotListener { snapshot,e ->
            cargando = false; error = e?.localizedMessage ?: ""
            lista = snapshot?.documents?.map { d -> Asignado(d.id,d.getString("direccion") ?: "", d.getString("referencias") ?: "",d.getString("kitNombre") ?: "",d.getString("fecha") ?: "",d.getString("horario") ?: "",d.getString("metodoPago") ?: "",d.getString("entregaEstado") ?: "ASIGNADA",d.getDouble("destinoLat"),d.getDouble("destinoLng")) } ?: emptyList()
        }
        onDispose { listener.remove() }
    }
    LazyColumn(Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Mis entregas", style = MaterialTheme.typography.headlineMedium)
            Text(mensaje)
            if (cargando) CircularProgressIndicator()
            if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { intento++ }) { Text("Actualizar pedidos") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)) }) { Text("Ajustes de ubicación") }
            TextButton(onClick = { context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS,android.net.Uri.parse("package:${context.packageName}"))) }) { Text("Ajustes de permisos y notificaciones") }
            if (!cargando && lista.isEmpty()) Text("No tienes pedidos asignados.")
        }
        items(lista, key = { it.id }) { pedido ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("${pedido.kit} · ${pedido.id}")
                    Text(pedido.direccion); Text("Referencias: ${pedido.referencias.ifBlank { "Sin referencias" }}")
                    Text("${pedido.fecha} · ${pedido.horario}"); Text("Pago: ${pedido.pago}"); Text("Entrega: ${pedido.estado}")
                    TextButton(onClick = { if (!abrirDestino(context,pedido.direccion,pedido.lat,pedido.lng)) error = "No hay una aplicación de mapas disponible" }) { Text("Abrir destino en mapas") }
                    if (pedido.estado in setOf("ASIGNADA","CANCELADA","EN_CURSO") && activo == null) {
                        Button(onClick = {
                            pendiente = pedido.id
                            val requests = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION,Manifest.permission.ACCESS_COARSE_LOCATION)
                            if (Build.VERSION.SDK_INT >= 33) requests += Manifest.permission.POST_NOTIFICATIONS
                            permisos.launch(requests.toTypedArray())
                        }) { Text(if (pedido.estado == "EN_CURSO") "Reanudar seguimiento" else "Iniciar entrega") }
                    }
                    if (pedido.estado == "EN_CURSO") {
                        fun terminar(siguiente: String) {
                            if (activo == pedido.id) context.stopService(Intent(context,SeguimientoService::class.java))
                            ocupado = true
                            scope.launch {
                                try { Entregas.cambiar(pedido.id,siguiente); SeguimientoService.mensaje.value = "Seguimiento detenido · $siguiente" }
                                catch (e: Exception) { error = "Seguimiento detenido. ${e.localizedMessage}. Reintenta finalizar o reanuda el seguimiento." }
                                ocupado = false
                            }
                        }
                        Button(onClick = { terminar("ENTREGADA") }, enabled = !ocupado) { Text("Finalizar entrega") }
                        OutlinedButton(onClick = { terminar("CANCELADA") }, enabled = !ocupado) { Text("Cancelar recorrido") }
                    }
                }
            }
        }
    }
}
