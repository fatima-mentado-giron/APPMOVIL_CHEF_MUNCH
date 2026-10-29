package com.example.munch.integracion

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FieldValue
import kotlinx.coroutines.tasks.await

object PoliticaEntrega {
    fun asignable(estado: String, entrega: String): Boolean = estado in setOf("PAGO_CONFIRMADO", "EN_PREPARACION") && entrega != "EN_CURSO" && entrega != "ENTREGADA"
    fun transicion(actual: String, siguiente: String): Boolean =
        (actual in setOf("ASIGNADA", "CANCELADA") && siguiente == "EN_CURSO") ||
        (actual == "EN_CURSO" && siguiente in setOf("ENTREGADA", "CANCELADA"))
    fun coordenadas(lat: Double, lng: Double) = lat.isFinite() && lng.isFinite() && lat in -90.0..90.0 && lng in -180.0..180.0
    fun permisoUbicacion(precisa: Boolean, aproximada: Boolean) = precisa || aproximada
}

object Entregas {
    suspend fun asignar(id: String, uid: String) {
        val db = FirebaseFirestore.getInstance()
        db.runTransaction { tx ->
            val ref = db.collection("pedidos").document(id)
            val pedido = tx.get(ref)
            val conductor = tx.get(db.collection("repartidores").document(uid))
            check(conductor.getBoolean("activo") == true) { "El repartidor no está registrado o activo" }
            check(PoliticaEntrega.asignable(pedido.getString("estado") ?: "", pedido.getString("entregaEstado") ?: "SIN_ASIGNAR")) { "El pedido debe estar pagado y sin recorrido activo" }
            tx.update(ref, mapOf("repartidorUid" to uid, "entregaEstado" to "ASIGNADA"))
            tx.delete(ref.collection("seguimiento").document("actual"))
        }.await()
    }
    suspend fun cambiar(id: String, siguiente: String) {
        val db = FirebaseFirestore.getInstance()
        val uid = FirebaseAuth.getInstance().currentUser?.uid ?: error("Inicia sesión")
        db.runTransaction { tx ->
            val ref = db.collection("pedidos").document(id)
            val pedido = tx.get(ref)
            check(pedido.getString("repartidorUid") == uid) { "Este pedido no está asignado a tu cuenta" }
            check(PoliticaEntrega.transicion(pedido.getString("entregaEstado") ?: "", siguiente)) { "Transición de entrega no permitida" }
            if (siguiente == "EN_CURSO") check(pedido.getString("estado") in setOf("PAGO_CONFIRMADO", "EN_PREPARACION")) { "El pago o la preparación del pedido no permite iniciar el recorrido" }
            val campos = mutableMapOf<String, Any>("entregaEstado" to siguiente)
            if (siguiente == "EN_CURSO") campos["estado"] = "ENTREGADO_A_REPARTIDOR"
            if (siguiente == "ENTREGADA") campos["estado"] = "ENTREGADO_A_CLIENTE"
            if (siguiente == "CANCELADA") campos["estado"] = "EN_PREPARACION"
            tx.update(ref, campos)
        }.await()
    }
}
