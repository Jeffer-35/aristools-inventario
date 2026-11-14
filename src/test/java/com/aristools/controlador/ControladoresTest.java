package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.BodegaDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.ReporteDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaConteo;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Salida;
import com.aristools.modelo.Transferencia;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pruebas de los controladores sobre la base H2 en memoria (BaseDePruebas).
 * O solo leen, o la operación es rechazada y la transacción hace ROLLBACK,
 * así que los datos de prueba no cambian entre una prueba y otra.
 */
@ExtendWith(BaseDePruebas.class)
class ControladoresTest {

    private static final String CLAVE = "Aristools2026*";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    private Bodega principal() throws Exception {
        return new BodegaDAO().listar(false).stream().filter(b -> b.getCodigo().equals("BOD-01")).findFirst().orElseThrow();
    }

    @Test
    void panelPrincipalTieneLasCuatroTarjetas() {
        login.iniciarSesion("almacen", CLAVE);
        Map<String, Integer> r = new PanelControlador().resumen();
        assertEquals(Set.of("Productos activos", "Bajo stock", "Sobrestock", "Movimientos pendientes"), r.keySet());
        assertTrue(r.get("Productos activos") >= 4);
    }

    @Test
    void cadaRolSoloHaceLoQueLePermite() {
        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> new UsuarioControlador().listar());
        login.iniciarSesion("admin", CLAVE);
        assertThrows(NegocioException.class, () -> new AutorizacionControlador().listarPendientes(Naturaleza.ENTRADA),
                "El administrador no autoriza movimientos");
        login.iniciarSesion("compras", CLAVE);
        new AutorizacionControlador().listarPendientes(Naturaleza.ENTRADA);   // permitido
    }

    @Test
    void salidaConStockInsuficienteSeRechazaYNoGuardaNada() throws Exception {
        login.iniciarSesion("almacen", CLAVE);
        int antes = new ReporteDAO().movimientos(new FiltroMovimiento()).size();
        Producto martillo = new ProductoDAO().buscarPorCodigo("HER-001");
        Salida s = new Salida(martillo, new TipoMovimientoDAO().buscarPorNombre("Consumo interno"), null,
                100000, principal(), null);
        NegocioException e = assertThrows(NegocioException.class, () -> new MovimientoControlador().registrarSalida(s));
        assertTrue(e.getMessage().startsWith("Stock insuficiente"), e.getMessage());
        assertEquals(antes, new ReporteDAO().movimientos(new FiltroMovimiento()).size(), "ROLLBACK: no se guardó nada");
    }

    @Test
    void transferenciaALaMismaBodegaSeRechaza() throws Exception {
        login.iniciarSesion("almacen", CLAVE);
        Transferencia t = new Transferencia(new ProductoDAO().buscarPorCodigo("TOR-001"),
                new TipoMovimientoDAO().buscarPorNombre("Transferencia"), null, 5, principal(), principal());
        assertThrows(NegocioException.class, () -> new MovimientoControlador().registrarTransferencia(t));
    }

    @Test
    void conteoConUnaLineaErradaRechazaTodoElArchivo() throws Exception {
        login.iniciarSesion("almacen", CLAVE);
        ConteoControlador conteo = new ConteoControlador();
        int bodega = principal().getIdBodega();
        File plantilla = File.createTempFile("conteo", ".csv");
        plantilla.deleteOnExit();
        conteo.generarPlantilla(bodega, plantilla);
        List<String> filas = Files.readAllLines(plantilla.toPath(), StandardCharsets.UTF_8);
        assertTrue(filas.get(0).contains("codigo;producto;stock_sistema;cantidad_contada"));

        // Cantidad contada = stock del sistema en todas las filas, excepto una con texto
        List<String> conError = filas.stream().skip(1).map(f -> f + f.split(";")[2]).collect(Collectors.toList());
        conError.set(0, conError.get(0).replaceAll(";\\d+$", ";abc"));
        Files.write(plantilla.toPath(), conError, StandardCharsets.UTF_8);
        NegocioException e = assertThrows(NegocioException.class, () -> conteo.leerPlantilla(bodega, plantilla));
        assertTrue(e.getMessage().contains("1 línea(s) con error"), e.getMessage());

        // Sin errores y sin diferencias: se lee bien, pero no se generan ajustes
        Files.write(plantilla.toPath(), filas.stream().skip(1).map(f -> f + f.split(";")[2]).collect(Collectors.toList()),
                StandardCharsets.UTF_8);
        List<LineaConteo> lineas = conteo.leerPlantilla(bodega, plantilla);
        assertTrue(lineas.stream().allMatch(l -> l.diferencia() == 0));
        assertThrows(NegocioException.class, () -> conteo.generarAjustes(bodega, lineas));
    }

    @Test
    void elJefeDeComprasSoloVeEntradasEnElKardex() throws Exception {
        login.iniciarSesion("compras", CLAVE);
        Producto martillo = new ProductoDAO().buscarPorCodigo("HER-001");
        List<LineaKardex> k = new ReporteControlador().generarKardex(martillo.getIdProducto(), null, null, null);
        assertTrue(k.stream().allMatch(l -> Set.of("Compra", "Devolución", "Saldo inicial").contains(l.getTipo())));
    }

    @Test
    void validacionesDeCatalogosYParametros() {
        login.iniciarSesion("admin", CLAVE);
        Producto duplicado = new Producto();
        duplicado.setCodigo("HER-001");
        duplicado.setNombre("Otro martillo");
        duplicado.setUnidadMedida("Unidad");
        duplicado.setCategoria(new ProductoControlador().listarCategorias(false).get(0));
        NegocioException e = assertThrows(NegocioException.class, () -> new ProductoControlador().guardarProducto(duplicado));
        assertEquals("Ya existe un producto con el código HER-001.", e.getMessage());
        assertThrows(NegocioException.class, () -> new ParametroControlador().guardar("Aristools", 50, 10),
                "Mínimo mayor que el máximo");
    }
}
