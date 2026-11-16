package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Conteo;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.LineaConteo;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Historial de conteos físicos: tablas conteo y conteo_detalle. */
public class ConteoDAO {

    /**
     * Guarda el conteo y todas sus líneas en una sola transacción.
     * @return el id del conteo creado
     */
    public int insertar(Conteo c, int idUsuario, List<LineaConteo> lineas) throws SQLException {
        String sqlConteo = "INSERT INTO conteo (id_bodega, id_usuario, fecha, tipo, clase_abc, a_ciegas, "
                         + "productos_contados, con_diferencia) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        String sqlDetalle = "INSERT INTO conteo_detalle (id_conteo, id_producto, stock_sistema, cantidad_contada) "
                          + "SELECT ?, p.id_producto, ?, ? FROM producto p WHERE p.codigo = ?";
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = con.prepareStatement(sqlConteo, Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, c.getBodega().getIdBodega());
                    ps.setInt(2, idUsuario);
                    ps.setTimestamp(3, Timestamp.valueOf(c.getFecha()));
                    ps.setString(4, c.esCiclico() ? "CICLICO" : "GENERAL");
                    ps.setString(5, c.getClaseAbc() == null ? null : String.valueOf(c.getClaseAbc()));
                    ps.setBoolean(6, c.isACiegas());
                    ps.setInt(7, c.getProductosContados());
                    ps.setInt(8, c.getConDiferencia());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(sqlDetalle)) {
                    for (LineaConteo l : lineas) {
                        ps.setInt(1, id);
                        ps.setInt(2, l.getStockSistema());
                        ps.setInt(3, l.getCantidadContada());
                        ps.setString(4, l.getCodigoProducto());
                        ps.executeUpdate();
                    }
                }
                con.commit();
                c.setIdConteo(id);
                return id;
            } catch (SQLException e) {
                con.rollback();
                throw e;
            } finally {
                con.setAutoCommit(true);
            }
        }
    }

    /**
     * Conteos guardados, del más reciente al más antiguo.
     * @param idBodega null para todas las bodegas
     */
    public List<Conteo> listar(Integer idBodega) throws SQLException {
        String sql = "SELECT c.id_conteo, c.fecha, c.clase_abc, c.a_ciegas, c.productos_contados, c.con_diferencia, "
                   + "b.id_bodega, b.codigo, b.nombre, u.nombre_usuario "
                   + "FROM conteo c JOIN bodega b ON b.id_bodega = c.id_bodega "
                   + "JOIN usuario u ON u.id_usuario = c.id_usuario "
                   + (idBodega == null ? "" : "WHERE c.id_bodega = ? ")
                   + "ORDER BY c.fecha DESC, c.id_conteo DESC";
        List<Conteo> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (idBodega != null) {
                ps.setInt(1, idBodega);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    Bodega b = new Bodega(rs.getInt("id_bodega"), rs.getString("codigo"), rs.getString("nombre"),
                            null, EstadoRegistro.ACTIVO);
                    String clase = rs.getString("clase_abc");
                    lista.add(new Conteo(rs.getInt("id_conteo"), b, rs.getString("nombre_usuario"),
                            rs.getTimestamp("fecha").toLocalDateTime(), clase == null ? null : clase.charAt(0),
                            rs.getBoolean("a_ciegas"), rs.getInt("productos_contados"), rs.getInt("con_diferencia")));
                }
            }
        }
        return lista;
    }

    /** Líneas de un conteo guardado: lo que decía el sistema y lo que se contó. */
    public List<LineaConteo> detalle(int idConteo) throws SQLException {
        String sql = "SELECT p.codigo, p.nombre, d.stock_sistema, d.cantidad_contada "
                   + "FROM conteo_detalle d JOIN producto p ON p.id_producto = d.id_producto "
                   + "WHERE d.id_conteo = ? ORDER BY p.codigo";
        List<LineaConteo> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, idConteo);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new LineaConteo(rs.getString("codigo"), rs.getString("nombre"),
                            rs.getInt("stock_sistema"), rs.getInt("cantidad_contada")));
                }
            }
        }
        return lista;
    }
}
