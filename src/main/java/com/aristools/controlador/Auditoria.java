package com.aristools.controlador;

import com.aristools.dao.BitacoraDAO;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.Sesion;
import java.sql.SQLException;
import java.util.List;

/**
 * Registra en la bitácora las acciones sensibles. Los controladores lo llaman
 * después de que la operación se guardó con éxito. Si la bitácora no se puede
 * escribir (por ejemplo, la base no tiene la tabla), la operación del usuario
 * no se interrumpe: el error se informa en la consola.
 */
public final class Auditoria {

    /** Acciones que se registran (también se usan en el filtro de la pantalla). */
    public static final List<String> ACCIONES = List.of(
            "INICIO_SESION", "INTENTO_FALLIDO", "CUENTA_BLOQUEADA", "CIERRE_SESION", "CAMBIO_CLAVE",
            "RESTABLECER_CLAVE", "CREAR_USUARIO", "MODIFICAR_USUARIO", "ESTADO_USUARIO",
            "CREAR_PRODUCTO", "MODIFICAR_PRODUCTO", "ESTADO_PRODUCTO",
            "CREAR_CATEGORIA", "MODIFICAR_CATEGORIA", "ESTADO_CATEGORIA",
            "CREAR_BODEGA", "MODIFICAR_BODEGA", "ESTADO_BODEGA",
            "CREAR_PROVEEDOR", "MODIFICAR_PROVEEDOR", "ESTADO_PROVEEDOR", "CAMBIO_PARAMETROS");

    private static final BitacoraDAO DAO = new BitacoraDAO();

    private Auditoria() {
    }

    /** Registra una acción hecha por el usuario de la sesión actual. */
    public static void registrar(String accion, String detalle) {
        Usuario u = Sesion.getUsuario();
        registrar(u == null ? null : u.getIdUsuario(), u == null ? null : u.getNombreUsuario(), accion, detalle);
    }

    /** Registra una acción indicando el usuario (por ejemplo, en un intento de ingreso). */
    public static void registrar(Integer idUsuario, String nombreUsuario, String accion, String detalle) {
        try {
            DAO.registrar(idUsuario, nombreUsuario, accion, detalle);
        } catch (SQLException | RuntimeException e) {
            System.err.println("No se pudo registrar en la bitácora (" + accion + "): " + e.getMessage());
        }
    }
}
