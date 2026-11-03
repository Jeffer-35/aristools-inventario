package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Ajuste;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Salida;
import com.aristools.modelo.Transferencia;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** Consultas de kardex y reportes de movimientos (RF-014). Solo lectura. */
public class ReporteDAO {

    /**
     * Kardex cronológico de un producto. Cuenta solo movimientos AUTORIZADOS y
     * aplica Saldo = saldo anterior + entradas − salidas. Si hay fecha inicial,
     * la primera línea es el "Saldo inicial" acumulado antes de esa fecha.
     *
     * @param idBodega null para todas las bodegas
     * @param desde    null para desde el inicio
     * @param hasta    null para hasta hoy
     */
    public List<LineaKardex> kardex(int idProducto, Integer idBodega, LocalDate desde, LocalDate hasta)
            throws SQLException {
        String sql = MovimientoDAO.SELECT_BASE
                   + "WHERE m.id_producto = ? AND m.estado = 'AUTORIZADO' "
                   + "AND (? IS NULL OR m.id_bodega_origen = ? OR m.id_bodega_destino = ?) "
                   + "ORDER BY COALESCE(m.fecha_autorizacion, m.fecha), m.id_movimiento";
        LocalDateTime inicio = desde == null ? null : desde.atStartOfDay();
        LocalDateTime fin = hasta == null ? null : hasta.plusDays(1).atStartOfDay();
        List<LineaKardex> lineas = new ArrayList<>();
        int saldo = 0;
        boolean saldoInicialAgregado = false;
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idProducto);
            ps.setObject(2, idBodega, Types.INTEGER);
            ps.setObject(3, idBodega, Types.INTEGER);
            ps.setObject(4, idBodega, Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Movimiento m = MovimientoDAO.mapear(rs);
                    LocalDateTime cuando = m.getFechaAutorizacion() != null ? m.getFechaAutorizacion() : m.getFecha();
                    int[] es = entradaSalida(m, idBodega);
                    if (inicio != null && cuando.isBefore(inicio)) {
                        saldo += es[0] - es[1];          // acumula el saldo inicial
                        continue;
                    }
                    if (fin != null && !cuando.isBefore(fin)) {
                        break;                           // ya pasó la fecha final
                    }
                    if (inicio != null && !saldoInicialAgregado) {
                        lineas.add(new LineaKardex(inicio, "Saldo inicial", null, null, 0, 0, saldo, null, null));
                        saldoInicialAgregado = true;
                    }
                    saldo += es[0] - es[1];
                    lineas.add(new LineaKardex(cuando, m.getTipo().getNombre(), m.getDocumentoSoporte(),
                            nombreBodega(m, idBodega), es[0], es[1], saldo,
                            m.getUsuarioRegistra().getNombreUsuario(),
                            m.getUsuarioAutoriza() == null ? null : m.getUsuarioAutoriza().getNombreUsuario()));
                }
            }
        }
        if (inicio != null && !saldoInicialAgregado) {
            lineas.add(0, new LineaKardex(inicio, "Saldo inicial", null, null, 0, 0, saldo, null, null));
        }
        return lineas;
    }

    /** Reporte de movimientos con filtros opcionales (los null significan "todos"). */
    public List<Movimiento> movimientos(FiltroMovimiento f) throws SQLException {
        String sql = MovimientoDAO.SELECT_BASE
                   + "WHERE (? IS NULL OR m.id_producto = ?) "
                   + "AND (? IS NULL OR m.id_bodega_origen = ? OR m.id_bodega_destino = ?) "
                   + "AND (? IS NULL OR m.id_proveedor = ?) AND (? IS NULL OR m.id_tipo_movimiento = ?) "
                   + "AND (? IS NULL OR m.fecha >= ?) AND (? IS NULL OR m.fecha < ?) "
                   + "ORDER BY m.fecha DESC, m.id_movimiento DESC";
        Timestamp desde = f.getDesde() == null ? null : Timestamp.valueOf(f.getDesde().atStartOfDay());
        Timestamp hasta = f.getHasta() == null ? null : Timestamp.valueOf(f.getHasta().plusDays(1).atStartOfDay());
        List<Movimiento> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            int i = 1;
            ps.setObject(i++, f.getIdProducto(), Types.INTEGER);
            ps.setObject(i++, f.getIdProducto(), Types.INTEGER);
            ps.setObject(i++, f.getIdBodega(), Types.INTEGER);
            ps.setObject(i++, f.getIdBodega(), Types.INTEGER);
            ps.setObject(i++, f.getIdBodega(), Types.INTEGER);
            ps.setObject(i++, f.getIdProveedor(), Types.INTEGER);
            ps.setObject(i++, f.getIdProveedor(), Types.INTEGER);
            ps.setObject(i++, f.getIdTipoMovimiento(), Types.INTEGER);
            ps.setObject(i++, f.getIdTipoMovimiento(), Types.INTEGER);
            ps.setTimestamp(i++, desde);
            ps.setTimestamp(i++, desde);
            ps.setTimestamp(i++, hasta);
            ps.setTimestamp(i, hasta);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(MovimientoDAO.mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Unidades que entran y salen según la bodega consultada.
     * Sin filtro de bodega, una transferencia cuenta como entrada y salida
     * iguales, porque no cambia el stock total.
     */
    private int[] entradaSalida(Movimiento m, Integer idBodega) {
        int c = m.getCantidad();
        if (m instanceof Transferencia t) {
            if (idBodega == null) {
                return new int[] {c, c};
            }
            return t.getBodegaDestino().getIdBodega() == idBodega ? new int[] {c, 0} : new int[] {0, c};
        }
        boolean entra = m.getTipo().getNaturaleza() == Naturaleza.ENTRADA;
        return entra ? new int[] {c, 0} : new int[] {0, c};
    }

    // Nombre de la bodega que se muestra en la columna "Bodega" del kardex.
    private String nombreBodega(Movimiento m, Integer idBodega) {
        Bodega b = null;
        if (m instanceof Entrada e) {
            b = e.getBodegaDestino();
        } else if (m instanceof Salida s) {
            b = s.getBodegaOrigen();
        } else if (m instanceof Ajuste a) {
            b = a.getBodega();
        } else if (m instanceof Transferencia t) {
            if (idBodega == null) {
                return t.getBodegaOrigen().getNombre() + " → " + t.getBodegaDestino().getNombre();
            }
            b = t.getBodegaDestino().getIdBodega() == idBodega ? t.getBodegaDestino() : t.getBodegaOrigen();
        }
        return b == null ? null : b.getNombre();
    }
}
