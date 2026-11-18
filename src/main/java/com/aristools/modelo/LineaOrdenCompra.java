package com.aristools.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Un producto de una orden de compra: cuántas unidades se piden, a qué costo
 * estimado y cuántas han llegado ya (con entradas autorizadas).
 */
public class LineaOrdenCompra {

    private final Producto producto;
    private final int cantidad;
    private final BigDecimal costoUnitario;
    private int cantidadRecibida;   // 0 al generar la orden; sube cuando se autoriza la entrada que la recibe
    private int enEspera;           // unidades que ya vienen en entradas pendientes de autorizar

    public LineaOrdenCompra(Producto producto, int cantidad, BigDecimal costoUnitario) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.costoUnitario = costoUnitario == null ? BigDecimal.ZERO : costoUnitario;
    }

    /** Unidades que todavía faltan por llegar (nunca negativo). */
    public int pendiente() {
        return Math.max(cantidad - cantidadRecibida, 0);
    }

    /** Lo que todavía se puede recibir: lo que falta menos lo que ya viene en entradas pendientes. */
    public int porRecibir() {
        return Math.max(pendiente() - enEspera, 0);
    }

    /** cantidad x costo unitario */
    public BigDecimal subtotal() {
        return costoUnitario.multiply(BigDecimal.valueOf(cantidad)).setScale(2, RoundingMode.HALF_UP);
    }

    public Producto getProducto() {
        return producto;
    }

    public int getCantidad() {
        return cantidad;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public int getCantidadRecibida() {
        return cantidadRecibida;
    }

    public void setCantidadRecibida(int cantidadRecibida) {
        this.cantidadRecibida = cantidadRecibida;
    }

    public int getEnEspera() {
        return enEspera;
    }

    public void setEnEspera(int enEspera) {
        this.enEspera = enEspera;
    }
}
