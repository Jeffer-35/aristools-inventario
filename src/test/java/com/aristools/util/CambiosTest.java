package com.aristools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Detalle de los cambios que se guarda en la bitácora de auditoría. */
class CambiosTest {

    @Test
    void anotaSoloLosCamposQueCambiaron() {
        Cambios c = new Cambios()
                .campo("nombre", "Martillo", "Martillo")
                .campo("costo", new BigDecimal("21500.00"), new BigDecimal("23000"))
                .campo("stock mínimo", 10, 15);
        assertTrue(c.hayCambios());
        assertEquals("costo: 21500 → 23000; stock mínimo: 10 → 15", c.toString());
    }

    @Test
    void sinCambiosNoHayNadaQueRegistrar() {
        Cambios c = new Cambios().campo("nombre", "Martillo", "Martillo").campo("stock máximo", null, null);
        assertFalse(c.hayCambios());
        assertEquals("sin cambios", c.toString());
    }
}
