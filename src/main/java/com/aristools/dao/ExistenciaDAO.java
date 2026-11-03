package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Existencia;
import com.aristools.modelo.Producto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla existencia: cantidad de cada producto en cada bodega.
 * Los métodos que reciben Connection trabajan dentro de la transacción
 * que abre el controlador (RNF-003).
 */
public class ExistenciaDAO {

    private static final String COLUMNAS =
            "p.id_producto, p.codigo, p.nombre, p.descripcion, p.unidad_medida, p.costo_referencia, "
          + "p.stock_minimo, p.stock_maximo, p.estado, c.id_categoria, c.nombre AS cat_nombre, "
          + "b.id_bodega, b.codigo AS bod_codigo, b.nombre AS bod_nombre, b.ubicacion, b.estado AS bod_estado ";

    /**
     * Consulta de existencias de solo lectura (RF-013).
     * Los parámetros null o vacíos significan "todos".
     */
    public List<Existencia> consultar(String filtro, Integer idCategoria, Integer idBodega, boolean inactivos)
            throws SQLException {
        String sql = "SELECT " + COLUMNAS + ", e.cantidad "
                   + "FROM existencia e JOIN producto p ON p.id_producto = e.id_producto "
                   + "JOIN categoria c ON c.id_categoria = p.id_categoria JOIN bodega b ON b.id_bodega = e.id_bodega "
                   + "WHERE (? = '' OR p.codigo LIKE ? OR p.nombre LIKE ?) "
                   + "AND (? IS NULL OR p.id_categoria = ?) AND (? IS NULL OR e.id_bodega = ?) "
                   + (inactivos ? "" : "AND p.estado = 'ACTIVO' ")
                   + "ORDER BY p.codigo, b.codigo";
        String f = filtro == null ? "" : filtro.trim();
        List<Existencia> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, f);
            ps.setString(2, "%" + f + "%");
            ps.setString(3, "%" + f + "%");
            ps.setObject(4, idCategoria, Types.INTEGER);
            ps.setObject(5, idCategoria, Types.INTEGER);
            ps.setObject(6, idBodega, Types.INTEGER);
            ps.setObject(7, idBodega, Types.INTEGER);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Todos los productos activos con su stock en la bodega indicada
     * (0 si nunca han estado allí). Es la base de la plantilla de conteo (RF-009).
     */
    public List<Existencia> listarPorBodega(int idBodega) throws SQLException {
        String sql = "SELECT " + COLUMNAS + ", COALESCE(e.cantidad, 0) AS cantidad "
                   + "FROM producto p JOIN categoria c ON c.id_categoria = p.id_categoria "
                   + "JOIN bodega b ON b.id_bodega = ? "
                   + "LEFT JOIN existencia e ON e.id_producto = p.id_producto AND e.id_bodega = b.id_bodega "
                   + "WHERE p.estado = 'ACTIVO' ORDER BY p.codigo";
        List<Existencia> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idBodega);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /**
     * Bloquea la fila producto-bodega hasta el COMMIT y devuelve su cantidad.
     * Si la fila no existe la crea en 0. Así dos usuarios no pueden descontar
     * el mismo stock al mismo tiempo (concurrencia, RNF-003).
     */
    public int bloquear(Connection con, int idProducto, int idBodega) throws SQLException {
        try (PreparedStatement crear = con.prepareStatement(
                "INSERT INTO existencia (id_producto, id_bodega, cantidad) VALUES (?, ?, 0) "
              + "ON DUPLICATE KEY UPDATE cantidad = cantidad")) {
            crear.setInt(1, idProducto);
            crear.setInt(2, idBodega);
            crear.executeUpdate();
        }
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT cantidad FROM existencia WHERE id_producto = ? AND id_bodega = ? FOR UPDATE")) {
            ps.setInt(1, idProducto);
            ps.setInt(2, idBodega);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Suma (delta positivo) o resta (delta negativo) unidades. Llamar después de bloquear(). */
    public void ajustar(Connection con, int idProducto, int idBodega, int delta) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE existencia SET cantidad = cantidad + ? WHERE id_producto = ? AND id_bodega = ?")) {
            ps.setInt(1, delta);
            ps.setInt(2, idProducto);
            ps.setInt(3, idBodega);
            ps.executeUpdate();
        }
    }

    /** Stock total del producto = suma de sus existencias en todas las bodegas. */
    public int stockTotal(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT COALESCE(SUM(cantidad), 0) FROM existencia WHERE id_producto = ?")) {
            ps.setInt(1, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    // Convierte la fila en Existencia con su Producto (y Categoria) y su Bodega.
    private Existencia mapear(ResultSet rs) throws SQLException {
        Categoria c = new Categoria();
        c.setIdCategoria(rs.getInt("id_categoria"));
        c.setNombre(rs.getString("cat_nombre"));
        Producto p = new Producto(rs.getInt("id_producto"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("descripcion"), c, rs.getString("unidad_medida"), rs.getBigDecimal("costo_referencia"),
                rs.getObject("stock_minimo", Integer.class), rs.getObject("stock_maximo", Integer.class),
                EstadoRegistro.valueOf(rs.getString("estado")));
        Bodega b = new Bodega(rs.getInt("id_bodega"), rs.getString("bod_codigo"), rs.getString("bod_nombre"),
                rs.getString("ubicacion"), EstadoRegistro.valueOf(rs.getString("bod_estado")));
        return new Existencia(p, b, rs.getInt("cantidad"));
    }
}
