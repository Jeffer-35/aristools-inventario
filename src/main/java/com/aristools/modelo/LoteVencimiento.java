package com.aristools.modelo;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Lote de un producto con fecha de vencimiento (viene de una compra autorizada).
 *
 * FEFO (First Expired, First Out): lo primero que vence es lo primero que
 * debe salir. El sistema no lleva el stock lote por lote; lo estima con esa
 * misma regla: si siempre se despacha primero lo que vence antes, las
 * unidades que quedan en bodega son las de los lotes que vencen más tarde.
 */
public class LoteVencimiento {

    public static final String VENCIDO = "Vencido";
    public static final String POR_VENCER = "Por vencer";
    public static final String VIGENTE = "Vigente";

    private final Producto producto;
    private final String lote;
    private final LocalDate fechaVencimiento;
    private final int cantidadRecibida;
    private final String proveedor;
    private final String bodega;
    private int saldoEstimado;   // unidades de este lote que se estima siguen en bodega

    public LoteVencimiento(Producto producto, String lote, LocalDate fechaVencimiento, int cantidadRecibida,
                           String proveedor, String bodega) {
        this.producto = producto;
        this.lote = lote;
        this.fechaVencimiento = fechaVencimiento;
        this.cantidadRecibida = cantidadRecibida;
        this.proveedor = proveedor;
        this.bodega = bodega;
    }

    /**
     * Reparte el stock actual de UN producto entre sus lotes. Se empieza por
     * el lote que vence más tarde, porque según FEFO es el último en salir.
     *
     * Ejemplo: lote 1 (10 unidades, vence en enero) y lote 2 (10 unidades,
     * vence en marzo). Si quedan 12 en bodega: 10 son del lote 2 y 2 del lote 1.
     *
     * @return los lotes ordenados por fecha de vencimiento (el primero es el que debe salir primero)
     */
    public static List<LoteVencimiento> asignarSaldos(List<LoteVencimiento> lotesDelProducto, int stockActual) {
        List<LoteVencimiento> lista = new ArrayList<>(lotesDelProducto);
        lista.sort(Comparator.comparing(LoteVencimiento::getFechaVencimiento).reversed());
        int porRepartir = Math.max(stockActual, 0);
        for (LoteVencimiento l : lista) {
            l.saldoEstimado = Math.min(l.cantidadRecibida, porRepartir);
            porRepartir -= l.saldoEstimado;
        }
        lista.sort(Comparator.comparing(LoteVencimiento::getFechaVencimiento));
        return lista;
    }

    /** Días que faltan para el vencimiento; negativo si ya venció. */
    public long diasParaVencer(LocalDate hoy) {
        return ChronoUnit.DAYS.between(hoy, fechaVencimiento);
    }

    /**
     * @param diasAviso días de anticipación configurados (parámetro dias_aviso_vencimiento)
     */
    public String estado(LocalDate hoy, int diasAviso) {
        long dias = diasParaVencer(hoy);
        return dias < 0 ? VENCIDO : dias <= diasAviso ? POR_VENCER : VIGENTE;
    }

    public Producto getProducto() { return producto; }
    public String getLote() { return lote; }
    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public int getCantidadRecibida() { return cantidadRecibida; }
    public String getProveedor() { return proveedor; }
    public String getBodega() { return bodega; }
    public int getSaldoEstimado() { return saldoEstimado; }
}
