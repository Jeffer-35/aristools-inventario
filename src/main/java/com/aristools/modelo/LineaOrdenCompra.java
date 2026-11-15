package com.aristools.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Un producto de una orden de compra: cuántas unidades se piden y a qué costo estimado. */
public class LineaOrdenCompra {

    private final Producto producto;
    private final int cantidad;
    private final BigDecimal costoUnitario;

    public LineaOrdenCompra(Producto producto, int cantidad, BigDecimal costoUnitario) {
        this.producto = producto;
        this.cantidad = cantidad;
        this.costoUnitario = costoUnitario == null ? BigDecimal.ZERO : costoUnitario;
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
}
