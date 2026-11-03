package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Proveedor;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla proveedor (RF-005). */
public class ProveedorDAO {

    /** @param inactivos true para incluir los inactivos */
    public List<Proveedor> listar(boolean inactivos) throws SQLException {
        String sql = "SELECT id_proveedor, nit, razon_social, contacto, telefono, correo, direccion, estado "
                   + "FROM proveedor " + (inactivos ? "" : "WHERE estado = 'ACTIVO' ") + "ORDER BY razon_social";
        List<Proveedor> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Proveedor(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4),
                        rs.getString(5), rs.getString(6), rs.getString(7), EstadoRegistro.valueOf(rs.getString(8))));
            }
        }
        return lista;
    }

    public int insertar(Proveedor p) throws SQLException {
        String sql = "INSERT INTO proveedor (nit, razon_social, contacto, telefono, correo, direccion, estado) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            llenar(ps, p);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void actualizar(Proveedor p) throws SQLException {
        String sql = "UPDATE proveedor SET nit = ?, razon_social = ?, contacto = ?, telefono = ?, correo = ?, "
                   + "direccion = ?, estado = ? WHERE id_proveedor = ?";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            llenar(ps, p);
            ps.setInt(8, p.getIdProveedor());
            ps.executeUpdate();
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE proveedor SET estado = ? WHERE id_proveedor = ?")) {
            ps.setString(1, e.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** RF-005: no se puede inactivar un proveedor con movimientos pendientes. */
    public boolean tieneMovimientosPendientes(int id) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM movimiento_inventario WHERE id_proveedor = ? AND estado = 'PENDIENTE'")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1) > 0;
            }
        }
    }

    public int contarActivos() throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM proveedor WHERE estado = 'ACTIVO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Mismo orden de campos para INSERT y UPDATE.
    private void llenar(PreparedStatement ps, Proveedor p) throws SQLException {
        ps.setString(1, p.getNit());
        ps.setString(2, p.getRazonSocial());
        ps.setString(3, p.getContacto());
        ps.setString(4, p.getTelefono());
        ps.setString(5, p.getCorreo());
        ps.setString(6, p.getDireccion());
        ps.setString(7, p.getEstado().name());
    }
}
