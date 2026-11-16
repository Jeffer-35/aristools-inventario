package com.aristools.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Clasificación ABC del inventario (principio de Pareto): pocos productos
 * concentran la mayor parte del valor que sale de la bodega.
 *
 *   Clase A: los productos que suman hasta el 80 % del valor consumido.
 *   Clase B: los que siguen, hasta llegar al 95 %.
 *   Clase C: el resto, incluidos los que no tuvieron salidas.
 *
 * Sirve para decidir a qué productos ponerles más control: los de clase A
 * se cuentan más seguido (conteo cíclico).
 */
public class ClasificacionAbc {

    public static final BigDecimal LIMITE_A = new BigDecimal("80");
    public static final BigDecimal LIMITE_B = new BigDecimal("95");

    private final Producto producto;
    private final int unidades;          // unidades que salieron en el período
    private final BigDecimal valor;      // unidades x costo con el que se valoraron
    private BigDecimal porcentaje = BigDecimal.ZERO;   // parte del valor total
    private BigDecimal acumulado = BigDecimal.ZERO;    // suma de los porcentajes hasta este producto
    private char clase = 'C';

    public ClasificacionAbc(Producto producto, int unidades, BigDecimal valor) {
        this.producto = producto;
        this.unidades = unidades;
        this.valor = valor == null ? BigDecimal.ZERO : valor;
    }

    /**
     * Ordena de mayor a menor valor y asigna la clase a cada producto.
     * Un producto es A si el acumulado ANTES de sumarlo todavía no llega al
     * 80 %: así el producto que cruza el límite queda en la clase más alta.
     */
    public static List<ClasificacionAbc> clasificar(List<ClasificacionAbc> consumo) {
        List<ClasificacionAbc> lista = new ArrayList<>(consumo);
        lista.sort(Comparator.comparing(ClasificacionAbc::getValor).reversed()
                .thenComparing(c -> c.getProducto().getCodigo()));
        BigDecimal total = BigDecimal.ZERO;
        for (ClasificacionAbc c : lista) {
            total = total.add(c.valor);
        }
        BigDecimal cien = BigDecimal.valueOf(100);
        BigDecimal acumulado = BigDecimal.ZERO;
        for (ClasificacionAbc c : lista) {
            if (total.signum() == 0 || c.valor.signum() == 0) {
                c.clase = 'C';                       // sin salidas: no aporta valor
                c.acumulado = total.signum() == 0 ? BigDecimal.ZERO : cien;
                continue;
            }
            c.porcentaje = c.valor.multiply(cien).divide(total, 2, RoundingMode.HALF_UP);
            c.clase = acumulado.compareTo(LIMITE_A) < 0 ? 'A' : acumulado.compareTo(LIMITE_B) < 0 ? 'B' : 'C';
            acumulado = acumulado.add(c.porcentaje);
            c.acumulado = acumulado.min(cien);
        }
        return lista;
    }

    public Producto getProducto() { return producto; }
    public int getUnidades() { return unidades; }
    public BigDecimal getValor() { return valor; }
    public BigDecimal getPorcentaje() { return porcentaje; }
    public BigDecimal getAcumulado() { return acumulado; }
    public char getClase() { return clase; }
}
