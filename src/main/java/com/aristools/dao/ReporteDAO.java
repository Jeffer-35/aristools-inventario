package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Ajuste;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.ClasificacionAbc;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.LoteVencimiento;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Salida;
import com.aristools.modelo.Transferencia;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Consultas de kardex y reportes de movimientos (RF-014). Solo lectura. */
public class ReporteDAO {

    /**
     * Kardex cronológico de un producto. Cuenta solo movimientos AUTORIZADOS y
     * aplica Saldo = saldo anterior + entradas − salidas. Si hay fecha inicial,
     * la primera línea es el "Saldo inicial" acumulado antes de esa fecha.
     *
     * Kardex valorizado: cada línea lleva también sus valores en pesos
     * (cantidad x costo unitario del movimiento) y el valor del saldo.
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
        BigDecimal valorSaldo = BigDecimal.ZERO;
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
                    BigDecimal costo = m.getCostoUnitario() == null ? BigDecimal.ZERO : m.getCostoUnitario();
                    BigDecimal valorEntrada = valor(es[0], costo);
                    BigDecimal valorSalida = valor(es[1], costo);
                    if (inicio != null && cuando.isBefore(inicio)) {
                        saldo += es[0] - es[1];          // acumula el saldo inicial
                        valorSaldo = valorDelSaldo(saldo, valorSaldo.add(valorEntrada).subtract(valorSalida));
                        continue;
                    }
                    if (fin != null && !cuando.isBefore(fin)) {
                        break;                           // ya pasó la fecha final
                    }
                    if (inicio != null && !saldoInicialAgregado) {
                        lineas.add(saldoInicial(inicio, saldo, valorSaldo));
                        saldoInicialAgregado = true;
                    }
                    saldo += es[0] - es[1];
                    valorSaldo = valorDelSaldo(saldo, valorSaldo.add(valorEntrada).subtract(valorSalida));
                    LineaKardex linea = new LineaKardex(cuando, m.getTipo().getNombre(), m.getDocumentoSoporte(),
                            nombreBodega(m, idBodega), es[0], es[1], saldo,
                            m.getUsuarioRegistra().getNombreUsuario(),
                            m.getUsuarioAutoriza() == null ? null : m.getUsuarioAutoriza().getNombreUsuario());
                    linea.setValores(costo, valorEntrada, valorSalida, valorSaldo);
                    lineas.add(linea);
                }
            }
        }
        if (inicio != null && !saldoInicialAgregado) {
            lineas.add(0, saldoInicial(inicio, saldo, valorSaldo));
        }
        return lineas;
    }

    // Valor en pesos de unas unidades: cantidad x costo unitario, con dos decimales.
    private static BigDecimal valor(int unidades, BigDecimal costoUnitario) {
        return costoUnitario.multiply(BigDecimal.valueOf(unidades)).setScale(2, RoundingMode.HALF_UP);
    }

    // Sin unidades no puede quedar valor: evita que se acumulen centavos por los redondeos.
    private static BigDecimal valorDelSaldo(int saldo, BigDecimal valorCalculado) {
        return saldo <= 0 || valorCalculado.signum() < 0 ? BigDecimal.ZERO.setScale(2) : valorCalculado;
    }

    // Primera línea del kardex cuando se consulta desde una fecha.
    private static LineaKardex saldoInicial(LocalDateTime inicio, int saldo, BigDecimal valorSaldo) {
        LineaKardex linea = new LineaKardex(inicio, "Saldo inicial", null, null, 0, 0, saldo, null, null);
        linea.setValores(null, BigDecimal.ZERO, BigDecimal.ZERO, valorSaldo);
        return linea;
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
     * Pérdidas de mercancía de un período: mermas, productos dañados o
     * vencidos y faltantes del conteo (ajustes negativos), solo AUTORIZADOS.
     * La fecha que cuenta es la de autorización, que es cuando el stock bajó.
     * Cada movimiento trae su costo unitario, así se sabe cuánto dinero se perdió.
     *
     * @param idBodega null para todas las bodegas
     */
    public List<Movimiento> perdidas(LocalDate desde, LocalDate hasta, Integer idBodega) throws SQLException {
        String sql = MovimientoDAO.SELECT_BASE
                   + "WHERE m.estado = 'AUTORIZADO' "
                   + "AND t.nombre IN ('Merma', 'Producto dañado o vencido', 'Ajuste negativo') "
                   + "AND (? IS NULL OR m.id_bodega_origen = ?) "
                   + "AND (? IS NULL OR COALESCE(m.fecha_autorizacion, m.fecha) >= ?) "
                   + "AND (? IS NULL OR COALESCE(m.fecha_autorizacion, m.fecha) < ?) "
                   + "ORDER BY COALESCE(m.fecha_autorizacion, m.fecha), m.id_movimiento";
        Timestamp inicio = desde == null ? null : Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin = hasta == null ? null : Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
        List<Movimiento> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setObject(1, idBodega, Types.INTEGER);
            ps.setObject(2, idBodega, Types.INTEGER);
            ps.setTimestamp(3, inicio);
            ps.setTimestamp(4, inicio);
            ps.setTimestamp(5, fin);
            ps.setTimestamp(6, fin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(MovimientoDAO.mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Consumo valorizado de cada producto activo en un período: unidades que
     * salieron (consumos, mermas, daños, devoluciones a proveedor y ajustes
     * negativos ya AUTORIZADOS) y su valor al costo con que se valoraron.
     * Los productos sin salidas aparecen con cero. Es la base de la
     * clasificación ABC.
     */
    public List<ClasificacionAbc> consumoPorProducto(LocalDate desde, LocalDate hasta) throws SQLException {
        String sql = "SELECT p.id_producto, p.codigo, p.nombre, p.unidad_medida, "
                   + "COALESCE(SUM(m.cantidad), 0) AS unidades, "
                   + "COALESCE(SUM(m.cantidad * COALESCE(m.costo_unitario, p.costo_promedio)), 0) AS valor "
                   + "FROM producto p "
                   + "LEFT JOIN movimiento_inventario m ON m.id_producto = p.id_producto "
                   + "AND m.estado = 'AUTORIZADO' "
                   + "AND m.id_tipo_movimiento IN (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE naturaleza = 'SALIDA') "
                   + "AND (? IS NULL OR COALESCE(m.fecha_autorizacion, m.fecha) >= ?) "
                   + "AND (? IS NULL OR COALESCE(m.fecha_autorizacion, m.fecha) < ?) "
                   + "WHERE p.estado = 'ACTIVO' "
                   + "GROUP BY p.id_producto, p.codigo, p.nombre, p.unidad_medida";
        Timestamp inicio = desde == null ? null : Timestamp.valueOf(desde.atStartOfDay());
        Timestamp fin = hasta == null ? null : Timestamp.valueOf(hasta.plusDays(1).atStartOfDay());
        List<ClasificacionAbc> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, inicio);
            ps.setTimestamp(2, inicio);
            ps.setTimestamp(3, fin);
            ps.setTimestamp(4, fin);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Producto p = new Producto();
                    p.setIdProducto(rs.getInt("id_producto"));
                    p.setCodigo(rs.getString("codigo"));
                    p.setNombre(rs.getString("nombre"));
                    p.setUnidadMedida(rs.getString("unidad_medida"));
                    lista.add(new ClasificacionAbc(p, rs.getInt("unidades"), rs.getBigDecimal("valor")));
                }
            }
        }
        return lista;
    }

    /**
     * Lotes con fecha de vencimiento (compras AUTORIZADAS de productos que
     * vencen), con el saldo estimado de cada uno según la regla FEFO.
     * Solo devuelve los lotes que todavía tienen unidades en bodega,
     * ordenados por fecha de vencimiento: el primero es el que debe salir primero.
     */
    public List<LoteVencimiento> lotesConSaldo() throws SQLException {
        String sql = "SELECT p.id_producto, p.codigo, p.nombre, p.unidad_medida, m.lote, m.fecha_vencimiento, "
                   + "m.cantidad, pv.razon_social, b.nombre AS bodega, "
                   + "(SELECT COALESCE(SUM(e.cantidad), 0) FROM existencia e WHERE e.id_producto = p.id_producto) AS stock "
                   + "FROM movimiento_inventario m "
                   + "JOIN producto p ON p.id_producto = m.id_producto "
                   + "LEFT JOIN proveedor pv ON pv.id_proveedor = m.id_proveedor "
                   + "LEFT JOIN bodega b ON b.id_bodega = m.id_bodega_destino "
                   + "WHERE m.estado = 'AUTORIZADO' AND m.fecha_vencimiento IS NOT NULL AND p.estado = 'ACTIVO' "
                   + "ORDER BY p.id_producto, m.fecha_vencimiento";
        Map<Integer, List<LoteVencimiento>> porProducto = new LinkedHashMap<>();
        Map<Integer, Integer> stock = new LinkedHashMap<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Producto p = new Producto();
                p.setIdProducto(rs.getInt("id_producto"));
                p.setCodigo(rs.getString("codigo"));
                p.setNombre(rs.getString("nombre"));
                p.setUnidadMedida(rs.getString("unidad_medida"));
                p.setControlaVencimiento(true);
                porProducto.computeIfAbsent(p.getIdProducto(), k -> new ArrayList<>()).add(new LoteVencimiento(p,
                        rs.getString("lote"), rs.getDate("fecha_vencimiento").toLocalDate(), rs.getInt("cantidad"),
                        rs.getString("razon_social"), rs.getString("bodega")));
                stock.put(p.getIdProducto(), rs.getInt("stock"));
            }
        }
        List<LoteVencimiento> lista = new ArrayList<>();
        for (Map.Entry<Integer, List<LoteVencimiento>> e : porProducto.entrySet()) {
            for (LoteVencimiento l : LoteVencimiento.asignarSaldos(e.getValue(), stock.get(e.getKey()))) {
                if (l.getSaldoEstimado() > 0) {
                    lista.add(l);
                }
            }
        }
        lista.sort(java.util.Comparator.comparing(LoteVencimiento::getFechaVencimiento));
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
