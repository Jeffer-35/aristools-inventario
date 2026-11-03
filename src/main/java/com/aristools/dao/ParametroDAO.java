package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Parametro;
import com.aristools.util.NegocioException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/** Acceso a la tabla parametro (RF-015). */
public class ParametroDAO {

    public List<Parametro> listar() throws SQLException {
        List<Parametro> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT id_parametro, clave, valor, descripcion FROM parametro ORDER BY id_parametro");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(new Parametro(rs.getInt(1), rs.getString(2), rs.getString(3), rs.getString(4)));
            }
        }
        return lista;
    }

    /**
     * Lee un parámetro numérico usando la conexión recibida, para que funcione
     * dentro de la misma transacción del movimiento (por ejemplo, al evaluar alertas).
     */
    public int obtenerEntero(Connection con, String clave) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("SELECT clave, valor FROM parametro WHERE clave = ?")) {
            ps.setString(1, clave);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NegocioException("No existe el parámetro '" + clave + "'.");
                }
                return new Parametro(0, rs.getString(1), rs.getString(2), null).valorEntero();
            }
        }
    }

    /** Cambia el valor de un parámetro (se usa dentro de una transacción). */
    public void actualizar(Connection con, String clave, String valor) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement("UPDATE parametro SET valor = ? WHERE clave = ?")) {
            ps.setString(1, valor);
            ps.setString(2, clave);
            if (ps.executeUpdate() == 0) {
                throw new NegocioException("No existe el parámetro '" + clave + "'.");
            }
        }
    }
}
