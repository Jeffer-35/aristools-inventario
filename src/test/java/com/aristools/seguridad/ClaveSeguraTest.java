package com.aristools.seguridad;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.modelo.Usuario;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

/**
 * Política de contraseñas (RNF-001): contraseña segura y vencimiento
 * configurable. No necesitan base de datos.
 */
class ClaveSeguraTest {

    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 11, 12, 19, 0);

    @Test
    void aceptaUnaContrasenaSegura() {
        assertDoesNotThrow(() -> Validador.claveSegura("Aristools2026*"));
        assertDoesNotThrow(() -> Validador.claveSegura("Abc1234!"));
    }

    @Test
    void rechazaContrasenasDebiles() {
        assertThrows(NegocioException.class, () -> Validador.claveSegura("Abc12!"));          // corta
        assertThrows(NegocioException.class, () -> Validador.claveSegura("aristools2026*"));  // sin mayúscula
        assertThrows(NegocioException.class, () -> Validador.claveSegura("ARISTOOLS2026*"));  // sin minúscula
        assertThrows(NegocioException.class, () -> Validador.claveSegura("Aristools*"));      // sin número
        assertThrows(NegocioException.class, () -> Validador.claveSegura("Aristools2026"));   // sin símbolo
        assertThrows(NegocioException.class, () -> Validador.claveSegura("Aris tools2026*")); // con espacio
    }

    @Test
    void conVigenciaCeroLaContrasenaNoVence() {
        Usuario u = new Usuario();
        u.setFechaCambioClave(AHORA.minusDays(400));
        assertFalse(u.claveVencida(0, AHORA));
    }

    @Test
    void laContrasenaVenceAlCumplirLosDiasDeVigencia() {
        Usuario u = new Usuario();
        u.setFechaCambioClave(AHORA.minusDays(29));
        assertFalse(u.claveVencida(30, AHORA));
        u.setFechaCambioClave(AHORA.minusDays(30));
        assertTrue(u.claveVencida(30, AHORA));
    }
}
