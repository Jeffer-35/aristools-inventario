package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.util.NegocioException;
import java.sql.CallableStatement;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Llama desde Java a los procedimientos almacenados de la base aristools
 * (taller de Bases de Datos 2, semanas 13 y 14).
 *
 * Usa la clase CallableStatement de JDBC, que es la que sirve para ejecutar
 * procedimientos almacenados. El llamado se escribe con la sintaxis de
 * escape de JDBC: {call nombre_procedimiento(?, ?, ?)}. Cada signo ? es un
 * parámetro:
 *   - los de entrada (IN) se llenan con setInt, setString, setNull...;
 *   - los de salida (OUT) se registran antes con registerOutParameter y se
 *     leen después de ejecutar con getInt, getString...
 *
 * La conexión la entrega ConexionDB, que lee las credenciales de
 * db.properties. Cada método abre y cierra su conexión con
 * try-with-resources. Los procedimientos manejan su propia transacción
 * (START TRANSACTION, COMMIT y ROLLBACK), por eso aquí no se usa
 * setAutoCommit(false).
 *
 * Cuando una regla falla, el procedimiento hace SIGNAL SQLSTATE '45000' con
 * un mensaje en español; ese mensaje llega a Java como SQLException y aquí se
 * convierte en NegocioException para mostrarlo tal cual en la pantalla.
 */
public class ProcedimientosDAO {

    /** Una fila del resultado de sp_consultar_existencias. */
    public record FilaExistencia(String codigo, String producto, String codigoBodega, String bodega, int cantidad) { }

    /** Resultado de sp_consultar_existencias: las filas (result set) y el total (parámetro OUT). */
    public record ConsultaExistencias(List<FilaExistencia> filas, int total) { }

    /**
     * ACTUALIZAR: sp_autorizar_movimiento(IN p_id_movimiento, IN p_id_autorizador, OUT p_mensaje).
     * Autoriza un movimiento pendiente y actualiza la existencia.
     *
     * @return el mensaje que devuelve el procedimiento ("Movimiento autorizado")
     */
    public String autorizarMovimiento(int idMovimiento, int idAutorizador) {
        try (Connection con = ConexionDB.obtener();
             CallableStatement cs = con.prepareCall("{call sp_autorizar_movimiento(?, ?, ?)}")) {
            cs.setInt(1, idMovimiento);                     // IN
            cs.setInt(2, idAutorizador);                    // IN
            cs.registerOutParameter(3, Types.VARCHAR);      // OUT
            cs.execute();
            return cs.getString(3);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * INSERTAR (y actualizar): sp_registrar_transferencia(IN producto, IN origen, IN destino,
     * IN cantidad, IN usuario, IN observacion, OUT p_id_movimiento).
     *
     * @return el número del movimiento que se creó
     */
    public int registrarTransferencia(int idProducto, int idOrigen, int idDestino, int cantidad,
                                      int idUsuario, String observacion) {
        try (Connection con = ConexionDB.obtener();
             CallableStatement cs = con.prepareCall("{call sp_registrar_transferencia(?, ?, ?, ?, ?, ?, ?)}")) {
            cs.setInt(1, idProducto);
            cs.setInt(2, idOrigen);
            cs.setInt(3, idDestino);
            cs.setInt(4, cantidad);
            cs.setInt(5, idUsuario);
            if (observacion == null || observacion.isBlank()) {
                cs.setNull(6, Types.VARCHAR);               // parámetro opcional
            } else {
                cs.setString(6, observacion.trim());
            }
            cs.registerOutParameter(7, Types.INTEGER);
            cs.executeUpdate();                             // inserta y actualiza filas
            return cs.getInt(7);
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * INSERTAR o ACTUALIZAR alertas: sp_evaluar_alertas_producto(IN p_id_producto).
     * No devuelve nada; su resultado queda en la tabla alerta.
     */
    public void evaluarAlertas(int idProducto) {
        try (Connection con = ConexionDB.obtener();
             CallableStatement cs = con.prepareCall("{call sp_evaluar_alertas_producto(?)}")) {
            cs.setInt(1, idProducto);
            cs.execute();
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    /**
     * CONSULTAR: sp_consultar_existencias(IN p_id_producto, OUT p_total).
     * Devuelve filas (result set) y además un parámetro de salida.
     *
     * @param idProducto producto a consultar; null = todos los productos
     */
    public ConsultaExistencias consultarExistencias(Integer idProducto) {
        try (Connection con = ConexionDB.obtener();
             CallableStatement cs = con.prepareCall("{call sp_consultar_existencias(?, ?)}")) {
            if (idProducto == null) {
                cs.setNull(1, Types.INTEGER);
            } else {
                cs.setInt(1, idProducto);
            }
            cs.registerOutParameter(2, Types.INTEGER);
            List<FilaExistencia> filas = new ArrayList<>();
            boolean hayFilas = cs.execute();
            if (hayFilas) {
                try (ResultSet rs = cs.getResultSet()) {
                    while (rs.next()) {
                        filas.add(new FilaExistencia(rs.getString("codigo"), rs.getString("producto"),
                                rs.getString("codigo_bodega"), rs.getString("bodega"), rs.getInt("cantidad")));
                    }
                }
            }
            // El parámetro OUT se lee después de recorrer las filas
            return new ConsultaExistencias(filas, cs.getInt(2));
        } catch (SQLException e) {
            throw traducir(e);
        }
    }

    // Los errores de las reglas (SIGNAL '45000') traen un mensaje para el usuario.
    private static NegocioException traducir(SQLException e) {
        if ("45000".equals(e.getSQLState())) {
            return new NegocioException(e.getMessage());
        }
        return NegocioException.deBaseDeDatos(e);
    }
}
