package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.CompraDAO;
import com.aristools.dao.ProveedorDAO;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.OrdenCompra;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.SugerenciaCompra;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Sugerencia de compra y orden de compra sobre la base H2 en memoria.
 *
 * En los datos de prueba la pintura PIN-001 está por debajo de su mínimo
 * (8): por eso siempre aparece entre los productos que hay que pedir.
 */
@ExtendWith(BaseDePruebas.class)
class CompraTest {

    private static final String CLAVE = "Aristools2026*";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void sugiereLaPinturaYGeneraLaOrdenDeCompra() throws Exception {
        login.iniciarSesion("compras", CLAVE);
        CompraControlador compras = new CompraControlador();
        List<SugerenciaCompra> porPedir = compras.sugerencias(null, true);
        assertTrue(porPedir.stream().allMatch(SugerenciaCompra::hayQuePedir), "El filtro deja solo lo que hay que pedir");
        SugerenciaCompra pintura = porPedir.stream()
                .filter(s -> s.getProducto().getCodigo().equals("PIN-001")).findFirst().orElseThrow();
        assertEquals(pintura.getStockMaximo() - pintura.getStockActual(), pintura.cantidadSugerida(),
                "Se pide lo que falta para llegar al máximo");
        assertEquals(compras.diasEntregaPorDefecto(), pintura.getDiasEntrega(),
                "Sin compras anteriores no tiene proveedor y usa los días generales");
        assertTrue(compras.sugerencias(null, false).size() >= porPedir.size());

        // La orden va a la Distribuidora (3 días de entrega en los datos de prueba)
        Proveedor distribuidora = new ProveedorDAO().listar(false).stream()
                .filter(p -> p.getNit().equals("900123456-1")).findFirst().orElseThrow();
        assertEquals(3, distribuidora.getDiasEntrega());
        OrdenCompra orden = compras.generarOrden(distribuidora, List.of(new LineaOrdenCompra(pintura.getProducto(),
                pintura.cantidadSugerida(), pintura.getCostoUnitario())), "Entregar en la bodega principal");
        assertTrue(orden.numero().startsWith("OC-"));
        assertEquals(0, orden.total().compareTo(new CompraDAO().totalOrden(orden.getIdOrden())),
                "La orden quedó guardada con su total");
    }

    @Test
    void soloComprasYAdministradorUsanLaSugerencia() {
        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> new CompraControlador().sugerencias(null, true));
        login.iniciarSesion("supervisor", CLAVE);
        assertThrows(NegocioException.class, () -> new CompraControlador().sugerencias(null, true));
        login.iniciarSesion("admin", CLAVE);
        assertFalse(new CompraControlador().sugerencias(null, false).isEmpty());
    }
}
