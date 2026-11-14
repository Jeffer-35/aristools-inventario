package com.aristools.util;

import java.math.BigDecimal;

/**
 * Arma el detalle de una modificación para la bitácora, comparando cada
 * campo antes y después. Solo anota los campos que cambiaron, por ejemplo:
 * "costo: 21500 → 23000; stock mínimo: 10 → 15".
 */
public final class Cambios {

    private final StringBuilder texto = new StringBuilder();

    /** Compara un campo y lo anota si su valor cambió. */
    public Cambios campo(String nombre, Object antes, Object despues) {
        String a = mostrar(antes);
        String d = mostrar(despues);
        if (!a.equals(d)) {
            if (texto.length() > 0) {
                texto.append("; ");
            }
            texto.append(nombre).append(": ").append(a).append(" → ").append(d);
        }
        return this;
    }

    /** @return true si al menos un campo cambió */
    public boolean hayCambios() {
        return texto.length() > 0;
    }

    @Override
    public String toString() {
        return hayCambios() ? texto.toString() : "sin cambios";
    }

    // Texto legible: vacío para null y números sin ceros sobrantes (21500.00 → 21500)
    private static String mostrar(Object valor) {
        if (valor == null) {
            return "(vacío)";
        }
        if (valor instanceof BigDecimal b) {
            return b.stripTrailingZeros().toPlainString();
        }
        String s = valor.toString().trim();
        return s.isEmpty() ? "(vacío)" : s;
    }
}
