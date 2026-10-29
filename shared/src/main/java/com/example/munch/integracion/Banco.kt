package com.example.munch.integracion
import androidx.compose.runtime.*
import com.example.munch.data.DatosBanco
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.tasks.await

@Composable
fun datosBancarios(): Pair<DatosBanco?, String> {
    var banco by remember { mutableStateOf<DatosBanco?>(null) }
    var error by remember { mutableStateOf("") }
    LaunchedEffect(Unit) {
        try {
            val d = FirebaseFirestore.getInstance().collection("configuracion").document("pagos").get().await()
            val clabe = d.getString("clabe")?.replace(" ", "") ?: ""
            check(d.exists() && clabe.matches(Regex("[0-9]{18}")) && !d.getString("titular").isNullOrBlank() && !d.getString("banco").isNullOrBlank()) { "El negocio aún no ha configurado sus datos bancarios. Puedes elegir Pago al recibir." }
            banco = DatosBanco(d.getString("banco")!!,clabe,d.getString("titular")!!,d.getString("concepto") ?: "Pedido Munch")
        } catch (e: Exception) { error = e.localizedMessage ?: "No se pudieron consultar los datos bancarios" }
    }
    return banco to error
}
