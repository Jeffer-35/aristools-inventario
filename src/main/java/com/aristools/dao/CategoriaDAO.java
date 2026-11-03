package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.EstadoRegistro;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla categoria (RF-003). Las categorías se inactivan, no se borran. */
public class CategoriaDAO {

    /** @param inactivas true para incluir también las inactivas */
    public List<Categoria> listar(boolean inactivas) throws SQLException {
        String sql = "SELECT id_categoria, nombre, descripcion, estado FROM categoria "
                   + (inactivas ? "" : "WHERE estado = 'ACTIVO' ") + "ORDER BY nombre";
        List<Categoria> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Categoria(rs.getInt(1), rs.getString(2), rs.getString(3),
                        EstadoRegistro.valueOf(rs.getString(4))));
            }
        }
        return lista;
    }

    /** Crea la categoría y devuelve su id. */
    public int insertar(Categoria c) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO categoria (nombre, descripcion, estado) VALUES (?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getDescripcion());
            ps.setString(3, c.getEstado().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void actualizar(Categoria c) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE categoria SET nombre = ?, descripcion = ?, estado = ? WHERE id_categoria = ?")) {
            ps.setString(1, c.getNombre());
            ps.setString(2, c.getDescripcion());
            ps.setString(3, c.getEstado().name());
            ps.setInt(4, c.getIdCategoria());
            ps.executeUpdate();
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE categoria SET estado = ? WHERE id_categoria = ?")) {
            ps.setString(1, e.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** RF-003: no se puede inactivar una categoría que tenga productos activos. */
    public boolean tieneProductosActivos(int id) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM producto WHERE id_categoria = ? AND estado = 'ACTIVO'")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public int contarActivas() throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM categoria WHERE estado = 'ACTIVO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
