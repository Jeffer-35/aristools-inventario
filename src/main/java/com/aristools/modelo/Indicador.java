package com.aristools.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Indicador de gestión (KPI) del tablero: un número que resume cómo va la
 * bodega, con su meta y un semáforo (bueno, alerta o crítico).
 *
 * Las fórmulas están en métodos estáticos para poder probarlas una por una.
 */
public class Indicador {

    /** Semáforo del indicador. INFORMATIVO: el dato no tiene meta, solo se muestra. */
    public enum Estado { BUENO, ALERTA, CRITICO, INFORMATIVO }

    private final String nombre;
    private final String valor;        // ya con su formato: "92,5 %", "$ 1.200.000", "3 veces"
    private final String meta;
    private final Estado estado;
    private final String formula;      // cómo se calcula, para explicarlo en la pantalla y en el PDF

    public Indicador(String nombre, String valor, String meta, Estado estado, String formula) {
        this.nombre = nombre;
        this.valor = valor;
        this.meta = meta;
        this.estado = estado;
        this.formula = formula;
    }

    /** a ÷ b x 100 con un decimal; 0 si b es cero. */
    public static BigDecimal porcentaje(BigDecimal a, BigDecimal b) {
        if (b == null || b.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return a.multiply(BigDecimal.valueOf(100)).divide(b, 1, RoundingMode.HALF_UP);
    }

    /**
     * Rotación: cuántas veces se "gastó" el inventario en el período.
     * Rotación = costo de lo que salió ÷ inventario promedio, donde
     * inventario promedio = (valor inicial + valor final) ÷ 2.
     */
    public static BigDecimal rotacion(BigDecimal costoSalidas, BigDecimal valorInicial, BigDecimal valorFinal) {
        BigDecimal promedio = valorInicial.add(valorFinal).divide(BigDecimal.valueOf(2), 2, RoundingMode.HALF_UP);
        if (promedio.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return costoSalidas.divide(promedio, 2, RoundingMode.HALF_UP);
    }

    /** Días que duraría el inventario al ritmo del período: días del período ÷ rotación. */
    public static long diasDeInventario(long diasPeriodo, BigDecimal rotacion) {
        if (rotacion == null || rotacion.signum() <= 0) {
            return 0;
        }
        return BigDecimal.valueOf(diasPeriodo).divide(rotacion, 0, RoundingMode.HALF_UP).longValue();
    }

    /** Para indicadores donde MÁS es mejor (exactitud): bueno desde la meta, alerta desde el mínimo. */
    public static Estado estadoMayorMejor(BigDecimal valor, double meta, double minimo) {
        double v = valor.doubleValue();
        return v >= meta ? Estado.BUENO : v >= minimo ? Estado.ALERTA : Estado.CRITICO;
    }

    /** Para indicadores donde MENOS es mejor (pérdidas, bajo stock): bueno hasta la meta, alerta hasta el máximo. */
    public static Estado estadoMenorMejor(BigDecimal valor, double meta, double maximo) {
        double v = valor.doubleValue();
        return v <= meta ? Estado.BUENO : v <= maximo ? Estado.ALERTA : Estado.CRITICO;
    }

    public String getNombre() { return nombre; }
    public String getValor() { return valor; }
    public String getMeta() { return meta; }
    public Estado getEstado() { return estado; }
    public String getFormula() { return formula; }
}
