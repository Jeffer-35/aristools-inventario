package com.aristools.controlador;

import com.aristools.dao.AlertaDAO;
import com.aristools.dao.ConteoDAO;
import com.aristools.dao.MovimientoDAO;
import com.aristools.dao.ReporteDAO;
import com.aristools.modelo.Indicador;
import com.aristools.modelo.Indicador.Estado;
import com.aristools.modelo.LineaControl;
import com.aristools.modelo.LoteVencimiento;
import com.aristools.modelo.TipoAlerta;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Control del período y tablero de indicadores (KPI).
 *
 * El control del período dice, producto por producto, con cuánto se empezó,
 * qué entró, qué salió, qué se perdió, qué sobró y con cuánto se terminó.
 * El tablero resume eso mismo en nueve indicadores con semáforo.
 */
public class IndicadorControlador {

    private final ReporteDAO reporteDAO = new ReporteDAO();
    private final ConteoDAO conteoDAO = new ConteoDAO();
    private final AlertaDAO alertaDAO = new AlertaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();

    /**
     * Control de inventario del período (pérdidas y sobrantes incluidos).
     * Solo para el Supervisor de Bodega y el Administrador, igual que el
     * reporte de pérdidas. No trae los productos sin existencias ni movimientos.
     */
    public List<LineaControl> controlPeriodo(LocalDate desde, LocalDate hasta) {
        Sesion.exigir(Accion.VER_PERDIDAS);
        validar(desde, hasta);
        try {
            List<LineaControl> lineas = reporteDAO.controlPeriodo(desde, hasta);
            lineas.removeIf(LineaControl::vacia);
            return lineas;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Los nueve indicadores del tablero para el período indicado. */
    public List<Indicador> indicadores(LocalDate desde, LocalDate hasta) {
        Sesion.exigir(Accion.VER_REPORTES);
        validar(desde, hasta);
        try {
            List<LineaControl> lineas = reporteDAO.controlPeriodo(desde, hasta);
            BigDecimal valorInicial = BigDecimal.ZERO;
            BigDecimal valorFinal = BigDecimal.ZERO;
            BigDecimal costoSalidas = BigDecimal.ZERO;   // todo lo que salió, pérdidas incluidas
            BigDecimal valorPerdidas = BigDecimal.ZERO;
            int conExistencia = 0;
            int sinMovimiento = 0;
            for (LineaControl l : lineas) {
                valorInicial = valorInicial.add(l.getValorInicial());
                valorFinal = valorFinal.add(l.valorFinal());
                costoSalidas = costoSalidas.add(l.getValorSalidas()).add(l.getValorPerdidas());
                valorPerdidas = valorPerdidas.add(l.getValorPerdidas());
                if (l.saldoFinal() > 0) {
                    conExistencia++;
                    if (l.sinMovimiento()) {
                        sinMovimiento++;
                    }
                }
            }
            long dias = ChronoUnit.DAYS.between(desde, hasta) + 1;
            BigDecimal rotacion = Indicador.rotacion(costoSalidas, valorInicial, valorFinal);
            long diasInventario = Indicador.diasDeInventario(dias, rotacion);
            int[] conteos = conteoDAO.totales(desde, hasta);
            int activos = lineas.size();
            int bajoStock = alertaDAO.contarActivas(TipoAlerta.BAJO_STOCK);
            int pendientes = movimientoDAO.contarPendientes();
            int vencidos = 0;
            LocalDate hoy = LocalDate.now();
            for (LoteVencimiento l : reporteDAO.lotesConSaldo()) {
                if (l.diasParaVencer(hoy) < 0) {
                    vencidos++;
                }
            }

            List<Indicador> lista = new ArrayList<>();
            lista.add(new Indicador("Valor del inventario", Formato.pesos(reporteDAO.valorInventario()), "Dato de hoy",
                    Estado.INFORMATIVO, "Existencias x costo promedio de cada producto"));
            lista.add(new Indicador("Rotación del inventario", decimal(rotacion) + " veces", "Entre más alta, mejor",
                    Estado.INFORMATIVO, "Costo de lo que salió ÷ inventario promedio del período"));
            lista.add(new Indicador("Días de inventario", rotacion.signum() == 0 ? "Sin salidas" : diasInventario + " días",
                    "Lo que duraría el inventario", Estado.INFORMATIVO, "Días del período ÷ rotación"));

            BigDecimal exactitud = Indicador.porcentaje(BigDecimal.valueOf(conteos[0] - conteos[1]), BigDecimal.valueOf(conteos[0]));
            lista.add(conteos[2] == 0
                    ? new Indicador("Exactitud del inventario", "Sin conteos", "Meta: 95 % o más", Estado.ALERTA,
                            "No se hizo ningún conteo físico en el período")
                    : new Indicador("Exactitud del inventario", decimal(exactitud) + " %", "Meta: 95 % o más",
                            Indicador.estadoMayorMejor(exactitud, 95, 85),
                            "Productos que coincidieron ÷ productos contados (" + conteos[2] + " conteo(s))"));

            BigDecimal perdidas = Indicador.porcentaje(valorPerdidas, costoSalidas);
            lista.add(new Indicador("Pérdidas", decimal(perdidas) + " %  (" + Formato.pesos(valorPerdidas) + ")",
                    "Meta: 2 % o menos", Indicador.estadoMenorMejor(perdidas, 2, 5),
                    "Valor de mermas, daños y faltantes ÷ valor de todo lo que salió"));

            BigDecimal bajo = Indicador.porcentaje(BigDecimal.valueOf(bajoStock), BigDecimal.valueOf(activos));
            lista.add(new Indicador("Productos en bajo stock", bajoStock + " de " + activos + "  (" + decimal(bajo) + " %)",
                    "Meta: 10 % o menos", Indicador.estadoMenorMejor(bajo, 10, 25),
                    "Productos con alerta de bajo stock ÷ productos activos (dato de hoy)"));

            BigDecimal quietos = Indicador.porcentaje(BigDecimal.valueOf(sinMovimiento), BigDecimal.valueOf(conExistencia));
            lista.add(new Indicador("Productos sin movimiento", sinMovimiento + " de " + conExistencia + "  (" + decimal(quietos) + " %)",
                    "Meta: 20 % o menos", Indicador.estadoMenorMejor(quietos, 20, 40),
                    "Productos con existencias que no tuvieron entradas ni salidas en el período"));

            lista.add(new Indicador("Lotes vencidos en bodega", String.valueOf(vencidos), "Meta: 0",
                    vencidos == 0 ? Estado.BUENO : Estado.CRITICO, "Lotes con fecha de vencimiento pasada y unidades en bodega (dato de hoy)"));
            lista.add(new Indicador("Movimientos por autorizar", String.valueOf(pendientes), "Meta: 0",
                    Indicador.estadoMenorMejor(BigDecimal.valueOf(pendientes), 0, 5),
                    "Entradas y salidas que esperan al Jefe de Compras o al Supervisor (dato de hoy)"));
            return lista;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    private static String decimal(BigDecimal n) {
        return n.stripTrailingZeros().scale() <= 0 ? n.setScale(0).toPlainString() : n.toPlainString().replace('.', ',');
    }

    private void validar(LocalDate desde, LocalDate hasta) {
        if (desde == null || hasta == null) {
            throw new NegocioException("Elija la fecha inicial y la fecha final del período.");
        }
        if (desde.isAfter(hasta)) {
            throw new NegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }
}
