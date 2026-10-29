package com.example.munch.integracion

import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import org.junit.Assert.*
import org.junit.Test

class QrVideoTest {
    @Test fun qrConservaEnlaceHttpsCompletoIncluidoToken() {
        val url = "https://firebasestorage.googleapis.com/v0/b/munch/o/videos%2Fkit%2Fpasta.mp4?alt=media&token=compartible"
        val matrix = QrVideo.generar(url)
        val pixels = IntArray(matrix.width*matrix.height) { i -> if (matrix[i%matrix.width,i/matrix.width]) 0xff000000.toInt() else 0xffffffff.toInt() }
        val decoded = MultiFormatReader().decode(BinaryBitmap(HybridBinarizer(RGBLuminanceSource(matrix.width,matrix.height,pixels))))
        assertEquals(url,decoded.text)
    }
    @Test fun rechazaRutasLocalesYEnlacesNoHttps() {
        for (url in listOf("file:///sdcard/PASTA.mp4","content://videos/1","/sdcard/PASTA.mp4","http://example.com/video.mp4")) {
            try { QrVideo.generar(url); fail("Debió rechazar $url") }
            catch (_: IllegalArgumentException) { }
        }
    }
}
