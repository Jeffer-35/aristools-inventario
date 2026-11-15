package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.OrdenCompra;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Datos para la sugerencia de compra y registro de las órdenes de compra.
 * Las órdenes se guardan con sus productos en una sola transacción.
 */
public class CompraDAO {

    /** Datos de un producto activo que necesita la sugerencia de compra. */
    public static class DatosProducto {
        public int idProducto;
        public int stock;              // sin contar la mercancía en cuarentena
        public int salidas;            // unidades que salieron en el período
        public Integer idProveedor;    // proveedor de la última compra; null si nunca se ha comprado
        public BigDecimal ultimoCosto; // costo de la última compra; null si nunca se ha comprado
    }

    /**
     * Por cada producto activo: stock disponible, unidades que salieron desde
     * la fecha indicada y proveedor y costo de su última compra.
     *
     * Cuentan como salidas los movimientos AUTORIZADOS de naturaleza SALIDA
     * (consumo interno, merma, dañado o vencido y ajuste negativo), menos la
     * devolución a proveedor, que no es consumo. Las transferencias no
     * cuentan porque solo cambian la mercancía de bodega.
     */
    public List<DatosProducto> datosSugerencia(LocalDateTime desde) throws SQLException {
        String ultimaCompra = "FROM movimiento_inventario mc JOIN tipo_movimiento tc "
                + "ON tc.id_tipo_movimiento = mc.id_tipo_movimiento "
                + "WHERE mc.id_producto = p.id_producto AND tc.nombre = 'Compra' AND mc.estado = 'AUTORIZADO' "
                + "ORDER BY COALESCE(mc.fecha_autorizacion, mc.fecha) DESC, mc.id_movimiento DESC LIMIT 1";
        String sql = "SELECT p.id_producto, "
                + "COALESCE((SELECT SUM(e.cantidad) FROM existencia e JOIN bodega b ON b.id_bodega = e.id_bodega "
                + "          WHERE e.id_producto = p.id_producto AND b.es_cuarentena = FALSE), 0) AS stock, "
                + "COALESCE((SELECT SUM(m.cantidad) FROM movimiento_inventario m JOIN tipo_movimiento t "
                + "          ON t.id_tipo_movimiento = m.id_tipo_movimiento "
                + "          WHERE m.id_producto = p.id_producto AND m.estado = 'AUTORIZADO' "
                + "          AND t.naturaleza = 'SALIDA' AND t.nombre <> 'Devolución a proveedor' "
                + "          AND COALESCE(m.fecha_autorizacion, m.fecha) >= ?), 0) AS salidas, "
                + "(SELECT mc.id_proveedor " + ultimaCompra + ") AS id_proveedor, "
                + "(SELECT mc.costo_unitario " + ultimaCompra + ") AS ultimo_costo "
                + "FROM producto p WHERE p.estado = 'ACTIVO' ORDER BY p.codigo";
        List<DatosProducto> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatosProducto d = new DatosProducto();
                    d.idProducto = rs.getInt("id_producto");
                    d.stock = rs.getInt("stock");
                    d.salidas = rs.getInt("salidas");
                    d.idProveedor = rs.getObject("id_proveedor", Integer.class);
                    d.ultimoCosto = rs.getBigDecimal("ultimo_costo");
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    /**
     * Guarda la orden con todos sus productos en una transacción: se guarda
     * todo o nada. Devuelve el id, con el que se arma el número OC-.
     */
    public int insertar(OrdenCompra o) throws SQLException {
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO orden_compra (id_proveedor, id_usuario, fecha, total, observacion) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, o.getProveedor().getIdProveedor());
                    ps.setInt(2, o.getUsuario().getIdUsuario());
                    ps.setTimestamp(3, Timestamp.valueOf(o.getFecha()));
                    ps.setBigDecimal(4, o.total());
                    ps.setString(5, o.getObservacion());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO orden_compra_detalle (id_orden, id_producto, cantidad, costo_unitario) VALUES (?, ?, ?, ?)")) {
                    for (LineaOrdenCompra l : o.getLineas()) {
                        ps.setInt(1, id);
                        ps.setInt(2, l.getProducto().getIdProducto());
                        ps.setInt(3, l.getCantidad());
                        ps.setBigDecimal(4, l.getCostoUnitario());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                con.commit();
                o.setIdOrden(id);
                return id;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /** Valor total guardado de una orden (lo usan las pruebas para comprobar el registro). */
    public BigDecimal totalOrden(int idOrden) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT total FROM orden_compra WHERE id_orden = ?")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }
}
