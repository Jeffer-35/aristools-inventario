package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Rol;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla rol. Los cuatro roles son fijos (los crea aristools.sql). */
public class RolDAO {

    /** Lista los roles para el combo box del formulario de usuarios. */
    public List<Rol> listar() throws SQLException {
        String sql = "SELECT id_rol, nombre_rol, descripcion FROM rol ORDER BY id_rol";
        List<Rol> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Rol(rs.getInt(1), rs.getString(2), rs.getString(3)));
            }
        }
        return lista;
    }

    /** @return el rol con ese id, o null si no existe */
    public Rol buscarPorId(int id) throws SQLException {
        String sql = "SELECT id_rol, nombre_rol, descripcion FROM rol WHERE id_rol = ?";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? new Rol(rs.getInt(1), rs.getString(2), rs.getString(3)) : null;
            }
        }
    }
}
