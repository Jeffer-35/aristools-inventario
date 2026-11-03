package com.aristools.seguridad;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import com.aristools.util.NegocioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** Pruebas unitarias de BCrypt, permisos y sesión (RNF-001, RNF-002). */
class SeguridadTest {

    /** Hash del usuario admin en datos_iniciales.sql (contraseña Aristools2026*). */
    private static final String HASH_ADMIN = "$2a$10$uAMEtfnrGDRMuJWHbsLLYuwpuuE6NEfjkNngFuFSTFi.UMGt3M7ey";

    private final Rol almacenista = new Rol(2, "Almacenista", "");
    private final Rol administrador = new Rol(1, "Administrador del Sistema", "");

    @AfterEach
    void cerrarSesion() {
        Sesion.cerrar();
    }

    @Test
    void bcryptGeneraHashesDistintosQueSeVerifican() {
        String h1 = PasswordUtil.encriptar("Clave123*");
        String h2 = PasswordUtil.encriptar("Clave123*");
        assertTrue(h1.startsWith("$2a$10$"));
        assertNotEquals(h1, h2, "Cada hash lleva su propia sal");
        assertTrue(PasswordUtil.verificar("Clave123*", h1));
        assertFalse(PasswordUtil.verificar("otra", h1));
        assertFalse(PasswordUtil.verificar("Clave123*", "texto-que-no-es-hash"));
    }

    @Test
    void elHashDeLosDatosDePruebaEsCompatibleConJava() {
        assertTrue(PasswordUtil.verificar("Aristools2026*", HASH_ADMIN));
    }

    @Test
    void permisosPorRol() {
        assertTrue(Permisos.puede(almacenista, Accion.REGISTRAR_MOVIMIENTOS));
        assertFalse(Permisos.puede(almacenista, Accion.AUTORIZAR_SALIDAS));
        assertFalse(Permisos.puede(administrador, Accion.AUTORIZAR_ENTRADAS),
                "El administrador no autoriza movimientos");
        assertTrue(Permisos.puede(administrador, Accion.GESTIONAR_USUARIOS));
    }

    @Test
    void sinSesionOSinPermisoSeRechazaLaOperacion() {
        assertThrows(NegocioException.class, () -> Sesion.exigir(Accion.CONSULTAR_EXISTENCIAS));
        Usuario u = new Usuario();
        u.setRol(almacenista);
        Sesion.iniciar(u);
        Sesion.exigir(Accion.REGISTRAR_MOVIMIENTOS);   // permitido: no lanza error
        NegocioException e = assertThrows(NegocioException.class, () -> Sesion.exigir(Accion.GESTIONAR_USUARIOS));
        assertEquals("Su rol (Almacenista) no tiene permiso para realizar esta operación.", e.getMessage());
    }
}
