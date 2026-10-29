package com.example.munch.integracion
import org.junit.Assert.*
import org.junit.Test
class PoliticaEntregaTest {
    @Test fun soloPedidosPagadosSinRutaActivaSeAsignan() {
        assertFalse(PoliticaEntrega.asignable("COMPROBANTE_ENVIADO", "SIN_ASIGNAR"))
        assertTrue(PoliticaEntrega.asignable("EN_PREPARACION", "SIN_ASIGNAR"))
        assertFalse(PoliticaEntrega.asignable("PAGO_CONFIRMADO", "EN_CURSO"))
    }
    @Test fun entregaExigeInicioYNoPuedeReabrirse() {
        assertFalse(PoliticaEntrega.transicion("ASIGNADA", "ENTREGADA"))
        assertTrue(PoliticaEntrega.transicion("ASIGNADA", "EN_CURSO"))
        assertTrue(PoliticaEntrega.transicion("EN_CURSO", "ENTREGADA"))
        assertTrue(PoliticaEntrega.transicion("EN_CURSO", "CANCELADA"))
        assertTrue(PoliticaEntrega.transicion("CANCELADA", "EN_CURSO"))
        assertFalse(PoliticaEntrega.transicion("ENTREGADA", "EN_CURSO"))
    }
    @Test fun permisosYCoordenadas() {
        assertFalse(PoliticaEntrega.permisoUbicacion(false,false))
        assertTrue(PoliticaEntrega.permisoUbicacion(false,true))
        assertTrue(PoliticaEntrega.permisoUbicacion(true,true))
        assertFalse(PoliticaEntrega.coordenadas(Double.NaN,0.0))
        assertFalse(PoliticaEntrega.coordenadas(91.0,0.0))
        assertTrue(PoliticaEntrega.coordenadas(19.4,-99.1))
    }
}
