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
    /** Longitud mínima de las contraseñas (RNF-001). */
    public static final int LONGITUD_MINIMA_CLAVE = 8;

    /**
     * Exige una contraseña segura: mínimo 8 caracteres, con mayúscula,
     * minúscula, número y símbolo. Indica exactamente qué le falta.
     */
    public static void claveSegura(String clave) {
        if (clave == null || clave.length() < LONGITUD_MINIMA_CLAVE) {
            throw new NegocioException("La contraseña debe tener al menos " + LONGITUD_MINIMA_CLAVE + " caracteres.");
        }
        if (clave.matches(".*\\s.*")) {
            throw new NegocioException("La contraseña no puede tener espacios.");
        }
        StringBuilder falta = new StringBuilder();
        if (!clave.matches(".*[A-ZÁÉÍÓÚÑ].*")) falta.append(" una mayúscula,");
        if (!clave.matches(".*[a-záéíóúñ].*")) falta.append(" una minúscula,");
        if (!clave.matches(".*\\d.*")) falta.append(" un número,");
        if (!clave.matches(".*[^A-Za-z0-9ÁÉÍÓÚÑáéíóúñ\\s].*")) falta.append(" un símbolo (por ejemplo * # @ !),");
        if (falta.length() > 0) {
            throw new NegocioException("La contraseña debe tener" + falta.substring(0, falta.length() - 1) + ".");
        }
    }

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
