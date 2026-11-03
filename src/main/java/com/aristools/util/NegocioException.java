package com.aristools.util;

/**
 * Error de regla de negocio (dato inválido, permiso insuficiente, stock que
 * no alcanza, etc.). Su mensaje está escrito para mostrarse tal cual al
 * usuario en la interfaz.
 */
public class NegocioException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    public NegocioException(String mensaje) {
        super(mensaje);
    }
}
