package com.aristools.modelo;

/**
 * Cantidad de un producto en una bodega (llave compuesta producto + bodega).
 * El stock total de un producto es la suma de sus existencias.
 */
public class Existencia {

    private Producto producto;
    private Bodega bodega;
    private int cantidad;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Existencia() {
    }

    /** Constructor con todos los atributos. */
    public Existencia(Producto producto, Bodega bodega, int cantidad) {
        this.producto = producto;
        this.bodega = bodega;
        this.cantidad = cantidad;
    }

    /** @return true si hay unidades suficientes para sacar la cantidad pedida */
    public boolean alcanzaPara(int cantidad) {
        return this.cantidad >= cantidad;
    }

    // ----- get / set de cada atributo -----

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public Bodega getBodega() {
        return bodega;
    }

    public void setBodega(Bodega bodega) {
        this.bodega = bodega;
    }

    public int getCantidad() {
        return cantidad;
    }

    public void setCantidad(int cantidad) {
        this.cantidad = cantidad;
    }

}
