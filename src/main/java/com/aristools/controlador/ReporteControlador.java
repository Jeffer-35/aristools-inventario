package com.aristools.controlador;

import com.aristools.dao.ExistenciaDAO;
import com.aristools.dao.MovimientoDAO;
import com.aristools.dao.ReporteDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.EstadoMovimiento;
import com.aristools.modelo.Existencia;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Permisos;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Consulta de existencias (RF-013), kardex y reportes de movimientos (RF-014).
 * Cada rol ve solo los movimientos de su alcance: el Jefe de Compras las
 * entradas y el Supervisor de Bodega mermas, daños, devoluciones y ajustes.
 */
public class ReporteControlador {

    private static final Set<String> ALCANCE_JEFE = Set.of("Compra", "Devolución");
    private static final Set<String> ALCANCE_SUPERVISOR = Set.of("Merma", "Producto dañado o vencido",
            "Devolución a proveedor", "Ajuste positivo", "Ajuste negativo");

    private final ExistenciaDAO existenciaDAO = new ExistenciaDAO();
    private final ReporteDAO reporteDAO = new ReporteDAO();
    private final TipoMovimientoDAO tipoDAO = new TipoMovimientoDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();

    /**
     * Tipos de movimiento para el filtro de reportes, recortados al alcance
     * del rol: el Jefe de Compras solo ve compras y devoluciones; el Supervisor,
     * mermas, daños, devoluciones a proveedor y ajustes; el Administrador, todos.
     */
    public List<TipoMovimiento> listarTiposReporte() {
        Sesion.exigir(Accion.VER_REPORTES);
        try {
            Set<String> alcance = alcance();
            List<TipoMovimiento> tipos = new ArrayList<>();
            for (Naturaleza n : Naturaleza.values()) {
                for (TipoMovimiento t : tipoDAO.listarPorNaturaleza(n)) {
                    if (alcance == null || alcance.contains(t.getNombre())) {
                        tipos.add(t);
                    }
                }
            }
            return tipos;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Consulta de solo lectura con filtros opcionales (null = todos). */
    public List<Existencia> consultarExistencias(String filtro, Integer idCategoria, Integer idBodega, boolean inactivos) {
        Sesion.exigir(Accion.CONSULTAR_EXISTENCIAS);
        try {
            return existenciaDAO.consultar(filtro, idCategoria, idBodega, inactivos);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Kardex cronológico; el saldo siempre se calcula con todos los movimientos. */
    public List<LineaKardex> generarKardex(int idProducto, Integer idBodega, LocalDate desde, LocalDate hasta) {
        Sesion.exigir(Accion.VER_REPORTES);
        validarFechas(desde, hasta);
        try {
            Set<String> alcance = alcance();
            List<LineaKardex> lineas = reporteDAO.kardex(idProducto, idBodega, desde, hasta);
            if (alcance == null) {
                return lineas;
            }
            return lineas.stream()
                    .filter(l -> "Saldo inicial".equals(l.getTipo()) || alcance.contains(l.getTipo()))
                    .collect(Collectors.toList());
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Reporte de movimientos con filtros, recortado al alcance del rol. */
    public List<Movimiento> reporteMovimientos(FiltroMovimiento f) {
        Sesion.exigir(Accion.VER_REPORTES);
        validarFechas(f.getDesde(), f.getHasta());
        try {
            Set<String> alcance = alcance();
            List<Movimiento> lista = reporteDAO.movimientos(f);
            if (alcance != null) {
                lista.removeIf(m -> !alcance.contains(m.getTipo().getNombre()));
            }
            return lista;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Reporte de pérdidas del período: mermas, daños, vencidos y faltantes
     * ya autorizados, con su valor en pesos. Solo para el Supervisor de
     * Bodega y el Administrador.
     */
    public List<Movimiento> reportePerdidas(LocalDate desde, LocalDate hasta, Integer idBodega) {
        Sesion.exigir(Accion.VER_PERDIDAS);
        validarFechas(desde, hasta);
        try {
            return reporteDAO.perdidas(desde, hasta, idBodega);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Movimiento completo para imprimir su documento de soporte. El
     * documento solo existe cuando el movimiento ya está autorizado, y cada
     * rol solo puede imprimir los movimientos de su alcance.
     */
    public Movimiento movimientoParaDocumento(int idMovimiento) {
        Usuario u = Sesion.getUsuario();
        if (u == null || !(Permisos.puede(u.getRol(), Accion.VER_REPORTES)
                || Permisos.puede(u.getRol(), Accion.AUTORIZAR_ENTRADAS)
                || Permisos.puede(u.getRol(), Accion.AUTORIZAR_SALIDAS))) {
            throw new NegocioException("No tiene permiso para imprimir documentos de soporte.");
        }
        try {
            Movimiento m = movimientoDAO.buscarPorId(idMovimiento);
            if (m == null) {
                throw new NegocioException("El movimiento " + idMovimiento + " no existe.");
            }
            Set<String> alcance = alcance();
            if (alcance != null && !alcance.contains(m.getTipo().getNombre())) {
                throw new NegocioException("Ese movimiento no está dentro del alcance de su rol.");
            }
            if (m.getEstado() != EstadoMovimiento.AUTORIZADO) {
                throw new NegocioException("El documento de soporte se genera cuando el movimiento está autorizado. "
                        + "Este movimiento está " + m.getEstado() + ".");
            }
            return m;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    // null significa "sin restricción" (Administrador).
    private Set<String> alcance() {
        Usuario u = Sesion.getUsuario();
        String rol = u.getRol().getNombreRol();
        if ("Jefe de Compras".equals(rol)) {
            return ALCANCE_JEFE;
        }
        if ("Supervisor de Bodega".equals(rol)) {
            return ALCANCE_SUPERVISOR;
        }
        return null;
    }

    private void validarFechas(LocalDate desde, LocalDate hasta) {
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException("La fecha inicial no puede ser posterior a la fecha final.");
        }
    }
}
