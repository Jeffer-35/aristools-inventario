package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.EstadoRegistro;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla bodega (RF-004). */
public class BodegaDAO {

    private static final String SELECT = "SELECT id_bodega, codigo, nombre, ubicacion, estado FROM bodega ";

    /** @param inactivas true para incluir las inactivas (los combos de movimientos usan false) */
    public List<Bodega> listar(boolean inactivas) throws SQLException {
        List<Bodega> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT
                     + (inactivas ? "" : "WHERE estado = 'ACTIVO' ") + "ORDER BY codigo");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    public Bodega buscarPorId(int id) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE id_bodega = ?")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    public int insertar(Bodega b) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "INSERT INTO bodega (codigo, nombre, ubicacion, estado) VALUES (?, ?, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, b.getCodigo());
            ps.setString(2, b.getNombre());
            ps.setString(3, b.getUbicacion());
            ps.setString(4, b.getEstado().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void actualizar(Bodega b) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE bodega SET codigo = ?, nombre = ?, ubicacion = ?, estado = ? WHERE id_bodega = ?")) {
            ps.setString(1, b.getCodigo());
            ps.setString(2, b.getNombre());
            ps.setString(3, b.getUbicacion());
            ps.setString(4, b.getEstado().name());
            ps.setInt(5, b.getIdBodega());
            ps.executeUpdate();
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE bodega SET estado = ? WHERE id_bodega = ?")) {
            ps.setString(1, e.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public int contarActivas() throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM bodega WHERE estado = 'ACTIVO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private Bodega mapear(ResultSet rs) throws SQLException {
        return new Bodega(rs.getInt("id_bodega"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("ubicacion"), EstadoRegistro.valueOf(rs.getString("estado")));
    }
}
