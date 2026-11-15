package com.aristools.modelo;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Renglón de totales del reporte de pérdidas: cuántas bajas, cuántas
 * unidades y cuánto dinero se perdió por una causa o en una bodega.
 */
public class TotalPerdida {

    private final String nombre;
    private int bajas;
    private int unidades;
    private BigDecimal valor = BigDecimal.ZERO;

    public TotalPerdida(String nombre) {
        this.nombre = nombre;
    }

    /**
     * Agrupa las pérdidas por el dato que se indique y las ordena de la que
     * más dinero representa a la que menos.
     *
     * @param perdidas movimientos de pérdida ya autorizados
     * @param grupo    cómo se obtiene el nombre del grupo de cada movimiento
     *                 (por ejemplo, su causa o su bodega)
     */
    public static List<TotalPerdida> agrupar(List<Movimiento> perdidas, Function<Movimiento, String> grupo) {
        Map<String, TotalPerdida> totales = new LinkedHashMap<>();
        for (Movimiento m : perdidas) {
            TotalPerdida t = totales.computeIfAbsent(grupo.apply(m), TotalPerdida::new);
            t.bajas++;
            t.unidades += m.getCantidad();
            t.valor = t.valor.add(m.valorTotal());
        }
        List<TotalPerdida> lista = new ArrayList<>(totales.values());
        lista.sort((a, b) -> b.valor.compareTo(a.valor));
        return lista;
    }

    public String getNombre() {
        return nombre;
    }

    public int getBajas() {
        return bajas;
    }

    public int getUnidades() {
        return unidades;
    }

    public BigDecimal getValor() {
        return valor;
    }
}
