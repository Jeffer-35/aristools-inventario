package com.aristools.seguridad;

import com.aristools.modelo.Usuario;
import com.aristools.util.NegocioException;

/**
 * Guarda quién inició sesión mientras el programa está abierto.
 * Los controladores llaman a exigir() antes de cada operación protegida:
 * así la seguridad no depende solo de ocultar botones (RNF-002).
 */
public final class Sesion {

    private static Usuario usuarioActual;

    private Sesion() {
    }

    public static void iniciar(Usuario u) {
        usuarioActual = u;
    }

    /** Al cerrar sesión se descartan los datos de la sesión (RF-001). */
    public static void cerrar() {
        usuarioActual = null;
    }

    public static Usuario getUsuario() {
        return usuarioActual;
    }

    /**
     * Verifica que haya una sesión y que el rol tenga permiso para la acción.
     * @throws NegocioException si no hay sesión o el rol no tiene permiso
     */
    public static void exigir(Accion a) {
        if (usuarioActual == null) {
            throw new NegocioException("Debe iniciar sesión para continuar.");
        }
        if (!Permisos.puede(usuarioActual.getRol(), a)) {
            throw new NegocioException("Su rol (" + usuarioActual.getRol().getNombreRol()
                    + ") no tiene permiso para realizar esta operación.");
        }
    }
}
