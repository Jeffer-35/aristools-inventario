package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.BodegaDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.ProveedorDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.ClasificacionAbc;
import com.aristools.modelo.Conteo;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.LineaConteo;
import com.aristools.modelo.LoteVencimiento;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.io.File;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Sesión 5 sobre la base H2 en memoria: lote y vencimiento en la compra,
 * reporte FEFO, clasificación ABC, conteo a ciegas e historial de conteos.
 */
@ExtendWith(BaseDePruebas.class)
class ConteoYVencimientoTest {

    private static final String CLAVE = "Aristools2026*";
    private static final String FACTURA = "FAC-VENCE-0001";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    private static Bodega principal() throws Exception {
        return new BodegaDAO().listar(false).stream().filter(b -> b.getCodigo().equals("BOD-01")).findFirst().orElseThrow();
    }

    @Test
    void laCompraConLoteApareceEnVencimientosCuandoSeAutoriza() throws Exception {
        Producto pintura = new ProductoDAO().buscarPorCodigo("PIN-001");
        assertTrue(pintura.isControlaVencimiento(), "La pintura de prueba controla vencimiento");
        LocalDate vence = LocalDate.now().plusDays(20);

        login.iniciarSesion("almacen", CLAVE);
        Entrada compra = new Entrada(pintura, new TipoMovimientoDAO().buscarPorNombre("Compra"), null, 6,
                principal(), new ProveedorDAO().listar(false).get(0));
        compra.setDocumentoSoporte(FACTURA);
        compra.setCostoUnitario(new BigDecimal("48000"));
        assertThrows(NegocioException.class, () -> new MovimientoControlador().registrarEntradas(List.of(compra)),
                "Sin lote ni fecha de vencimiento la compra se rechaza");
        compra.setLote("L-PRUEBA-01");
        compra.setFechaVencimiento(vence);
        new MovimientoControlador().registrarEntradas(List.of(compra));
        assertTrue(new ReporteControlador().vencimientos().stream().noneMatch(l -> "L-PRUEBA-01".equals(l.getLote())),
                "Una compra pendiente todavía no está en bodega");

        login.iniciarSesion("compras", CLAVE);
        AutorizacionControlador autorizaciones = new AutorizacionControlador();
        Movimiento pendiente = autorizaciones.listarPendientes(Naturaleza.ENTRADA).stream()
                .filter(m -> FACTURA.equals(m.getDocumentoSoporte())).findFirst().orElseThrow();
        assertEquals("L-PRUEBA-01", ((Entrada) pendiente).getLote(), "El lote se guarda con la compra");
        assertEquals(vence, ((Entrada) pendiente).getFechaVencimiento());
        autorizaciones.autorizar(pendiente.getIdMovimiento());

        LoteVencimiento lote = new ReporteControlador().vencimientos().stream()
                .filter(l -> "L-PRUEBA-01".equals(l.getLote())).findFirst().orElseThrow();
        assertEquals(6, lote.getSaldoEstimado(), "Es el único lote: las 6 unidades siguen en bodega");
        assertEquals(LoteVencimiento.POR_VENCER, lote.estado(LocalDate.now(), 30), "Vence en 20 días: avisa");
        assertTrue(new PanelControlador().resumen().get("Lotes por vencer") >= 1, "La tarjeta de Inicio lo cuenta");
    }

    @Test
    void laClasificacionAbcIncluyeTodosLosProductosActivos() {
        login.iniciarSesion("admin", CLAVE);
        List<ClasificacionAbc> abc = new ReporteControlador().clasificacionAbc(null, null);
        assertEquals(new ProductoControlador().listarProductos("", false).size(), abc.size());
        assertTrue(abc.stream().allMatch(c -> "ABC".indexOf(c.getClase()) >= 0));
        for (int i = 1; i < abc.size(); i++) {
            assertTrue(abc.get(i - 1).getValor().compareTo(abc.get(i).getValor()) >= 0, "De mayor a menor valor");
        }
        login.iniciarSesion("supervisor", CLAVE);
        assertFalse(new ReporteControlador().clasificacionAbc(null, null).isEmpty(), "El Supervisor ve reportes");
    }

    @Test
    void conteoACiegasSinDiferenciasQuedaEnElHistorialConExactitudCien() throws Exception {
        login.iniciarSesion("almacen", CLAVE);
        ConteoControlador conteo = new ConteoControlador();
        int bodega = principal().getIdBodega();
        File normal = File.createTempFile("conteo", ".csv");
        File ciega = File.createTempFile("conteo_ciego", ".csv");
        normal.deleteOnExit();
        ciega.deleteOnExit();

        // La plantilla a ciegas no trae el stock del sistema
        int productos = conteo.generarPlantilla(bodega, ciega, null, true);
        List<String> filasCiegas = Files.readAllLines(ciega.toPath(), StandardCharsets.UTF_8);
        assertTrue(filasCiegas.get(0).endsWith("codigo;producto;cantidad_contada"));
        assertEquals(productos, filasCiegas.size() - 1);
        assertEquals(2, filasCiegas.get(1).split(";", -1).length - 1, "Solo código, producto y la casilla vacía");

        // Quien cuenta escribe lo que encontró; aquí coincide con el sistema (se toma de la plantilla normal)
        conteo.generarPlantilla(bodega, normal);
        List<String> contadas = Files.readAllLines(normal.toPath(), StandardCharsets.UTF_8).stream().skip(1)
                .map(f -> f.split(";")[0] + ";" + f.split(";")[1] + ";" + f.split(";")[2]).collect(Collectors.toList());
        Files.write(ciega.toPath(), contadas, StandardCharsets.UTF_8);
        List<LineaConteo> lineas = conteo.leerPlantilla(bodega, ciega);
        assertTrue(lineas.stream().allMatch(l -> l.diferencia() == 0));

        int antes = conteo.historial(bodega).size();
        Conteo guardado = conteo.guardarConteo(bodega, lineas, null, true);
        assertEquals(100.0, guardado.exactitud(), 0.001);
        List<Conteo> historial = conteo.historial(bodega);
        assertEquals(antes + 1, historial.size(), "Un conteo sin diferencias también se guarda");
        assertEquals(guardado.getIdConteo(), historial.get(0).getIdConteo(), "El más reciente va primero");
        assertTrue(historial.get(0).isACiegas());
        assertEquals(lineas.size(), conteo.detalleConteo(guardado.getIdConteo()).size());

        // El Jefe de Compras ve el historial (tiene reportes) pero no puede contar
        login.iniciarSesion("compras", CLAVE);
        assertFalse(new ConteoControlador().historial(null).isEmpty());
        assertThrows(NegocioException.class, () -> new ConteoControlador().guardarConteo(bodega, lineas, null, false));
    }

    @Test
    void laPlantillaCiclicaSoloTraeLaClasePedida() throws Exception {
        login.iniciarSesion("almacen", CLAVE);
        ConteoControlador conteo = new ConteoControlador();
        int bodega = principal().getIdBodega();
        File general = File.createTempFile("general", ".csv");
        general.deleteOnExit();
        int todos = conteo.generarPlantilla(bodega, general, null, false);
        int suma = 0;
        for (char clase : new char[]{'A', 'B', 'C'}) {
            File f = File.createTempFile("ciclico", ".csv");
            f.deleteOnExit();
            try {
                suma += conteo.generarPlantilla(bodega, f, clase, false);
            } catch (NegocioException e) {
                assertTrue(e.getMessage().contains("clase " + clase), "Una clase sin productos avisa y no crea plantilla vacía");
            }
        }
        assertEquals(todos, suma, "Cada producto activo está en una sola clase");
    }
}
