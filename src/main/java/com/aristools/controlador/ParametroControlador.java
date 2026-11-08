package com.aristools.controlador;

import com.aristools.conexion.ConexionDB;
import com.aristools.dao.ParametroDAO;
import com.aristools.modelo.Parametro;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Parámetros globales y umbrales por defecto (RF-015). Cambiarlos no altera
 * los umbrales propios de los productos existentes.
 */
public class ParametroControlador {

    private final ParametroDAO parametroDAO = new ParametroDAO();

    public List<Parametro> listar() {
        try {
            return parametroDAO.listar();
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Guarda los tres parámetros juntos en una transacción. */
    public void guardar(String nombre, int minimo, int maximo) {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        Validador.textoObligatorio(nombre, "nombre del establecimiento");
        if (minimo < 0 || maximo < 0) {
            throw new NegocioException("Los umbrales no pueden ser negativos.");
        }
        if (minimo > maximo) {
            throw new NegocioException("El umbral mínimo no puede ser mayor que el máximo.");
        }
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                parametroDAO.actualizar(con, "nombre_establecimiento", nombre.trim());
                parametroDAO.actualizar(con, "stock_minimo_defecto", String.valueOf(minimo));
                parametroDAO.actualizar(con, "stock_maximo_defecto", String.valueOf(maximo));
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }
}
