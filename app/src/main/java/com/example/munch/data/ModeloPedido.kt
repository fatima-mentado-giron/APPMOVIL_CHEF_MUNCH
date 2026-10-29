package com.example.munch.data

enum class EstadoPedido(val titulo: String, val descripcion: String) {
    PENDIENTE_PAGO(
        titulo = "Pendiente de comprobante",
        descripcion = "Adjunta la captura de tu transferencia."
    ),
    COMPROBANTE_ENVIADO(
        titulo = "⏳ Comprobante enviado",
        descripcion = "Esperando verificación del administrador."
    ),
    PAGO_CONFIRMADO(
        titulo = "✅ Pago verificado",
        descripcion = "¡Tu pedido está confirmado! Listo para preparar."
    ),
    PAGO_RECHAZADO(
        titulo = "❌ Comprobante no verificado",
        descripcion = "Hubo un problema con la transferencia. Intenta de nuevo."
    ),
    EN_PREPARACION(
        titulo = "👨‍🍳 En preparación",
        descripcion = "Estamos empaquetando tus ingredientes en cocina."
    ),
    ENTREGADO_A_REPARTIDOR(
        titulo = "🛵 Entregado a repartidor",
        descripcion = "Tu kit va en camino con el repartidor."
    ),
    ENTREGADO_A_CLIENTE(
        titulo = "📦 Entregado al cliente",
        descripcion = "¡Tu pedido ha sido entregado! Disfruta tu experiencia."
    )
}

data class Pedido(
    val id: String = "",
    val clienteUid: String = "",
    val repartidorUid: String = "",
    val entregaEstado: String = "SIN_ASIGNAR",
    val destinoLat: Double? = null,
    val destinoLng: Double? = null,
    val referencias: String = "",
    val kitNombre: String = "",
    val platillo: String = "",
    val porciones: Int = 2,
    val preferencias: String = "",
    val direccion: String = "",
    val fecha: String = "",
    val horario: String = "",
    val metodoPago: String = "Transferencia bancaria",
    val comprobanteUri: String? = null,
    val estado: EstadoPedido = EstadoPedido.COMPROBANTE_ENVIADO,
    val fechaCreacion: Long = System.currentTimeMillis()
)
