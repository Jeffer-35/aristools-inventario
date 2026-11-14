package com.aristools.controlador;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.modelo.Usuario;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pruebas del inicio de sesión (RF-001) sobre la base H2 en memoria.
 * Usan los usuarios de datos_iniciales.sql; no bloquean cuentas activas.
 */
@ExtendWith(BaseDePruebas.class)
class LoginControladorTest {

    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void credencialesCorrectasInicianSesion() {
        Usuario u = login.iniciarSesion("admin", "Aristools2026*");
        assertTrue(u.getRol().esAdministrador());
        assertSame(u, Sesion.getUsuario());
    }

    @Test
    void contrasenaIncorrectaNoIndicaCualDatoFallo() {
        NegocioException e = assertThrows(NegocioException.class,
                () -> login.iniciarSesion("supervisor", "clave-equivocada"));
        assertEquals("Usuario o contraseña incorrectos.", e.getMessage());
        login.iniciarSesion("supervisor", "Aristools2026*");   // el ingreso correcto reinicia el contador
        assertEquals(0, Sesion.getUsuario().getIntentosFallidos());
    }

    @Test
    void usuarioInexistenteRecibeElMismoMensaje() {
        NegocioException e = assertThrows(NegocioException.class,
                () -> login.iniciarSesion("no.existe", "lo-que-sea"));
        assertEquals("Usuario o contraseña incorrectos.", e.getMessage());
    }

    @Test
    void yaExisteAdministradorYNoSeCreaOtroPorEstaVia() {
        assertTrue(login.existeAdministrador());
        assertThrows(NegocioException.class, () -> login.crearPrimerAdministrador(new Usuario(), "ClaveSegura1"));
    }
}
