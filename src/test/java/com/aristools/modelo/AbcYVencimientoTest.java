package com.aristools.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Reglas de la sesión 5 que no necesitan base de datos: clasificación ABC,
 * reparto FEFO del stock entre lotes, estado de un lote y exactitud del conteo.
 */
class AbcYVencimientoTest {

    private static Producto producto(String codigo) {
        Producto p = new Producto();
        p.setCodigo(codigo);
        p.setNombre("Producto " + codigo);
        return p;
    }

    @Test
    void laClasificacionAbcSigueElPrincipioDePareto() {
        // Valor consumido: 800.000 + 150.000 + 50.000 = 1.000.000; el cuarto no tuvo salidas
        List<ClasificacionAbc> abc = ClasificacionAbc.clasificar(List.of(
                new ClasificacionAbc(producto("P3"), 5, new BigDecimal("50000")),
                new ClasificacionAbc(producto("P1"), 40, new BigDecimal("800000")),
                new ClasificacionAbc(producto("P4"), 0, BigDecimal.ZERO),
                new ClasificacionAbc(producto("P2"), 10, new BigDecimal("150000"))));
        assertEquals("P1", abc.get(0).getProducto().getCodigo(), "Queda ordenado de mayor a menor valor");
        assertEquals('A', abc.get(0).getClase(), "El 80 % del valor es clase A");
        assertEquals(0, new BigDecimal("80.00").compareTo(abc.get(0).getPorcentaje()));
        assertEquals('B', abc.get(1).getClase(), "Del 80 % al 95 % es clase B");
        assertEquals(0, new BigDecimal("95.00").compareTo(abc.get(1).getAcumulado()));
        assertEquals('C', abc.get(2).getClase(), "El resto es clase C");
        assertEquals('C', abc.get(3).getClase(), "Sin salidas siempre es clase C");
    }

    @Test
    void fefoDejaEnBodegaLosLotesQueVencenMasTarde() {
        Producto pintura = producto("PIN");
        LoteVencimiento enero = new LoteVencimiento(pintura, "L-ENE", LocalDate.of(2027, 1, 31), 10, null, null);
        LoteVencimiento marzo = new LoteVencimiento(pintura, "L-MAR", LocalDate.of(2027, 3, 31), 10, null, null);
        // Entraron 20 y quedan 12: salieron 8, y por FEFO salieron del lote de enero
        List<LoteVencimiento> lotes = LoteVencimiento.asignarSaldos(List.of(marzo, enero), 12);
        assertEquals("L-ENE", lotes.get(0).getLote(), "Primero el que vence primero: es el que debe salir");
        assertEquals(2, lotes.get(0).getSaldoEstimado());
        assertEquals(10, lotes.get(1).getSaldoEstimado());
    }

    @Test
    void elEstadoDelLoteDependeDeLosDiasDeAviso() {
        LocalDate hoy = LocalDate.of(2026, 11, 15);
        Producto p = producto("PIN");
        assertEquals(LoteVencimiento.VENCIDO,
                new LoteVencimiento(p, "L1", hoy.minusDays(1), 5, null, null).estado(hoy, 30));
        assertEquals(LoteVencimiento.POR_VENCER,
                new LoteVencimiento(p, "L2", hoy.plusDays(30), 5, null, null).estado(hoy, 30));
        assertEquals(LoteVencimiento.VIGENTE,
                new LoteVencimiento(p, "L3", hoy.plusDays(31), 5, null, null).estado(hoy, 30));
        assertEquals(-1, new LoteVencimiento(p, "L1", hoy.minusDays(1), 5, null, null).diasParaVencer(hoy));
    }

    @Test
    void laCompraDeUnProductoQueVenceExigeLoteYFechaFutura() {
        Producto pintura = producto("PIN");
        pintura.setControlaVencimiento(true);
        Bodega bodega = new Bodega(1, "BOD-01", "Principal", null, EstadoRegistro.ACTIVO);
        Proveedor proveedor = new Proveedor();
        proveedor.setEstado(EstadoRegistro.ACTIVO);
        TipoMovimiento compra = new TipoMovimiento(1, "Compra", Naturaleza.ENTRADA, true, null);
        Entrada e = new Entrada(pintura, compra, new Usuario(), 10, bodega, proveedor);
        e.setDocumentoSoporte("FAC-0001");
        e.setCostoUnitario(new BigDecimal("48000"));
        assertThrows(NegocioException.class, e::validar, "Sin lote ni fecha no se acepta");
        e.setLote("L-2291");
        e.setFechaVencimiento(LocalDate.now().minusDays(1));
        assertThrows(NegocioException.class, e::validar, "No se recibe mercancía vencida");
        e.setFechaVencimiento(LocalDate.now().plusMonths(6));
        e.validar();
    }

    @Test
    void laExactitudDelConteoEsElPorcentajeQueCoincidio() {
        Conteo c = new Conteo(1, null, "almacen", java.time.LocalDateTime.now(), 'A', true, 20, 2);
        assertEquals(90.0, c.exactitud(), 0.001);
        assertEquals("Cíclico (clase A), a ciegas", c.tipo());
        assertEquals("General", new Conteo(2, null, "almacen", java.time.LocalDateTime.now(), null, false, 4, 0).tipo());
    }
}
