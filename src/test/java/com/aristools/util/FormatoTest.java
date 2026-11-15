package com.aristools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Pruebas de la forma de mostrar y leer valores en pesos. */
class FormatoTest {

    @Test
    void muestraLosPesosConPuntoDeMilesYComaDecimal() {
        assertEquals("$ 21.500", Formato.pesos(new BigDecimal("21500.0000")));
        assertEquals("$ 21.500,50", Formato.pesos(new BigDecimal("21500.5")));
        assertEquals("$ 1.234.567,89", Formato.pesos(new BigDecimal("1234567.891")));
        assertEquals("$ 0", Formato.pesos(BigDecimal.ZERO));
        assertEquals("—", Formato.pesos(null));
    }

    @Test
    void leeLasFormasEnQueElUsuarioEscribeUnCosto() {
        assertEquals(0, new BigDecimal("21500").compareTo(Formato.leerPesos("21500", "costo unitario")));
        assertEquals(0, new BigDecimal("21500").compareTo(Formato.leerPesos("$ 21.500", "costo unitario")));
        assertEquals(0, new BigDecimal("21500.50").compareTo(Formato.leerPesos("21.500,50", "costo unitario")));
        assertEquals(0, new BigDecimal("21500.50").compareTo(Formato.leerPesos("21500.50", "costo unitario")));
        assertEquals(0, new BigDecimal("1250000").compareTo(Formato.leerPesos("1.250.000", "costo unitario")));
    }

    @Test
    void rechazaCostosVaciosNoNumericosOCero() {
        for (String texto : new String[]{"", "   ", "abc", "0", "-500"}) {
            assertThrows(NegocioException.class, () -> Formato.leerPesos(texto, "costo unitario"), "Texto: " + texto);
        }
        NegocioException e = assertThrows(NegocioException.class, () -> Formato.leerPesos("", "costo unitario"));
        assertEquals("El campo costo unitario es obligatorio.", e.getMessage());
    }
}
