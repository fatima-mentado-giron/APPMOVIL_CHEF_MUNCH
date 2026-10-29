package com.example.munch.admin

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.munch.data.Pedido
import com.example.munch.integracion.*
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.launch

@Composable
fun AdminSecciones() {
    var videos by rememberSaveable { mutableStateOf(false) }
    Column(Modifier.fillMaxSize()) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            FilterChip(selected = !videos, onClick = { videos = false }, label = { Text("Pedidos y pagos") })
            FilterChip(selected = videos, onClick = { videos = true }, label = { Text("Videos de cocina") })
        }
        if (videos) Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) { AdminVideos() }
        else Box(Modifier.weight(1f)) { AdminMunchApp() }
    }
}

@Composable
fun AsignacionPedido(pedido: Pedido) {
    var conductores by remember { mutableStateOf(emptyList<Pair<String,String>>()) }
    var error by remember { mutableStateOf("") }
    var seleccionado by remember(pedido.id, pedido.repartidorUid) { mutableStateOf(pedido.repartidorUid) }
    var ocupado by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    DisposableEffect(Unit) {
        val listener = FirebaseFirestore.getInstance().collection("repartidores").whereEqualTo("activo",true).addSnapshotListener { snap,e ->
            error = e?.localizedMessage ?: ""
            conductores = snap?.documents?.map { it.id to (it.getString("nombre") ?: it.id) } ?: emptyList()
        }
        onDispose { listener.remove() }
    }
    Text("Repartidor: ${conductores.find { it.first == pedido.repartidorUid }?.second ?: pedido.repartidorUid.ifBlank { "Sin asignar" }}")
    Text("Recorrido: ${pedido.entregaEstado}")
    if (PoliticaEntrega.asignable(pedido.estado.name,pedido.entregaEstado)) {
        if (conductores.isEmpty()) Text("No hay repartidores activos registrados. Configura su cuenta y rol desde el entorno de administración de Firebase.")
        conductores.forEach { (uid,nombre) ->
            FilterChip(selected = seleccionado == uid, onClick = { seleccionado = uid }, label = { Text(nombre) }, enabled = !ocupado)
        }
        Button(onClick = {
            ocupado = true; error = ""
            scope.launch {
                try { Entregas.asignar(pedido.id,seleccionado) }
                catch (e: Exception) { error = e.localizedMessage ?: "Error al asignar" }
                ocupado = false
            }
        }, enabled = seleccionado.isNotBlank() && !ocupado && conductores.any { it.first == seleccionado }) { Text("Asignar pedido") }
    }
    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
}
