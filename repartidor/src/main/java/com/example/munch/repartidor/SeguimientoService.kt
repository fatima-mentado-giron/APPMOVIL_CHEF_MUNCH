package com.example.munch.repartidor

import android.Manifest
import android.app.*
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.*
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.munch.integracion.Entregas
import com.example.munch.integracion.PoliticaEntrega
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.Source
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.tasks.await

class SeguimientoService : Service(), LocationListener {
    companion object {
        val mensaje = MutableStateFlow("Seguimiento detenido")
        val pedidoActivo = MutableStateFlow<String?>(null)
        private const val CHANNEL = "munch_entrega"
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private lateinit var manager: LocationManager
    private var escucha: com.google.firebase.firestore.ListenerRegistration? = null
    private var authListener: FirebaseAuth.AuthStateListener? = null
    private var ultimo: Location? = null
    private var ultimoEnvio = 0L
    private var enviando = false
    private var inicio = 0L
    override fun onCreate() {
        super.onCreate()
        manager = getSystemService(LocationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(NotificationChannel(CHANNEL,"Entregas en curso",NotificationManager.IMPORTANCE_LOW))
    }
    private fun notificacion(texto: String): Notification {
        val abrir = PendingIntent.getActivity(this,0,Intent(this,RepartidorMainActivity::class.java),PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        return NotificationCompat.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_menu_mylocation)
            .setContentTitle("Munch · Entrega en curso").setContentText(texto).setContentIntent(abrir).setOngoing(true).build()
    }
    private fun avisar(texto: String) {
        mensaje.value = texto
        getSystemService(NotificationManager::class.java).notify(71,notificacion(texto))
    }
    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val id = intent?.getStringExtra("pedido") ?: run { stopSelf(); return START_NOT_STICKY }
        if (pedidoActivo.value != null) {
            if (pedidoActivo.value != id) mensaje.value = "Finaliza o cancela la entrega activa antes de iniciar otra."
            return START_NOT_STICKY
        }
        val precisa = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val aproximada = ContextCompat.checkSelfPermission(this,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        if (!PoliticaEntrega.permisoUbicacion(precisa,aproximada)) { mensaje.value = "Permiso de ubicación denegado"; stopSelf(); return START_NOT_STICKY }
        try {
            ServiceCompat.startForeground(this,71,notificacion("Comprobando asignación…"),if (Build.VERSION.SDK_INT >= 29) ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION else 0)
        } catch (e: Exception) { mensaje.value = e.localizedMessage ?: "No se pudo iniciar el servicio"; stopSelf(); return START_NOT_STICKY }
        pedidoActivo.value = id
        scope.launch {
            try {
                check(manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) { "GPS desactivado. Actívalo y vuelve a iniciar la entrega." }
                val auth = FirebaseAuth.getInstance()
                val uid = auth.currentUser?.uid ?: error("Inicia sesión")
                check(auth.currentUser!!.getIdToken(false).await().claims["repartidor"] == true) { "Rol de repartidor requerido" }
                val ref = FirebaseFirestore.getInstance().collection("pedidos").document(id)
                val doc = ref.get(Source.SERVER).await()
                check(doc.getString("repartidorUid") == uid) { "La asignación cambió" }
                if (doc.getString("entregaEstado") != "EN_CURSO") Entregas.cambiar(id,"EN_CURSO")
                escucha = ref.addSnapshotListener { snapshot,e ->
                    if (e != null || snapshot?.getString("repartidorUid") != uid || snapshot.getString("entregaEstado") != "EN_CURSO") {
                        mensaje.value = e?.localizedMessage ?: "Entrega terminada, cancelada o reasignada. Seguimiento detenido."
                        stopSelf()
                    }
                }
                authListener = FirebaseAuth.AuthStateListener { if (it.currentUser?.uid != uid) stopSelf() }.also { auth.addAuthStateListener(it) }
                inicio = SystemClock.elapsedRealtime()
                if (precisa) manager.requestLocationUpdates(LocationManager.GPS_PROVIDER,15000L,20f,this@SeguimientoService,Looper.getMainLooper())
                if (manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) manager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,15000L,20f,this@SeguimientoService,Looper.getMainLooper())
                avisar(if (precisa) "Esperando ubicación real del teléfono…" else "Ubicación aproximada: la entrega tendrá menor precisión.")
                scope.launch {
                    while (isActive) {
                        delay(30000)
                        val conPermiso = ContextCompat.checkSelfPermission(this@SeguimientoService,Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(this@SeguimientoService,Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
                        if (!conPermiso) { mensaje.value = "Permiso de ubicación revocado. Seguimiento detenido."; stopSelf(); break }
                        if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) avisar("GPS desactivado: activa la ubicación del teléfono")
                        else if (!conectado()) avisar("Sin conexión: no se envía ubicación hasta recuperar internet")
                        else if (SystemClock.elapsedRealtime() - maxOf(ultimoEnvio,inicio) > 120000) avisar("Sin ubicación reciente: comprueba permisos y señal GPS")
                    }
                }
            } catch (e: Exception) { mensaje.value = e.localizedMessage ?: "No se pudo iniciar el seguimiento"; stopSelf() }
        }
        return START_NOT_STICKY
    }
    private fun conectado(): Boolean {
        val cm = getSystemService(ConnectivityManager::class.java)
        return cm.getNetworkCapabilities(cm.activeNetwork)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
    }
    override fun onLocationChanged(location: Location) {
        val id = pedidoActivo.value ?: return
        val ahora = SystemClock.elapsedRealtime()
        val simulada = if (Build.VERSION.SDK_INT >= 31) location.isMock else @Suppress("DEPRECATION") location.isFromMockProvider
        if (simulada) { avisar("Ubicación simulada detectada; no se publicará como ubicación real."); return }
        if (!PoliticaEntrega.coordenadas(location.latitude,location.longitude) || !location.hasAccuracy() || location.accuracy < 0 || !location.accuracy.isFinite()) return
        if ((SystemClock.elapsedRealtimeNanos() - location.elapsedRealtimeNanos) > 60_000_000_000) return
        if (enviando || ahora - ultimoEnvio < 15000) return
        if (ultimo != null && ultimo!!.distanceTo(location) < 20 && ahora - ultimoEnvio < 60000) return
        if (!conectado()) { avisar("Sin conexión: ubicación pendiente de una nueva lectura al reconectar"); return }
        enviando = true
        scope.launch {
            try {
                val db = FirebaseFirestore.getInstance()
                // Transaction requires online authorization and refuses writes after a terminal transition.
                db.runTransaction { tx ->
                    val ref = db.collection("pedidos").document(id)
                    val pedido = tx.get(ref)
                    check(pedido.getString("repartidorUid") == FirebaseAuth.getInstance().currentUser?.uid && pedido.getString("entregaEstado") == "EN_CURSO") { "Entrega no activa" }
                    tx.set(ref.collection("seguimiento").document("actual"), mapOf(
                        "latitud" to location.latitude, "longitud" to location.longitude, "precision" to location.accuracy.toDouble(),
                        "medidoEn" to com.google.firebase.Timestamp(java.util.Date(location.time)), "actualizadoEn" to FieldValue.serverTimestamp()))
                }.await()
                ultimo = Location(location); ultimoEnvio = ahora
                avisar("Compartiendo ubicación real · precisión ${location.accuracy.toInt()} m")
            } catch (e: Exception) {
                if (e is CancellationException) throw e
                avisar("Error al enviar ubicación: ${e.localizedMessage}. Se reintentará con la próxima lectura.")
            }
            finally { enviando = false }
        }
    }
    override fun onProviderDisabled(provider: String) { avisar("Ubicación desactivada: activa GPS en ajustes") }
    override fun onProviderEnabled(provider: String) { avisar("Ubicación activada; esperando una lectura nueva") }
    @Deprecated("Legacy Android callback") override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
    override fun onDestroy() {
        manager.removeUpdates(this); escucha?.remove()
        authListener?.let { FirebaseAuth.getInstance().removeAuthStateListener(it) }
        scope.cancel(); pedidoActivo.value = null
        stopForeground(STOP_FOREGROUND_REMOVE)
        super.onDestroy()
    }
    override fun onBind(intent: Intent?) = null
}
