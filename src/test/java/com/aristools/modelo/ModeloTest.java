package com.aristools.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas unitarias de las reglas del modelo (sección 4.4).
 * No usan la base de datos: prueban la herencia, el polimorfismo y las
 * validaciones de las clases.
 */
class ModeloTest {

    // ----- datos de prueba -----
    private final Rol almacenista = new Rol(2, "Almacenista", "");
    private final Rol supervisor = new Rol(4, "Supervisor de Bodega", "");
    private final Usuario registra = usuario(2, "almacen", almacenista);
    private final Usuario autoriza = usuario(4, "supervisor", supervisor);
    private final Producto martillo = new Producto(1, "HER-001", "Martillo", "", null, "Unidad", null, 5, 50, EstadoRegistro.ACTIVO);
    private final Bodega principal = new Bodega(1, "BOD-01", "Principal", "", EstadoRegistro.ACTIVO);
    private final Bodega exhibicion = new Bodega(2, "BOD-02", "Exhibición", "", EstadoRegistro.ACTIVO);

    private static Usuario usuario(int id, String nombre, Rol rol) {
        Usuario u = new Usuario();
        u.setIdUsuario(id);
        u.setNombreUsuario(nombre);
        u.setRol(rol);
        return u;
    }

    private TipoMovimiento tipo(String nombre, Naturaleza n, boolean autoriza, Rol rol) {
        return new TipoMovimiento(1, nombre, n, autoriza, rol);
    }

    // ----- herencia y polimorfismo -----

    @Test
    void transferenciaRestaEnOrigenYSumaEnDestinoSinCambiarElTotal() {
        Movimiento m = new Transferencia(martillo, tipo("Transferencia", Naturaleza.TRANSFERENCIA, false, null),
                registra, 8, principal, exhibicion);
        List<CambioStock> cambios = m.calcularCambiosStock();
        assertEquals(2, cambios.size());
        assertEquals(-8, cambios.get(0).getDelta());
        assertEquals(principal, cambios.get(0).getBodega());
        assertEquals(8, cambios.get(1).getDelta());
        assertEquals(0, cambios.stream().mapToInt(CambioStock::getDelta).sum(), "El total no debe cambiar");
    }

    @Test
    void cadaSubclaseCalculaSuPropioCambio() {
        Movimiento entrada = new Entrada(martillo, tipo("Devolución", Naturaleza.ENTRADA, true, null), registra, 5, principal, null);
        Movimiento salida = new Salida(martillo, tipo("Consumo interno", Naturaleza.SALIDA, false, null), registra, 3, principal, null);
        Movimiento ajusteNeg = new Ajuste(martillo, tipo("Ajuste negativo", Naturaleza.SALIDA, true, supervisor), registra, 2, principal, false);
        assertEquals(5, entrada.calcularCambiosStock().get(0).getDelta());
        assertEquals(-3, salida.calcularCambiosStock().get(0).getDelta());
        assertEquals(-2, ajusteNeg.calcularCambiosStock().get(0).getDelta());
    }

    // ----- validaciones -----

    @Test
    void transferenciaALaMismaBodegaSeRechaza() {
        Movimiento m = new Transferencia(martillo, tipo("Transferencia", Naturaleza.TRANSFERENCIA, false, null),
                registra, 8, principal, principal);
        NegocioException e = assertThrows(NegocioException.class, m::validar);
        assertEquals("El origen debe ser distinto del destino.", e.getMessage());
    }

    @Test
    void cantidadCeroONegativaSeRechaza() {
        Movimiento m = new Salida(martillo, tipo("Consumo interno", Naturaleza.SALIDA, false, null), registra, 0, principal, null);
        assertThrows(NegocioException.class, m::validar);
    }

    @Test
    void mermaSinMotivoSeRechaza() {
        Salida s = new Salida(martillo, tipo("Merma", Naturaleza.SALIDA, true, supervisor), registra, 2, principal, null);
        assertThrows(NegocioException.class, s::validar);
        s.setMotivo("Envases golpeados");
        NegocioException e = assertThrows(NegocioException.class, s::validar);
        assertEquals("Seleccione la causa de la baja (vencido, roto, humedad, etc.).", e.getMessage());
        s.setCausaBaja(CausaBaja.ROTO_O_GOLPEADO);
        s.validar();   // con motivo y causa ya es válida
    }

    // ----- averías, cuarentena y documentos de soporte -----

    @Test
    void laBodegaDeCuarentenaNoRecibeComprasNiEntregaConsumoInterno() {
        Bodega cuarentena = new Bodega(9, "BOD-CUA", "Cuarentena", "", EstadoRegistro.ACTIVO);
        cuarentena.setCuarentena(true);
        Proveedor proveedor = new Proveedor(1, "900123456-1", "Distribuidora Ferretera del Valle S.A.S.",
                "", "", "", "", EstadoRegistro.ACTIVO);
        Entrada compra = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, cuarentena, proveedor);
        compra.setDocumentoSoporte("FAC-1");
        compra.setCostoUnitario(new BigDecimal("24000"));
        assertThrows(NegocioException.class, compra::validar, "No se compra directo a cuarentena");

        Salida consumo = new Salida(martillo, tipo("Consumo interno", Naturaleza.SALIDA, false, null), registra, 1, cuarentena, null);
        assertThrows(NegocioException.class, consumo::validar, "Lo averiado no se usa");

        // Sí se puede trasladar a cuarentena y darlo de baja desde allí
        new Transferencia(martillo, tipo("Transferencia", Naturaleza.TRANSFERENCIA, false, null), registra, 2, principal, cuarentena).validar();
        Salida baja = new Salida(martillo, tipo("Producto dañado o vencido", Naturaleza.SALIDA, true, supervisor), registra, 2, cuarentena, null);
        baja.setMotivo("Mango partido");
        baja.setCausaBaja(CausaBaja.ROTO_O_GOLPEADO);
        baja.validar();
    }

    @Test
    void cadaMovimientoTieneSuDocumentoDeSoporte() {
        Movimiento entrada = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 5, principal, null);
        Movimiento vale = new Salida(martillo, tipo("Consumo interno", Naturaleza.SALIDA, false, null), registra, 3, principal, null);
        Movimiento baja = new Salida(martillo, tipo("Merma", Naturaleza.SALIDA, true, supervisor), registra, 2, principal, null);
        Movimiento traslado = new Transferencia(martillo, tipo("Transferencia", Naturaleza.TRANSFERENCIA, false, null),
                registra, 8, principal, exhibicion);
        Movimiento faltante = new Ajuste(martillo, tipo("Ajuste negativo", Naturaleza.SALIDA, true, supervisor), registra, 1, principal, false);
        baja.setIdMovimiento(57);
        assertEquals("BAJ-000057", baja.numeroDocumento());
        assertEquals("Acta de baja", baja.tituloDocumento());
        assertEquals(List.of("ENT", "VAL", "BAJ", "TRF", "CNT"), List.of(entrada.prefijoDocumento(),
                vale.prefijoDocumento(), baja.prefijoDocumento(), traslado.prefijoDocumento(), faltante.prefijoDocumento()));
        // Son pérdidas la merma y el faltante del conteo; las demás no
        assertTrue(baja.esPerdida());
        assertTrue(faltante.esPerdida());
        assertFalse(entrada.esPerdida() || vale.esPerdida() || traslado.esPerdida());
    }

    @Test
    void lasPerdidasSeTotalizanPorCausaDeMayorAMenorValor() {
        TipoMovimiento danado = tipo("Producto dañado o vencido", Naturaleza.SALIDA, true, supervisor);
        Movimiento a = perdida(danado, 2, "20000", CausaBaja.HUMEDAD);     //  40.000
        Movimiento b = perdida(danado, 5, "30000", CausaBaja.VENCIDO);     // 150.000
        Movimiento c = perdida(danado, 1, "10000", CausaBaja.HUMEDAD);     //  10.000
        assertEquals(0, new BigDecimal("40000").compareTo(a.valorTotal()));
        List<TotalPerdida> totales = TotalPerdida.agrupar(List.of(a, b, c), m -> m.getCausaBaja().getDescripcion());
        assertEquals(2, totales.size());
        assertEquals("Vencido", totales.get(0).getNombre());
        assertEquals(0, new BigDecimal("150000").compareTo(totales.get(0).getValor()));
        assertEquals("Humedad", totales.get(1).getNombre());
        assertEquals(2, totales.get(1).getBajas());
        assertEquals(3, totales.get(1).getUnidades());
        assertEquals(0, new BigDecimal("50000").compareTo(totales.get(1).getValor()));
    }

    private Movimiento perdida(TipoMovimiento tipo, int cantidad, String costo, CausaBaja causa) {
        Salida s = new Salida(martillo, tipo, registra, cantidad, principal, null);
        s.setCostoUnitario(new BigDecimal(costo));
        s.setCausaBaja(causa);
        return s;
    }

    @Test
    void compraSinProveedorSeRechaza() {
        Entrada e = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, principal, null);
        assertThrows(NegocioException.class, e::validar);
    }

    @Test
    void compraSinDocumentoSoporteSeRechaza() {
        Proveedor proveedor = new Proveedor(1, "900123456-1", "Distribuidora Ferretera del Valle S.A.S.",
                "", "", "", "", EstadoRegistro.ACTIVO);
        Entrada e = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, principal, proveedor);
        NegocioException ex = assertThrows(NegocioException.class, e::validar);
        assertEquals("Una compra exige el documento de soporte (factura o remisión).", ex.getMessage());
        e.setDocumentoSoporte("FAC-004512");
        e.setCostoUnitario(new BigDecimal("24000"));
        e.validar();   // con proveedor, documento y costo ya es válida
    }

    @Test
    void compraSinCostoDeFacturaSeRechaza() {
        Proveedor proveedor = new Proveedor(1, "900123456-1", "Distribuidora Ferretera del Valle S.A.S.",
                "", "", "", "", EstadoRegistro.ACTIVO);
        Entrada e = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, principal, proveedor);
        e.setDocumentoSoporte("FAC-004512");
        NegocioException ex = assertThrows(NegocioException.class, e::validar);
        assertEquals("Una compra exige el costo unitario de la factura, mayor que cero.", ex.getMessage());
        e.setCostoUnitario(BigDecimal.ZERO);
        assertThrows(NegocioException.class, e::validar, "El costo cero tampoco es válido");
    }

    @Test
    void elDocumentoDeLaCompraDebeLlevarElNumeroDeLaFactura() {
        Proveedor proveedor = new Proveedor(1, "900123456-1", "Distribuidora Ferretera del Valle S.A.S.",
                "", "", "", "", EstadoRegistro.ACTIVO);
        Entrada e = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, principal, proveedor);
        e.setCostoUnitario(new BigDecimal("24000"));
        e.setDocumentoSoporte("Factura");
        NegocioException ex = assertThrows(NegocioException.class, e::validar, "Solo la palabra no identifica la factura");
        assertTrue(ex.getMessage().startsWith("El documento soporte debe llevar el número"));
        e.setDocumentoSoporte("FAC-004512");
        e.validar();
    }

    @Test
    void unCostoMuyDistintoDelPromedioSeConsideraInusual() {
        BigDecimal promedio = new BigDecimal("48000");
        assertTrue(Entrada.costoInusual(new BigDecimal("5.5"), promedio), "5.5 pesos en vez de 5.500");
        assertTrue(Entrada.costoInusual(new BigDecimal("23000"), promedio), "menos de la mitad");
        assertTrue(Entrada.costoInusual(new BigDecimal("97000"), promedio), "más del doble");
        assertFalse(Entrada.costoInusual(new BigDecimal("55000"), promedio), "un alza normal de precio");
        assertFalse(Entrada.costoInusual(new BigDecimal("24000"), promedio), "justo la mitad todavía es aceptable");
        assertFalse(Entrada.costoInusual(new BigDecimal("5.5"), BigDecimal.ZERO), "sin promedio no hay con qué comparar");
    }

    // ----- kardex valorizado (costo promedio ponderado) -----

    @Test
    void laCompraRecalculaElCostoPromedioPonderado() {
        Entrada compra = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, principal, null);
        compra.setCostoUnitario(new BigDecimal("24000"));
        BigDecimal promedioActual = new BigDecimal("20000");
        // (10 x 20.000 + 10 x 24.000) / 20 = 22.000
        assertEquals(0, new BigDecimal("22000").compareTo(compra.nuevoCostoPromedio(10, promedioActual)));
        // La compra se valora al costo de la factura, no al promedio
        assertEquals(0, new BigDecimal("24000").compareTo(compra.costoParaValorar(promedioActual)));
        // Sin existencias, el promedio es el costo de la factura
        assertEquals(0, new BigDecimal("24000").compareTo(compra.nuevoCostoPromedio(0, promedioActual)));
    }

    @Test
    void soloLaCompraCambiaElCostoPromedio() {
        BigDecimal promedio = new BigDecimal("22000");
        Movimiento devolucion = new Entrada(martillo, tipo("Devolución", Naturaleza.ENTRADA, true, null), registra, 5, principal, null);
        Movimiento salida = new Salida(martillo, tipo("Consumo interno", Naturaleza.SALIDA, false, null), registra, 3, principal, null);
        Movimiento traslado = new Transferencia(martillo, tipo("Transferencia", Naturaleza.TRANSFERENCIA, false, null),
                registra, 8, principal, exhibicion);
        for (Movimiento m : List.of(devolucion, salida, traslado)) {
            assertEquals(promedio, m.costoParaValorar(promedio), "Se valora al costo promedio del momento");
            assertEquals(promedio, m.nuevoCostoPromedio(20, promedio), "El promedio no cambia");
        }
    }

    // ----- autorizaciones (segregación de funciones) -----

    @Test
    void quienRegistraNoPuedeAutorizar() {
        Salida s = new Salida(martillo, tipo("Merma", Naturaleza.SALIDA, true, supervisor), registra, 2, principal, null);
        NegocioException e = assertThrows(NegocioException.class, () -> s.autorizar(registra));
        assertEquals("Quien registra un movimiento no puede autorizarlo.", e.getMessage());
    }

    @Test
    void elSupervisorAutorizaYElMovimientoYaNoSeModifica() {
        Salida s = new Salida(martillo, tipo("Merma", Naturaleza.SALIDA, true, supervisor), registra, 2, principal, null);
        s.autorizar(autoriza);
        assertEquals(EstadoMovimiento.AUTORIZADO, s.getEstado());
        assertEquals(autoriza, s.getUsuarioAutoriza());
        assertThrows(NegocioException.class, () -> s.rechazar(autoriza, "tarde"));
    }

    @Test
    void elRechazoExigeMotivo() {
        Salida s = new Salida(martillo, tipo("Merma", Naturaleza.SALIDA, true, supervisor), registra, 2, principal, null);
        assertThrows(NegocioException.class, () -> s.rechazar(autoriza, "  "));
        assertTrue(s.estaPendiente());
    }

    // ----- usuario y producto -----

    @Test
    void laCuentaSeBloqueaAlTercerIntentoFallido() {
        Usuario u = usuario(9, "prueba", almacenista);
        u.registrarIntentoFallido();
        u.registrarIntentoFallido();
        assertFalse(u.estaBloqueado());
        u.registrarIntentoFallido();
        assertTrue(u.estaBloqueado());
    }

    @Test
    void umbralesDelProducto() {
        Producto p = new Producto();
        p.setStockMinimo(40);
        p.setStockMaximo(30);
        assertFalse(p.umbralesValidos(), "El mínimo no puede superar al máximo");
        p.setStockMaximo(40);
        assertTrue(p.umbralesValidos(), "Mínimo igual al máximo es válido (RF-003)");
        p.setStockMinimo(null);
        assertEquals(10, p.minimoEfectivo(10), "Sin umbral propio usa el general");
    }

    @Test
    void diferenciaDelConteoFisico() {
        LineaConteo l = new LineaConteo("TOR-0145", "Tornillo", 18, 16);
        assertEquals(-2, l.diferencia());
    }
}
