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

    /**
     * Guarda los parámetros generales y los de seguridad en una transacción.
     * @param diasVigencia días de vigencia de la contraseña (0 = no vence)
     * @param minutosInactividad minutos sin actividad para cerrar la sesión (0 = desactivado)
     */
    public void guardar(String nombre, int minimo, int maximo, int diasVigencia, int minutosInactividad) {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        Validador.textoObligatorio(nombre, "nombre del establecimiento");
        if (minimo < 0 || maximo < 0) {
            throw new NegocioException("Los umbrales no pueden ser negativos.");
        }
        if (minimo > maximo) {
            throw new NegocioException("El umbral mínimo no puede ser mayor que el máximo.");
        }
        if (diasVigencia < 0 || diasVigencia > 365) {
            throw new NegocioException("La vigencia de la contraseña debe estar entre 0 y 365 días (0 = no vence).");
        }
        if (minutosInactividad < 0 || minutosInactividad > 240) {
            throw new NegocioException("El cierre por inactividad debe estar entre 0 y 240 minutos (0 = desactivado).");
        }
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                parametroDAO.actualizar(con, "nombre_establecimiento", nombre.trim());
                parametroDAO.actualizar(con, "stock_minimo_defecto", String.valueOf(minimo));
                parametroDAO.actualizar(con, "stock_maximo_defecto", String.valueOf(maximo));
                parametroDAO.guardar(con, "dias_vigencia_clave", String.valueOf(diasVigencia),
                        "Días de vigencia de la contraseña (0 = sin vencimiento, recomendación NIST SP 800-63B)");
                parametroDAO.guardar(con, "minutos_inactividad", String.valueOf(minutosInactividad),
                        "Minutos sin actividad para cerrar la sesión (0 = desactivado)");
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
