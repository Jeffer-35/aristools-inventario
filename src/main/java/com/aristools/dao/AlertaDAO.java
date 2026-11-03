package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Alerta;
import com.aristools.modelo.EstadoAlerta;
import com.aristools.modelo.Producto;
import com.aristools.modelo.TipoAlerta;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla alerta (RF-012). */
public class AlertaDAO {

    /** Alertas activas para el panel principal, las más recientes primero. */
    public List<Alerta> listarActivas() throws SQLException {
        String sql = "SELECT a.id_alerta, a.tipo, a.stock_detectado, a.umbral, a.fecha_generacion, a.estado, "
                   + "p.id_producto, p.codigo, p.nombre FROM alerta a JOIN producto p ON p.id_producto = a.id_producto "
                   + "WHERE a.estado = 'ACTIVA' ORDER BY a.fecha_generacion DESC";
        List<Alerta> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Producto p = new Producto();
                p.setIdProducto(rs.getInt("id_producto"));
                p.setCodigo(rs.getString("codigo"));
                p.setNombre(rs.getString("nombre"));
                lista.add(new Alerta(rs.getInt("id_alerta"), p, TipoAlerta.valueOf(rs.getString("tipo")),
                        rs.getInt("stock_detectado"), rs.getInt("umbral"),
                        rs.getTimestamp("fecha_generacion").toLocalDateTime(),
                        EstadoAlerta.valueOf(rs.getString("estado"))));
            }
        }
        return lista;
    }

    /**
     * "Crea o actualiza la alerta" (ficha RF-012): resuelve la alerta del tipo
     * contrario si existía, actualiza la activa del mismo tipo o crea una nueva.
     */
    public void guardarOActualizar(Connection con, Alerta a) throws SQLException {
        int idProducto = a.getProducto().getIdProducto();
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE alerta SET estado = 'RESUELTA' WHERE id_producto = ? AND estado = 'ACTIVA' AND tipo <> ?")) {
            ps.setInt(1, idProducto);
            ps.setString(2, a.getTipo().name());
            ps.executeUpdate();
        }
        int actualizadas;
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE alerta SET stock_detectado = ?, umbral = ?, fecha_generacion = ? "
              + "WHERE id_producto = ? AND tipo = ? AND estado = 'ACTIVA'")) {
            ps.setInt(1, a.getStockDetectado());
            ps.setInt(2, a.getUmbral());
            ps.setTimestamp(3, Timestamp.valueOf(a.getFechaGeneracion()));
            ps.setInt(4, idProducto);
            ps.setString(5, a.getTipo().name());
            actualizadas = ps.executeUpdate();
        }
        if (actualizadas == 0) {
            try (PreparedStatement ps = con.prepareStatement(
                    "INSERT INTO alerta (id_producto, tipo, stock_detectado, umbral, fecha_generacion, estado) "
                  + "VALUES (?, ?, ?, ?, ?, 'ACTIVA')")) {
                ps.setInt(1, idProducto);
                ps.setString(2, a.getTipo().name());
                ps.setInt(3, a.getStockDetectado());
                ps.setInt(4, a.getUmbral());
                ps.setTimestamp(5, Timestamp.valueOf(a.getFechaGeneracion()));
                ps.executeUpdate();
            }
        }
    }

    /** El stock volvió a su rango normal: resuelve las alertas activas del producto. */
    public void resolver(Connection con, int idProducto) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(
                "UPDATE alerta SET estado = 'RESUELTA' WHERE id_producto = ? AND estado = 'ACTIVA'")) {
            ps.setInt(1, idProducto);
            ps.executeUpdate();
        }
    }

    /** Números de las tarjetas "Bajo stock" y "Sobrestock" del panel principal. */
    public int contarActivas(TipoAlerta t) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM alerta WHERE estado = 'ACTIVA' AND tipo = ?")) {
            ps.setString(1, t.name());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }
}
