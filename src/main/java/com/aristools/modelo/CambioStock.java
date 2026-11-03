package com.aristools.modelo;

/**
 * Resultado de calcularCambiosStock(): cuánto suma (delta positivo) o resta
 * (delta negativo) un movimiento en una bodega.
 */
public class CambioStock {

    private Bodega bodega;
    private int delta;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public CambioStock() {
    }

    /** Constructor con todos los atributos. */
    public CambioStock(Bodega bodega, int delta) {
        this.bodega = bodega;
        this.delta = delta;
    }

    // ----- get / set de cada atributo -----

    public Bodega getBodega() {
        return bodega;
    }

    public void setBodega(Bodega bodega) {
        this.bodega = bodega;
    }

    public int getDelta() {
        return delta;
    }

    public void setDelta(int delta) {
        this.delta = delta;
    }

}
