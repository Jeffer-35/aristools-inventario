package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Rol;
import com.aristools.modelo.TipoMovimiento;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Acceso al catálogo tipo_movimiento (9 tipos fijos creados por aristools.sql). */
public class TipoMovimientoDAO {

    private static final String SELECT =
            "SELECT t.id_tipo_movimiento, t.nombre, t.naturaleza, t.requiere_autorizacion, "
          + "r.id_rol, r.nombre_rol, r.descripcion "
          + "FROM tipo_movimiento t LEFT JOIN rol r ON r.id_rol = t.id_rol_autorizador ";

    /** Tipos de una naturaleza, para el combo de cada pestaña de movimientos. */
    public List<TipoMovimiento> listarPorNaturaleza(Naturaleza n) throws SQLException {
        List<TipoMovimiento> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE t.naturaleza = ? ORDER BY t.id_tipo_movimiento")) {
            ps.setString(1, n.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Por ejemplo "Ajuste positivo", que usa el conteo físico. */
    public TipoMovimiento buscarPorNombre(String nombre) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE t.nombre = ?")) {
            ps.setString(1, nombre);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    private TipoMovimiento mapear(ResultSet rs) throws SQLException {
        int idRol = rs.getInt("id_rol");
        Rol rol = rs.wasNull() ? null : new Rol(idRol, rs.getString("nombre_rol"), rs.getString("descripcion"));
        return new TipoMovimiento(rs.getInt("id_tipo_movimiento"), rs.getString("nombre"),
                Naturaleza.valueOf(rs.getString("naturaleza")), rs.getBoolean("requiere_autorizacion"), rol);
    }
}
