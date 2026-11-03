package com.aristools.util;

import java.util.regex.Pattern;

/**
 * Validaciones de formularios reutilizadas por los controladores.
 * Los mensajes indican la causa y cómo corregirla (RNF-006).
 */
public final class Validador {

    private static final Pattern CORREO = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    private Validador() {
    }

    /** @return true si el texto tiene forma de correo (nombre@dominio.ext) */
    public static boolean correoValido(String c) {
        return c != null && CORREO.matcher(c.trim()).matches();
    }

    /** Lanza un error si el campo obligatorio está vacío. */
    public static void textoObligatorio(String t, String campo) {
        if (t == null || t.isBlank()) {
            throw new NegocioException("El campo " + campo + " es obligatorio.");
        }
    }

    /**
     * Convierte el texto a entero no negativo.
     * @throws NegocioException si no es numérico o es negativo
     */
    public static int enteroNoNegativo(String t, String campo) {
        try {
            int valor = Integer.parseInt(t == null ? "" : t.trim());
            if (valor < 0) {
                throw new NumberFormatException();
            }
            return valor;
        } catch (NumberFormatException e) {
            throw new NegocioException("El campo " + campo + " debe ser un número entero mayor o igual a cero.");
        }
    }
}
