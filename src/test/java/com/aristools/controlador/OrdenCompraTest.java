package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.BodegaDAO;
import com.aristools.dao.CompraDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.ProveedorDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.EstadoOrden;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.OrdenCompra;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Seguimiento de la orden de compra: Pendiente → Recibida en parte →
 * Recibida, y anulación. Usa productos propios (OCP-...) para no cambiar el
 * stock ni el costo promedio de los productos que usan las demás pruebas.
 */
@ExtendWith(BaseDePruebas.class)
class OrdenCompraTest {

    private static final String CLAVE = "Aristools2026*";
    private final LoginControlador login = new LoginControlador();
    private final CompraDAO compraDAO = new CompraDAO();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void laOrdenPasaDePendienteARecibidaEnParteYARecibida() throws Exception {
        Producto tornillo = producto("OCP-001", "Tornillo de prueba para órdenes");
        Producto chazo = producto("OCP-002", "Chazo de prueba para órdenes");
        Proveedor distribuidora = distribuidora();

        // 1) El Jefe de Compras pide 10 tornillos y 5 chazos
        login.iniciarSesion("compras", CLAVE);
        OrdenCompra orden = new CompraControlador().generarOrden(distribuidora, List.of(
                new LineaOrdenCompra(tornillo, 10, new BigDecimal("1000")),
                new LineaOrdenCompra(chazo, 5, new BigDecimal("2000"))), null);
        assertEquals(EstadoOrden.PENDIENTE, compraDAO.buscarOrden(orden.getIdOrden()).getEstado());
        assertTrue(new CompraControlador().listarOrdenes(true).stream().anyMatch(o -> o.getIdOrden() == orden.getIdOrden()),
                "Una orden nueva aparece entre las que esperan mercancía");

        // 2) Llegan 6 tornillos: el Almacenista registra la entrada contra la orden
        login.iniciarSesion("almacen", CLAVE);
        assertTrue(new CompraControlador().ordenesAbiertasDe(distribuidora.getIdProveedor()).stream()
                .anyMatch(o -> o.getIdOrden() == orden.getIdOrden()), "El Almacenista ve la orden al recibir");
        MovimientoControlador movimientos = new MovimientoControlador();
        movimientos.registrarEntradas(List.of(entrada(tornillo, 6, distribuidora, orden, "FAC-OC-001")));
        assertEquals(EstadoOrden.PENDIENTE, compraDAO.buscarOrden(orden.getIdOrden()).getEstado(),
                "Mientras la entrada esté pendiente la orden no cambia");

        // Reglas: no pasarse de lo que falta (contando lo que ya viene en espera) y solo productos de la orden
        NegocioException pasa = assertThrows(NegocioException.class, () -> movimientos.registrarEntradas(
                List.of(entrada(tornillo, 5, distribuidora, orden, "FAC-OC-002"))));
        assertTrue(pasa.getMessage().contains("le faltan 4"), pasa.getMessage());
        Producto otro = new ProductoDAO().buscarPorCodigo("ELE-001");
        assertThrows(NegocioException.class, () -> movimientos.registrarEntradas(
                List.of(entrada(otro, 1, distribuidora, orden, "FAC-OC-003"))), "Un producto que no se pidió");

        // 3) El Jefe de Compras autoriza: la orden queda Recibida en parte (6 de 15 = 40 %)
        login.iniciarSesion("compras", CLAVE);
        autorizarFactura("FAC-OC-001");
        OrdenCompra parcial = compraDAO.buscarOrden(orden.getIdOrden());
        assertEquals(EstadoOrden.PARCIAL, parcial.getEstado());
        assertEquals(6, parcial.linea(tornillo.getIdProducto()).getCantidadRecibida());
        assertEquals(40, parcial.porcentajeRecibido());

        // 4) Llega el resto: 4 tornillos y 5 chazos en una sola entrada
        login.iniciarSesion("almacen", CLAVE);
        movimientos.registrarEntradas(List.of(entrada(tornillo, 4, distribuidora, orden, "FAC-OC-004"),
                entrada(chazo, 5, distribuidora, orden, "FAC-OC-004")));
        login.iniciarSesion("compras", CLAVE);
        autorizarFactura("FAC-OC-004");
        autorizarFactura("FAC-OC-004");
        OrdenCompra completa = compraDAO.buscarOrden(orden.getIdOrden());
        assertEquals(EstadoOrden.RECIBIDA, completa.getEstado());
        assertEquals(100, completa.porcentajeRecibido());
        assertTrue(new CompraControlador().listarOrdenes(true).stream().noneMatch(o -> o.getIdOrden() == orden.getIdOrden()),
                "Una orden recibida ya no espera mercancía");

        // 5) Una orden recibida no recibe más ni se puede anular
        assertThrows(NegocioException.class, () -> new CompraControlador().anularOrden(orden.getIdOrden(), "Ya no se necesita"));
        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> movimientos.registrarEntradas(
                List.of(entrada(tornillo, 1, distribuidora, orden, "FAC-OC-005"))));
    }

    @Test
    void laOrdenSeAnulaConMotivoYSinEntradasEnEspera() throws Exception {
        Producto chazo = producto("OCP-002", "Chazo de prueba para órdenes");
        Proveedor distribuidora = distribuidora();
        login.iniciarSesion("compras", CLAVE);
        CompraControlador compras = new CompraControlador();
        OrdenCompra orden = compras.generarOrden(distribuidora,
                List.of(new LineaOrdenCompra(chazo, 3, new BigDecimal("2000"))), null);

        // Una entrada en espera impide anular: primero se autoriza o se rechaza
        login.iniciarSesion("almacen", CLAVE);
        new MovimientoControlador().registrarEntradas(List.of(entrada(chazo, 1, distribuidora, orden, "FAC-OC-010")));
        login.iniciarSesion("compras", CLAVE);
        assertThrows(NegocioException.class, () -> compras.anularOrden(orden.getIdOrden(), "El proveedor no tiene"),
                "Hay una entrada esperando autorización");
        Movimiento pendiente = pendientePorFactura("FAC-OC-010");
        new AutorizacionControlador().rechazar(pendiente.getIdMovimiento(), "La factura no corresponde");
        assertEquals(EstadoOrden.PENDIENTE, compraDAO.buscarOrden(orden.getIdOrden()).getEstado(),
                "Rechazar la entrada no cambia la orden");

        assertThrows(NegocioException.class, () -> compras.anularOrden(orden.getIdOrden(), "no"), "El motivo es obligatorio");
        compras.anularOrden(orden.getIdOrden(), "El proveedor no tiene el producto");
        OrdenCompra anulada = compraDAO.buscarOrden(orden.getIdOrden());
        assertEquals(EstadoOrden.ANULADA, anulada.getEstado());
        assertEquals("El proveedor no tiene el producto", anulada.getMotivoAnulacion());

        // Una orden anulada ya no recibe mercancía
        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> new MovimientoControlador().registrarEntradas(
                List.of(entrada(chazo, 1, distribuidora, orden, "FAC-OC-011"))));
    }

    @Test
    void elEstadoSeCalculaConLoRecibido() {
        Producto p = new Producto();
        p.setIdProducto(1);
        LineaOrdenCompra linea = new LineaOrdenCompra(p, 10, BigDecimal.ONE);
        assertEquals(EstadoOrden.PENDIENTE, OrdenCompra.estadoSegunRecibido(List.of(linea)));
        linea.setCantidadRecibida(4);
        assertEquals(EstadoOrden.PARCIAL, OrdenCompra.estadoSegunRecibido(List.of(linea)));
        assertEquals(6, linea.pendiente());
        linea.setCantidadRecibida(10);
        assertEquals(EstadoOrden.RECIBIDA, OrdenCompra.estadoSegunRecibido(List.of(linea)));
        assertTrue(EstadoOrden.PARCIAL.estaAbierta());
        assertTrue(!EstadoOrden.ANULADA.estaAbierta());
    }

    // Crea el producto de prueba la primera vez (como Administrador) y lo devuelve.
    private Producto producto(String codigo, String nombre) throws Exception {
        Producto p = new ProductoDAO().buscarPorCodigo(codigo);
        if (p == null) {
            login.iniciarSesion("admin", CLAVE);
            ProductoControlador productos = new ProductoControlador();
            productos.guardarProducto(new Producto(0, codigo, nombre, "Solo para las pruebas de órdenes de compra",
                    productos.listarCategorias(false).get(0), "Unidad", new BigDecimal("1000"), null, null,
                    EstadoRegistro.ACTIVO));
            login.cerrarSesion();
            p = new ProductoDAO().buscarPorCodigo(codigo);
        }
        assertNotNull(p);
        return p;
    }

    private Proveedor distribuidora() throws Exception {
        return new ProveedorDAO().listar(false).stream()
                .filter(p -> p.getNit().equals("900123456-1")).findFirst().orElseThrow();
    }

    private Entrada entrada(Producto p, int cantidad, Proveedor proveedor, OrdenCompra orden, String factura)
            throws Exception {
        Bodega principal = new BodegaDAO().listar(false).stream()
                .filter(b -> b.getCodigo().equals("BOD-01")).findFirst().orElseThrow();
        Entrada e = new Entrada(p, new TipoMovimientoDAO().buscarPorNombre("Compra"), null, cantidad, principal, proveedor);
        e.setDocumentoSoporte(factura);
        e.setCostoUnitario(new BigDecimal("1000"));
        e.setIdOrden(orden.getIdOrden());
        return e;
    }

    private Movimiento pendientePorFactura(String factura) {
        return new AutorizacionControlador().listarPendientes(Naturaleza.ENTRADA).stream()
                .filter(m -> factura.equals(m.getDocumentoSoporte())).findFirst().orElseThrow();
    }

    private void autorizarFactura(String factura) {
        new AutorizacionControlador().autorizar(pendientePorFactura(factura).getIdMovimiento());
    }
}
