package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.EstadoUsuario;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla usuario (RF-001, RF-002).
 * Nunca guarda contraseñas en texto plano: recibe el hash ya calculado.
 */
public class UsuarioDAO {

    private static final String SELECT =
            "SELECT u.id_usuario, u.nombre_completo, u.documento, u.correo, u.nombre_usuario, "
          + "u.contrasena_hash, u.estado, u.intentos_fallidos, u.ultimo_acceso, "
          + "r.id_rol, r.nombre_rol, r.descripcion "
          + "FROM usuario u JOIN rol r ON r.id_rol = u.id_rol ";

    /** Busca la cuenta para el inicio de sesión; null si no existe. */
    public Usuario buscarPorNombreUsuario(String n) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE u.nombre_usuario = ?")) {
            ps.setString(1, n);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Lista todos los usuarios para la tabla de gestión. */
    public List<Usuario> listar() throws SQLException {
        List<Usuario> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "ORDER BY u.nombre_usuario");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                lista.add(mapear(rs));
            }
        }
        return lista;
    }

    /** Crea la cuenta y devuelve el id generado. */
    public int insertar(Usuario u) throws SQLException {
        String sql = "INSERT INTO usuario (nombre_completo, documento, correo, nombre_usuario, "
                   + "contrasena_hash, id_rol, estado) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, u.getNombreCompleto());
            ps.setString(2, u.getDocumento());
            ps.setString(3, u.getCorreo());
            ps.setString(4, u.getNombreUsuario());
            ps.setString(5, u.getContrasenaHash());
            ps.setInt(6, u.getRol().getIdRol());
            ps.setString(7, u.getEstado().name());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Actualiza los datos editables (no la contraseña). */
    public void actualizar(Usuario u) throws SQLException {
        String sql = "UPDATE usuario SET nombre_completo = ?, documento = ?, correo = ?, "
                   + "nombre_usuario = ?, id_rol = ?, estado = ? WHERE id_usuario = ?";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, u.getNombreCompleto());
            ps.setString(2, u.getDocumento());
            ps.setString(3, u.getCorreo());
            ps.setString(4, u.getNombreUsuario());
            ps.setInt(5, u.getRol().getIdRol());
            ps.setString(6, u.getEstado().name());
            ps.setInt(7, u.getIdUsuario());
            ps.executeUpdate();
        }
    }

    /** Activa, desactiva o reactiva una cuenta; al activarla reinicia los intentos. */
    public void cambiarEstado(int id, EstadoUsuario e) throws SQLException {
        String sql = "UPDATE usuario SET estado = ?, intentos_fallidos = "
                   + "CASE WHEN ? = 'ACTIVO' THEN 0 ELSE intentos_fallidos END WHERE id_usuario = ?";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, e.name());
            ps.setString(2, e.name());
            ps.setInt(3, id);
            ps.executeUpdate();
        }
    }

    /** Guarda el nuevo hash al restablecer la contraseña. */
    public void actualizarClave(int id, String hash) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE usuario SET contrasena_hash = ?, intentos_fallidos = 0 WHERE id_usuario = ?")) {
            ps.setString(1, hash);
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    /** Guarda el contador de intentos fallidos y el estado (bloqueo, RF-001). */
    public void actualizarIntentos(Usuario u) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE usuario SET intentos_fallidos = ?, estado = ? WHERE id_usuario = ?")) {
            ps.setInt(1, u.getIntentosFallidos());
            ps.setString(2, u.getEstado().name());
            ps.setInt(3, u.getIdUsuario());
            ps.executeUpdate();
        }
    }

    /** Registra la fecha y hora del último inicio de sesión correcto. */
    public void registrarAcceso(int id) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "UPDATE usuario SET ultimo_acceso = NOW(), intentos_fallidos = 0 WHERE id_usuario = ?")) {
            ps.setInt(1, id);
            ps.executeUpdate();
        }
    }

    /** Cuántos administradores activos hay (siempre debe quedar al menos uno, RF-002). */
    public int contarAdministradoresActivos() throws SQLException {
        String sql = "SELECT COUNT(*) FROM usuario u JOIN rol r ON r.id_rol = u.id_rol "
                   + "WHERE r.nombre_rol = 'Administrador del Sistema' AND u.estado = 'ACTIVO'";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Convierte la fila actual del ResultSet en un objeto Usuario.
    private Usuario mapear(ResultSet rs) throws SQLException {
        Rol rol = new Rol(rs.getInt("id_rol"), rs.getString("nombre_rol"), rs.getString("descripcion"));
        Timestamp acceso = rs.getTimestamp("ultimo_acceso");
        return new Usuario(rs.getInt("id_usuario"), rs.getString("nombre_completo"), rs.getString("documento"),
                rs.getString("correo"), rs.getString("nombre_usuario"), rs.getString("contrasena_hash"), rol,
                EstadoUsuario.valueOf(rs.getString("estado")), rs.getInt("intentos_fallidos"),
                acceso == null ? null : acceso.toLocalDateTime());
    }
}
