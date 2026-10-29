package com.example.munch.integracion

import android.app.Application
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import com.google.firebase.storage.FirebaseStorage
import com.google.firebase.storage.StorageMetadata
import com.google.firebase.storage.UploadTask
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.UUID

const val MAX_VIDEO_BYTES = 1024L * 1024 * 1024 // 1 GiB; no duration limit.
val kitsVideo = listOf("Date Night", "Family", "Friends")
data class Video(val id: String, val titulo: String, val kit: String, val url: String, val publicado: Boolean)

@Composable
fun listaVideos(kit: String? = null, soloPublicados: Boolean): Triple<List<Video>, Boolean, String> {
    var lista by remember(kit, soloPublicados) { mutableStateOf(emptyList<Video>()) }
    var cargando by remember(kit, soloPublicados) { mutableStateOf(true) }
    var error by remember(kit, soloPublicados) { mutableStateOf("") }
    var intento by remember { mutableIntStateOf(0) }
    DisposableEffect(kit, soloPublicados, intento) {
        cargando = true; error = ""
        var query: com.google.firebase.firestore.Query = FirebaseFirestore.getInstance().collection("videos")
        if (kit != null) query = query.whereEqualTo("kit", kit)
        if (soloPublicados) query = query.whereEqualTo("publicado", true)
        val listener = query.addSnapshotListener { snapshot, e ->
            cargando = false
            error = e?.localizedMessage ?: ""
            lista = snapshot?.documents?.map { d -> Video(d.id, d.getString("titulo") ?: "Video", d.getString("kit") ?: "", d.getString("url") ?: "", d.getBoolean("publicado") == true) }?.sortedBy { it.titulo } ?: emptyList()
        }
        onDispose { listener.remove() }
    }
    if (error.isNotEmpty()) {
        Text(error, color = MaterialTheme.colorScheme.error)
        TextButton(onClick = { intento++ }) { Text("Reintentar consulta") }
    }
    return Triple(lista, cargando, error)
}

class SubidaVideo(app: Application) : AndroidViewModel(app) {
    var progreso by mutableFloatStateOf(0f); private set
    var ocupado by mutableStateOf(false); private set
    var mensaje by mutableStateOf(""); private set
    var reintentable by mutableStateOf(false); private set
    var cancelable by mutableStateOf(false); private set
    private var tarea: UploadTask? = null
    private var trabajo: kotlinx.coroutines.Job? = null
    private var cancelado = false
    private var archivo: Uri? = null
    private var titulo = ""
    private var kit = ""
    private var reemplazo: String? = null
    fun subir(uri: Uri, nombre: String, categoria: String, id: String? = null) {
        if (ocupado) return
        archivo = uri; titulo = nombre.trim(); kit = categoria; reemplazo = id
        ocupado = true; cancelado = false; cancelable = true; reintentable = false; progreso = 0f; mensaje = "Validando archivo…"
        trabajo = viewModelScope.launch {
            var ref: com.google.firebase.storage.StorageReference? = null
            var publicado = false
            var confirmacionIniciada = false
            try {
                require(titulo.isNotBlank() && titulo.length <= 200 && kit in kitsVideo) { "Escribe un título de hasta 200 caracteres y elige un kit" }
                val resolver = getApplication<Application>().contentResolver
                val size = withContext(Dispatchers.IO) {
                    resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                        if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else -1L
                    } ?: -1L
                }
                require(size in 1..MAX_VIDEO_BYTES) { "Selecciona un archivo de tamaño conocido, de hasta 1 GiB. Copia a almacenamiento local si tu proveedor no indica el tamaño." }
                val mime = resolver.getType(uri) ?: ""
                require(mime.startsWith("video/")) { "El archivo seleccionado no es un video" }
                val db = FirebaseFirestore.getInstance()
                val doc = db.collection("videos").document(id ?: UUID.randomUUID().toString())
                // A unique object keeps the currently published version working until replacement commits.
                ref = FirebaseStorage.getInstance().reference.child("videos/${doc.id}/${UUID.randomUUID()}")
                mensaje = "Subiendo…"
                val metadata = StorageMetadata.Builder().setContentType(mime).build()
                tarea = ref.putFile(uri, metadata)
                tarea!!.addOnProgressListener { snap -> progreso = (snap.bytesTransferred.toDouble() / snap.totalByteCount.coerceAtLeast(1)).toFloat() }
                tarea!!.await()
                check(!cancelado) { "Subida cancelada" }
                val url = ref.downloadUrl.await().toString()
                require(Uri.parse(url).scheme == "https") { "Storage no devolvió un enlace HTTPS" }
                check(!cancelado) { "Subida cancelada" }
                cancelable = false
                mensaje = "Publicando metadatos…"
                confirmacionIniciada = true
                doc.set(mapOf("titulo" to titulo, "kit" to kit, "url" to url, "storagePath" to ref.path,
                    "publicado" to true, "bytes" to size, "actualizadoEn" to FieldValue.serverTimestamp())).await()
                publicado = true
                mensaje = "Video publicado correctamente"
            } catch (e: Exception) {
                reintentable = true
                mensaje = if (cancelado) "Subida cancelada" else e.localizedMessage ?: "Error al subir el video"
            } finally {
                // Clean up only this unsuccessful upload, never an existing published version.
                // A canceled Firestore await can still commit remotely. Keep the object
                // once publication starts, so process/activity destruction cannot break it.
                if (!publicado && !confirmacionIniciada) ref?.delete()
                ocupado = false; cancelable = false; tarea = null
            }
        }
    }
    fun cancelar() { if (!cancelable) return; cancelado = true; tarea?.cancel(); mensaje = "Cancelando subida…" }
    fun reintentar() { archivo?.let { subir(it, titulo, kit, reemplazo) } }
    override fun onCleared() { tarea?.cancel(); super.onCleared() }
}

@Composable
fun AdminVideos() {
    val context = LocalContext.current
    val vm: SubidaVideo = viewModel()
    var titulo by rememberSaveable { mutableStateOf("") }
    var kit by rememberSaveable { mutableStateOf(kitsVideo.first()) }
    var archivo by rememberSaveable { mutableStateOf<String?>(null) }
    var reemplazo by rememberSaveable { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val selector = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                archivo = uri.toString(); error = ""
            } catch (e: Exception) { error = "No se pudo conservar acceso al archivo: ${e.localizedMessage}" }
        }
    }
    Text("Videos de cocina", style = MaterialTheme.typography.headlineSmall)
    Text("Hasta 1 GiB por video, sin límite de duración. PASTA.mp4 → Date Night; PIZZA.mp4 → Family; TACOS.mp4 → Friends.")
    OutlinedTextField(titulo, { titulo = it }, label = { Text("Título") }, enabled = !vm.ocupado)
    kitsVideo.forEach { nombre -> FilterChip(selected = kit == nombre, onClick = { if (!vm.ocupado) kit = nombre }, label = { Text(nombre) }) }
    Button(onClick = { selector.launch(arrayOf("video/*")) }, enabled = !vm.ocupado) { Text("Seleccionar video") }
    if (archivo != null) Text("Archivo seleccionado")
    if (reemplazo != null) {
        Text("Reemplazando: $reemplazo")
        TextButton(onClick = { reemplazo = null }, enabled = !vm.ocupado) { Text("Crear otro video") }
    }
    Button(onClick = { archivo?.let { vm.subir(Uri.parse(it), titulo, kit, reemplazo) } }, enabled = archivo != null && titulo.isNotBlank() && !vm.ocupado) { Text("Subir y publicar") }
    if (vm.ocupado) {
        LinearProgressIndicator(progress = { vm.progreso }, modifier = Modifier.fillMaxWidth())
        Text("${(vm.progreso * 100).toInt()} %")
        TextButton(onClick = vm::cancelar, enabled = vm.cancelable) { Text("Cancelar subida") }
    } else if (vm.reintentable) TextButton(onClick = vm::reintentar) { Text("Reintentar subida") }
    Text(vm.mensaje)
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
    val (videos, cargando, _) = listaVideos(soloPublicados = false)
    if (cargando) CircularProgressIndicator()
    if (!cargando && videos.isEmpty()) Text("Aún no hay videos. Selecciona un archivo para publicarlo.")
    videos.forEach { video ->
        HorizontalDivider()
        Text("${video.titulo} · ${video.kit} · ${if (video.publicado) "Publicado" else "Sin publicar"}")
        TextButton(onClick = { reemplazo = video.id; titulo = video.titulo; kit = video.kit; archivo = null }, enabled = !vm.ocupado) { Text("Reemplazar archivo") }
        TextButton(onClick = {
            scope.launch {
                try { FirebaseFirestore.getInstance().collection("videos").document(video.id).update("publicado", !video.publicado).await() }
                catch (e: Exception) { error = e.localizedMessage ?: "Error al cambiar la publicación" }
            }
        }) { Text(if (video.publicado) "Dejar de publicar" else "Publicar") }
        if (video.publicado) VideoReproducible(video)
    }
}

@Composable
fun VideosKit(kit: String) {
    Text("Videos del kit", style = MaterialTheme.typography.titleLarge)
    val (videos, cargando, error) = listaVideos(kit, true)
    if (cargando) CircularProgressIndicator()
    if (!cargando && error.isBlank() && videos.isEmpty()) Text("Todavía no hay videos publicados para este kit.")
    videos.forEach { VideoReproducible(it) }
}

@Composable
fun VideoReproducible(video: Video) {
    val context = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val url = remember(video.url) { Uri.parse(video.url) }
    Text(video.titulo, style = MaterialTheme.typography.titleMedium)
    if (url.scheme != "https" || url.host.isNullOrBlank()) { Text("Este video no tiene una URL HTTPS válida."); return }
    var mostrar by remember(video.url) { mutableStateOf(false) }
    TextButton(onClick = { mostrar = !mostrar }) { Text(if (mostrar) "Cerrar reproductor" else "Reproducir aquí") }
    if (mostrar) {
        var cargando by remember { mutableStateOf(true) }
        var error by remember { mutableStateOf("") }
        val player = remember(video.url) { ExoPlayer.Builder(context).build().apply { setMediaItem(MediaItem.fromUri(url)); prepare() } }
        DisposableEffect(player, owner) {
            cargando = player.playbackState == Player.STATE_BUFFERING || player.playbackState == Player.STATE_IDLE
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(state: Int) { cargando = state == Player.STATE_BUFFERING || state == Player.STATE_IDLE }
                override fun onPlayerError(e: PlaybackException) { error = e.localizedMessage ?: "Error al reproducir"; cargando = false }
            }
            val observer = LifecycleEventObserver { _, event -> if (event == Lifecycle.Event.ON_STOP || event == Lifecycle.Event.ON_PAUSE) player.pause() }
            player.addListener(listener); owner.lifecycle.addObserver(observer)
            onDispose { owner.lifecycle.removeObserver(observer); player.removeListener(listener); player.release() }
        }
        AndroidView(factory = { PlayerView(it).apply { this.player = player; useController = true } }, update = { it.player = player }, modifier = Modifier.fillMaxWidth().height(230.dp))
        if (cargando && error.isBlank()) CircularProgressIndicator()
        if (error.isNotBlank()) {
            Text(error, color = MaterialTheme.colorScheme.error)
            TextButton(onClick = { error = ""; player.prepare() }) { Text("Reintentar reproducción") }
        }
    }
    var qr by remember(video.url) { mutableStateOf<Bitmap?>(null) }
    var errorQr by remember(video.url) { mutableStateOf("") }
    LaunchedEffect(video.url) {
        try { qr = withContext(Dispatchers.Default) {
            val matrix = QrVideo.generar(video.url)
            Bitmap.createBitmap(640,640,Bitmap.Config.RGB_565).apply {
                val pixels = IntArray(640*640) { i -> if (matrix[i % 640,i / 640]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
                setPixels(pixels,0,640,0,0,640,640)
            }
        } } catch (e: Exception) { errorQr = e.localizedMessage ?: "No se pudo generar QR" }
    }
    qr?.let { Image(it.asImageBitmap(), "QR HTTPS de ${video.titulo}", Modifier.size(240.dp)) }
    if (errorQr.isNotBlank()) Text(errorQr)
    var errorAbrir by remember { mutableStateOf("") }
    TextButton(onClick = {
        try { context.startActivity(Intent(Intent.ACTION_VIEW,url)) }
        catch (_: android.content.ActivityNotFoundException) { errorAbrir = "No hay una aplicación disponible para abrir el enlace." }
    }) { Text("Abrir enlace en este teléfono") }
    if (errorAbrir.isNotBlank()) Text(errorAbrir)
    Text("Quien tenga el enlace del QR podrá acceder al video. Dejar de publicarlo lo oculta en Munch, pero no revoca enlaces ya compartidos.")
}
