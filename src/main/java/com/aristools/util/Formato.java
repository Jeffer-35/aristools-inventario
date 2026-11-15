package com.aristools.util;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;

/**
 * Forma de mostrar y de leer valores en pesos colombianos:
 * punto para los miles y coma para los decimales ($ 21.500,50).
 * Se usa en la entrada de mercancía, el kardex valorizado y los reportes.
 */
public final class Formato {

    private Formato() {
    }

    /** Devuelve el valor como "$ 21.500" (sin centavos si son cero) o "$ 21.500,50". */
    public static String pesos(BigDecimal valor) {
        if (valor == null) {
            return "—";
        }
        DecimalFormatSymbols simbolos = new DecimalFormatSymbols();
        simbolos.setGroupingSeparator('.');
        simbolos.setDecimalSeparator(',');
        BigDecimal redondeado = valor.setScale(2, RoundingMode.HALF_UP);
        boolean sinCentavos = redondeado.stripTrailingZeros().scale() <= 0;
        DecimalFormat formato = new DecimalFormat(sinCentavos ? "#,##0" : "#,##0.00", simbolos);
        return "$ " + formato.format(redondeado);
    }

    /**
     * Convierte a número lo que el usuario escribe en un campo de dinero.
     * Acepta 21500, 21.500 (punto de miles), 21500,50, 21500.50 y "$ 21.500".
     *
     * @param texto lo escrito por el usuario
     * @param campo nombre del campo, para el mensaje de error
     * @throws NegocioException si está vacío, no es un número o no es mayor que cero
     */
    public static BigDecimal leerPesos(String texto, String campo) {
        String t = texto == null ? "" : texto.trim().replace("$", "").replace(" ", "");
        if (t.isEmpty()) {
            throw new NegocioException("El campo " + campo + " es obligatorio.");
        }
        if (t.matches("\\d{1,3}(\\.\\d{3})+(,\\d+)?")) {
            t = t.replace(".", "").replace(",", ".");   // 21.500,50 -> 21500.50
        } else {
            t = t.replace(",", ".");
        }
        try {
            BigDecimal valor = new BigDecimal(t);
            if (valor.compareTo(BigDecimal.ZERO) <= 0) {
                throw new NumberFormatException();
            }
            return valor;
        } catch (NumberFormatException e) {
            throw new NegocioException("El campo " + campo + " debe ser un número mayor que cero "
                    + "(ejemplo: 21500 o 21500,50).");
        }
    }
}
