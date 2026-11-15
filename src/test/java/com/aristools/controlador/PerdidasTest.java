package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.BodegaDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.CausaBaja;
import com.aristools.modelo.EstadoMovimiento;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Salida;
import com.aristools.modelo.Transferencia;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Prueba de punta a punta de una avería sobre la base H2 en memoria:
 * la mercancía pasa a cuarentena, se registra la baja con su causa, el
 * Supervisor la autoriza y aparece valorizada en el reporte de pérdidas.
 *
 * Producto usado: PIN-001 (pintura), con costo de $ 48.000.
 */
@ExtendWith(BaseDePruebas.class)
class PerdidasTest {

    private static final String CLAVE = "Aristools2026*";
    private static final String MOTIVO = "Galones mojados por una gotera (prueba)";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void averiaPasaPorCuarentenaSeAutorizaYQuedaEnElReporteDePerdidas() throws Exception {
        Producto pintura = new ProductoDAO().buscarPorCodigo("PIN-001");
        List<Bodega> bodegas = new BodegaDAO().listar(false);
        Bodega principal = bodegas.stream().filter(b -> b.getCodigo().equals("BOD-01")).findFirst().orElseThrow();
        Bodega cuarentena = bodegas.stream().filter(Bodega::isCuarentena).findFirst().orElseThrow();
        TipoMovimientoDAO tipos = new TipoMovimientoDAO();

        // 1) El Almacenista separa 2 galones en cuarentena (transferencia inmediata)
        login.iniciarSesion("almacen", CLAVE);
        MovimientoControlador movimientos = new MovimientoControlador();
        movimientos.registrarTransferencia(new Transferencia(pintura, tipos.buscarPorNombre("Transferencia"), null, 2,
                principal, cuarentena));

        // Lo que está en cuarentena no se puede usar
        Salida consumo = new Salida(pintura, tipos.buscarPorNombre("Consumo interno"), null, 1, cuarentena, null);
        assertThrows(NegocioException.class, () -> movimientos.registrarSalida(consumo));

        // 2) Registra la baja con su causa: queda Pendiente
        Salida baja = new Salida(pintura, tipos.buscarPorNombre("Producto dañado o vencido"), null, 2, cuarentena, null);
        baja.setMotivo(MOTIVO);
        baja.setCausaBaja(CausaBaja.HUMEDAD);
        movimientos.registrarSalida(baja);

        // 3) El Supervisor la autoriza
        login.iniciarSesion("supervisor", CLAVE);
        AutorizacionControlador autorizaciones = new AutorizacionControlador();
        Movimiento pendiente = autorizaciones.listarPendientes(Naturaleza.SALIDA).stream()
                .filter(m -> MOTIVO.equals(m.getMotivo())).findFirst().orElseThrow();
        assertEquals(CausaBaja.HUMEDAD, pendiente.getCausaBaja());
        ReporteControlador reportes = new ReporteControlador();
        assertThrows(NegocioException.class, () -> reportes.movimientoParaDocumento(pendiente.getIdMovimiento()),
                "Un movimiento pendiente todavía no tiene documento de soporte");
        autorizaciones.autorizar(pendiente.getIdMovimiento());

        // 4) Ya tiene acta de baja, con quién la registró y quién la autorizó
        Movimiento acta = reportes.movimientoParaDocumento(pendiente.getIdMovimiento());
        assertEquals(EstadoMovimiento.AUTORIZADO, acta.getEstado());
        assertEquals("Acta de baja", acta.tituloDocumento());
        assertTrue(acta.numeroDocumento().startsWith("BAJ-"));
        assertEquals("almacen", acta.getUsuarioRegistra().getNombreUsuario());
        assertEquals("Almacenista", acta.getUsuarioRegistra().getRol().getNombreRol());
        assertEquals("supervisor", acta.getUsuarioAutoriza().getNombreUsuario());

        // 5) Reporte de pérdidas: 2 galones x $ 48.000 = $ 96.000 por humedad, en cuarentena
        Movimiento enReporte = reportes.reportePerdidas(null, null, cuarentena.getIdBodega()).stream()
                .filter(m -> m.getIdMovimiento() == acta.getIdMovimiento()).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("96000").compareTo(enReporte.valorTotal()));
        assertEquals(CausaBaja.HUMEDAD, enReporte.getCausaBaja());
        assertTrue(reportes.reportePerdidas(null, null, principal.getIdBodega()).stream()
                .noneMatch(m -> m.getIdMovimiento() == acta.getIdMovimiento()), "La baja salió de cuarentena");
    }

    @Test
    void elReporteDePerdidasEsSoloParaSupervisorYAdministrador() {
        login.iniciarSesion("compras", CLAVE);
        assertThrows(NegocioException.class, () -> new ReporteControlador().reportePerdidas(null, null, null));
        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> new ReporteControlador().reportePerdidas(null, null, null));
        login.iniciarSesion("admin", CLAVE);
        new ReporteControlador().reportePerdidas(null, null, null);   // permitido
    }
}
