package com.example.munch.integracion

import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import com.google.zxing.common.BitMatrix
import java.net.URI

object QrVideo {
    fun generar(url: String, dimension: Int = 640): BitMatrix {
        val uri = URI(url)
        require(uri.scheme == "https" && !uri.host.isNullOrBlank()) { "El QR requiere una URL HTTPS reproducible" }
        return MultiFormatWriter().encode(url, BarcodeFormat.QR_CODE, dimension, dimension)
    }
}
