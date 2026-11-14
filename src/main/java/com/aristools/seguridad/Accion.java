package com.aristools.seguridad;

/**
 * Operaciones protegidas del sistema. Cada rol tiene permiso solo para
 * algunas (control de acceso basado en roles, RNF-002).
 */
public enum Accion {
    GESTIONAR_USUARIOS,
    GESTIONAR_CATALOGOS,
    GESTIONAR_PROVEEDORES,
    CONFIGURAR_PARAMETROS,
    REGISTRAR_MOVIMIENTOS,
    REALIZAR_CONTEO,
    AUTORIZAR_ENTRADAS,
    AUTORIZAR_SALIDAS,
    CONSULTAR_EXISTENCIAS,
    VER_REPORTES,
    VER_BITACORA
}
