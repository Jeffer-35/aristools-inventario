package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.RegistroBitacora;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla bitacora. Solo inserta y consulta: la tabla no se
 * modifica ni se borra (lo impiden dos disparadores en la base de datos).
 */
public class BitacoraDAO {

    private static final int LARGO_DETALLE = 500;
    private static final int LIMITE_CONSULTA = 2000;

    public void registrar(Integer idUsuario, String nombreUsuario, String accion, String detalle) throws SQLException {
        String sql = "INSERT INTO bitacora (id_usuario, nombre_usuario, accion, detalle) VALUES (?, ?, ?, ?)";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (idUsuario == null) {
                ps.setNull(1, Types.INTEGER);
            } else {
                ps.setInt(1, idUsuario);
            }
            ps.setString(2, recortar(nombreUsuario, 60));
            ps.setString(3, accion);
            ps.setString(4, recortar(detalle, LARGO_DETALLE));
            ps.executeUpdate();
        }
    }

    /**
     * Consulta la bitácora con filtros opcionales (null = sin filtro), de la más
     * reciente a la más antigua. El texto se busca en el usuario y en el detalle.
     */
    public List<RegistroBitacora> consultar(LocalDate desde, LocalDate hasta, String accion, String texto)
            throws SQLException {
        StringBuilder sql = new StringBuilder(
                "SELECT id_bitacora, fecha, nombre_usuario, accion, detalle FROM bitacora WHERE 1 = 1");
        List<Object> parametros = new ArrayList<>();
        if (desde != null) {
            sql.append(" AND fecha >= ?");
            parametros.add(Timestamp.valueOf(desde.atStartOfDay()));
        }
        if (hasta != null) {
            sql.append(" AND fecha < ?");
            parametros.add(Timestamp.valueOf(hasta.plusDays(1).atStartOfDay()));
        }
        if (accion != null) {
            sql.append(" AND accion = ?");
            parametros.add(accion);
        }
        if (texto != null && !texto.isBlank()) {
            sql.append(" AND (nombre_usuario LIKE ? OR detalle LIKE ?)");
            parametros.add("%" + texto.trim() + "%");
            parametros.add("%" + texto.trim() + "%");
        }
        sql.append(" ORDER BY fecha DESC, id_bitacora DESC LIMIT ").append(LIMITE_CONSULTA);
        List<RegistroBitacora> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql.toString())) {
            for (int i = 0; i < parametros.size(); i++) {
                ps.setObject(i + 1, parametros.get(i));
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(new RegistroBitacora(rs.getInt(1), rs.getTimestamp(2).toLocalDateTime(),
                            rs.getString(3), rs.getString(4), rs.getString(5)));
                }
            }
        }
        return lista;
    }

    private static String recortar(String s, int largo) {
        return s == null || s.length() <= largo ? s : s.substring(0, largo - 1) + "…";
    }
}
