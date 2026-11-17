package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.dao.ReporteDAO;
import com.aristools.modelo.Indicador;
import com.aristools.modelo.Indicador.Estado;
import com.aristools.modelo.LineaControl;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/** Control del período y tablero de indicadores (sesión 6). */
@ExtendWith(BaseDePruebas.class)
class IndicadoresTest {

    private static final String CLAVE = "Aristools2026*";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void laLineaDeControlSeparaEntradasSalidasPerdidasYSobrantes() {
        LineaControl l = new LineaControl(new Producto());
        l.acumular(Naturaleza.ENTRADA, "Ajuste positivo", true, 20, new BigDecimal("200000"));   // antes del período
        l.acumular(Naturaleza.ENTRADA, "Compra", false, 10, new BigDecimal("110000"));
        l.acumular(Naturaleza.SALIDA, "Consumo interno", false, 6, new BigDecimal("62000"));
        l.acumular(Naturaleza.SALIDA, "Merma", false, 2, new BigDecimal("20667"));
        l.acumular(Naturaleza.ENTRADA, "Ajuste positivo", false, 1, new BigDecimal("10333"));
        l.acumular(Naturaleza.TRANSFERENCIA, "Transferencia", false, 5, new BigDecimal("50000"));  // no cuenta
        assertEquals(20, l.getSaldoInicial());
        assertEquals(10, l.getEntradas());
        assertEquals(6, l.getSalidas());
        assertEquals(2, l.getPerdidas());
        assertEquals(1, l.getSobrantes());
        assertEquals(23, l.saldoFinal(), "20 + 10 + 1 - 6 - 2");
        assertEquals(0, new BigDecimal("237666").compareTo(l.valorFinal()), "200.000 + 110.000 + 10.333 - 62.000 - 20.667");
        assertFalse(l.sinMovimiento());
    }

    @Test
    void lasFormulasDeLosIndicadores() {
        // Salió mercancía por 600.000 con un inventario promedio de (900.000 + 1.100.000) / 2 = 1.000.000
        BigDecimal rotacion = Indicador.rotacion(new BigDecimal("600000"), new BigDecimal("900000"), new BigDecimal("1100000"));
        assertEquals(0, new BigDecimal("0.60").compareTo(rotacion));
        assertEquals(50, Indicador.diasDeInventario(30, rotacion), "30 días / 0,6 = 50 días");
        assertEquals(0, Indicador.diasDeInventario(30, BigDecimal.ZERO), "Sin salidas no se puede calcular");
        assertEquals(0, new BigDecimal("90.0").compareTo(Indicador.porcentaje(BigDecimal.valueOf(18), BigDecimal.valueOf(20))));
        assertEquals(0, BigDecimal.ZERO.compareTo(Indicador.porcentaje(BigDecimal.TEN, BigDecimal.ZERO)), "No divide entre cero");
        // Exactitud: meta 95 %, alerta desde 85 %
        assertEquals(Estado.BUENO, Indicador.estadoMayorMejor(new BigDecimal("95"), 95, 85));
        assertEquals(Estado.ALERTA, Indicador.estadoMayorMejor(new BigDecimal("90"), 95, 85));
        assertEquals(Estado.CRITICO, Indicador.estadoMayorMejor(new BigDecimal("33.3"), 95, 85));
        // Pérdidas: meta 2 %, alerta hasta 5 %
        assertEquals(Estado.BUENO, Indicador.estadoMenorMejor(new BigDecimal("2"), 2, 5));
        assertEquals(Estado.ALERTA, Indicador.estadoMenorMejor(new BigDecimal("4.5"), 2, 5));
        assertEquals(Estado.CRITICO, Indicador.estadoMenorMejor(new BigDecimal("5.1"), 2, 5));
    }

    @Test
    void elControlDelPeriodoCuadraConLasExistencias() throws Exception {
        login.iniciarSesion("supervisor", CLAVE);
        LocalDate hoy = LocalDate.now();
        List<LineaControl> lineas = new IndicadorControlador().controlPeriodo(hoy.withDayOfMonth(1), hoy);
        assertFalse(lineas.isEmpty());
        // La suma de los saldos finales es todo lo que hay hoy en las bodegas
        int saldos = lineas.stream().mapToInt(LineaControl::saldoFinal).sum();
        int existencias = new ReporteControlador().consultarExistencias("", null, null, false).stream()
                .mapToInt(e -> e.getCantidad()).sum();
        assertEquals(existencias, saldos, "El control se arma con los mismos movimientos que cambian el stock");

        // Antes de cualquier movimiento no había nada: el saldo inicial y el final son cero
        List<LineaControl> antes = new ReporteDAO().controlPeriodo(LocalDate.of(2000, 1, 1), LocalDate.of(2000, 1, 31));
        assertTrue(antes.stream().allMatch(l -> l.getSaldoInicial() == 0 && l.saldoFinal() == 0));

        login.iniciarSesion("compras", CLAVE);
        assertThrows(NegocioException.class, () -> new IndicadorControlador().controlPeriodo(hoy.withDayOfMonth(1), hoy),
                "El control muestra pérdidas: solo Supervisor y Administrador");
    }

    @Test
    void elTableroTieneNueveIndicadoresYLoVenLosRolesConReportes() {
        LocalDate hoy = LocalDate.now();
        login.iniciarSesion("compras", CLAVE);
        List<Indicador> tablero = new IndicadorControlador().indicadores(hoy.withDayOfMonth(1), hoy);
        assertEquals(9, tablero.size());
        assertEquals("Valor del inventario", tablero.get(0).getNombre());
        assertTrue(tablero.get(0).getValor().startsWith("$"));
        assertTrue(tablero.stream().allMatch(i -> i.getEstado() != null && !i.getFormula().isBlank()));
        assertThrows(NegocioException.class, () -> new IndicadorControlador().indicadores(hoy, hoy.minusDays(1)));
        assertThrows(NegocioException.class, () -> new IndicadorControlador().indicadores(null, hoy));

        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> new IndicadorControlador().indicadores(hoy.withDayOfMonth(1), hoy),
                "El Almacenista no tiene reportes");
    }
}
