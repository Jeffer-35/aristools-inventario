package com.aristools.util;

import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;

/**
 * Error de regla de negocio (dato inválido, permiso insuficiente, stock que
 * no alcanza, etc.). Su mensaje está escrito para mostrarse tal cual al
 * usuario en la interfaz, indicando la causa y qué hacer (RNF-006).
 */
public class NegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NegocioException(String mensaje) {
        super(mensaje);
    }

    /**
     * Traduce un error de MySQL a un mensaje comprensible. Lo usan todos los
     * controladores, así el manejo de errores de base de datos no se repite.
     */
    public static NegocioException deBaseDeDatos(SQLException e) {
        if (e instanceof SQLIntegrityConstraintViolationException || "23000".equals(e.getSQLState())) {
            return new NegocioException("Ya existe un registro con ese dato único "
                    + "(código, NIT, nombre, usuario, documento o correo). Revise y cambie el valor.");
        }
        if ("HY000".equals(e.getSQLState()) && e.getErrorCode() == 3819) {
            return new NegocioException("Los datos no cumplen las reglas de la base de datos "
                    + "(por ejemplo, cantidades negativas, umbrales incoherentes "
                    + "o un movimiento autorizado por quien lo registró).");
        }
        return new NegocioException("Error de base de datos. Verifique que MySQL esté encendido e intente de nuevo.");
    }
}
