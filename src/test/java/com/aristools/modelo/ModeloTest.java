package com.aristools.modelo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.util.NegocioException;
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
        s.validar();   // con motivo ya es válida
    }

    @Test
    void compraSinProveedorSeRechaza() {
        Entrada e = new Entrada(martillo, tipo("Compra", Naturaleza.ENTRADA, true, null), registra, 10, principal, null);
        assertThrows(NegocioException.class, e::validar);
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
