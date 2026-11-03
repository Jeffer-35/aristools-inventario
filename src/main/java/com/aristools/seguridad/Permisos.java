package com.aristools.seguridad;

import com.aristools.modelo.Rol;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * Matriz de permisos por rol (RNF-002), según las fichas de requerimientos y
 * los menús del mockup. El panel principal y las alertas son visibles para
 * todos los roles, por eso no necesitan una acción.
 */
public final class Permisos {

    private static final Map<String, Set<Accion>> MATRIZ = Map.of(
            "Administrador del Sistema", EnumSet.of(Accion.GESTIONAR_USUARIOS, Accion.GESTIONAR_CATALOGOS,
                    Accion.GESTIONAR_PROVEEDORES, Accion.CONFIGURAR_PARAMETROS,
                    Accion.CONSULTAR_EXISTENCIAS, Accion.VER_REPORTES),
            "Almacenista", EnumSet.of(Accion.REGISTRAR_MOVIMIENTOS, Accion.REALIZAR_CONTEO,
                    Accion.CONSULTAR_EXISTENCIAS),
            "Jefe de Compras", EnumSet.of(Accion.GESTIONAR_PROVEEDORES, Accion.AUTORIZAR_ENTRADAS,
                    Accion.CONSULTAR_EXISTENCIAS, Accion.VER_REPORTES),
            "Supervisor de Bodega", EnumSet.of(Accion.AUTORIZAR_SALIDAS,
                    Accion.CONSULTAR_EXISTENCIAS, Accion.VER_REPORTES));

    private Permisos() {
    }

    /** @return true si el rol tiene permiso para la acción */
    public static boolean puede(Rol rol, Accion a) {
        return rol != null && accionesDe(rol).contains(a);
    }

    /** Todas las acciones permitidas al rol; la vista las usa para armar el menú lateral. */
    public static Set<Accion> accionesDe(Rol rol) {
        if (rol == null) {
            return Collections.emptySet();
        }
        return Collections.unmodifiableSet(MATRIZ.getOrDefault(rol.getNombreRol(), EnumSet.noneOf(Accion.class)));
    }
}
