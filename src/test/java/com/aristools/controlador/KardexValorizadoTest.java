package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.BodegaDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.ProveedorDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.Salida;
import com.aristools.pruebas.BaseDePruebas;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Prueba de punta a punta del kardex valorizado (costo promedio ponderado)
 * sobre la base H2 en memoria, con el flujo real del sistema:
 * el Almacenista registra la compra, el Jefe de Compras la autoriza y luego
 * hay una salida.
 *
 * Producto usado: ELE-001 (cable). Empieza con 12 rollos a $ 185.000.
 */
@ExtendWith(BaseDePruebas.class)
class KardexValorizadoTest {

    private static final String CLAVE = "Aristools2026*";
    private static final String FACTURA = "FAC-PRUEBA-0001";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void compraAutorizadaRecalculaElPromedioYElKardexQuedaValorizado() throws Exception {
        ProductoDAO productoDAO = new ProductoDAO();
        Producto cable = productoDAO.buscarPorCodigo("ELE-001");
        Bodega principal = new BodegaDAO().listar(false).stream()
                .filter(b -> b.getCodigo().equals("BOD-01")).findFirst().orElseThrow();
        Proveedor proveedor = new ProveedorDAO().listar(false).get(0);
        assertEquals(0, new BigDecimal("185000").compareTo(cable.getCostoPromedio()),
                "El costo promedio inicial es el costo de referencia");

        // 1) El Almacenista registra la compra: 8 rollos a $ 200.000 (queda Pendiente)
        login.iniciarSesion("almacen", CLAVE);
        assertNull(new MovimientoControlador().ultimoCostoCompra(cable.getIdProducto()),
                "Sin compras anteriores no hay costo que sugerir");
        Entrada compra = new Entrada(cable, new TipoMovimientoDAO().buscarPorNombre("Compra"), null, 8,
                principal, proveedor);
        compra.setDocumentoSoporte(FACTURA);
        compra.setCostoUnitario(new BigDecimal("200000"));
        new MovimientoControlador().registrarEntradas(List.of(compra));
        assertEquals(0, new BigDecimal("185000").compareTo(productoDAO.buscarPorCodigo("ELE-001").getCostoPromedio()),
                "Una compra pendiente todavía no cambia el costo promedio");

        // 2) El Jefe de Compras la autoriza: (12 x 185.000 + 8 x 200.000) / 20 = 191.000
        login.iniciarSesion("compras", CLAVE);
        AutorizacionControlador autorizaciones = new AutorizacionControlador();
        Movimiento pendiente = autorizaciones.listarPendientes(Naturaleza.ENTRADA).stream()
                .filter(m -> FACTURA.equals(m.getDocumentoSoporte())).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("200000").compareTo(pendiente.getCostoUnitario()),
                "El costo de la factura se guarda con la compra");
        autorizaciones.autorizar(pendiente.getIdMovimiento());
        assertEquals(0, new BigDecimal("191000").compareTo(productoDAO.buscarPorCodigo("ELE-001").getCostoPromedio()));

        // 3) Una salida de 5 rollos se valora al promedio y no lo cambia
        login.iniciarSesion("almacen", CLAVE);
        assertEquals(0, new BigDecimal("200000").compareTo(new MovimientoControlador().ultimoCostoCompra(cable.getIdProducto())),
                "La pantalla de Entrada sugiere el costo de la última compra");
        Salida consumo = new Salida(cable, new TipoMovimientoDAO().buscarPorNombre("Consumo interno"), null, 5,
                principal, null);
        consumo.setMotivo("Instalación eléctrica de la bodega");
        new MovimientoControlador().registrarSalida(consumo);
        assertEquals(0, new BigDecimal("191000").compareTo(productoDAO.buscarPorCodigo("ELE-001").getCostoPromedio()));

        // 4) Kardex: 12 + 8 - 5 = 15 rollos; 2.220.000 + 1.600.000 - 955.000 = 2.865.000 = 15 x 191.000
        login.iniciarSesion("admin", CLAVE);
        List<LineaKardex> kardex = new ReporteControlador().generarKardex(cable.getIdProducto(), null, null, null);
        LineaKardex lineaCompra = kardex.get(kardex.size() - 2);
        LineaKardex lineaSalida = kardex.get(kardex.size() - 1);
        assertEquals("Compra", lineaCompra.getTipo());
        assertEquals(0, new BigDecimal("1600000").compareTo(lineaCompra.getValorEntrada()));
        assertEquals(0, new BigDecimal("3820000").compareTo(lineaCompra.getValorSaldo()));
        assertEquals("Consumo interno", lineaSalida.getTipo());
        assertEquals(0, new BigDecimal("191000").compareTo(lineaSalida.getCostoUnitario()));
        assertEquals(0, new BigDecimal("955000").compareTo(lineaSalida.getValorSalida()));
        assertEquals(15, lineaSalida.getSaldo());
        assertEquals(0, new BigDecimal("2865000").compareTo(lineaSalida.getValorSaldo()),
                "Valor del saldo = unidades x costo promedio");
        assertTrue(kardex.stream().allMatch(l -> l.getValorSaldo().signum() >= 0));
    }
}
