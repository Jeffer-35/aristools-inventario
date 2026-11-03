package com.aristools.modelo;

/**
 * Una fila de la plantilla de conteo físico (RF-009).
 */
public class LineaConteo {

    private String codigoProducto;
    private String nombreProducto;
    private int stockSistema;
    private int cantidadContada;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public LineaConteo() {
    }

    /** Constructor con todos los atributos. */
    public LineaConteo(String codigoProducto, String nombreProducto, int stockSistema, int cantidadContada) {
        this.codigoProducto = codigoProducto;
        this.nombreProducto = nombreProducto;
        this.stockSistema = stockSistema;
        this.cantidadContada = cantidadContada;
    }

    /** Diferencia = cantidad contada − stock del sistema (RF-009). */
    public int diferencia() {
        return cantidadContada - stockSistema;
    }

    // ----- get / set de cada atributo -----

    public String getCodigoProducto() {
        return codigoProducto;
    }

    public void setCodigoProducto(String codigoProducto) {
        this.codigoProducto = codigoProducto;
    }

    public String getNombreProducto() {
        return nombreProducto;
    }

    public void setNombreProducto(String nombreProducto) {
        this.nombreProducto = nombreProducto;
    }

    public int getStockSistema() {
        return stockSistema;
    }

    public void setStockSistema(int stockSistema) {
        this.stockSistema = stockSistema;
    }

    public int getCantidadContada() {
        return cantidadContada;
    }

    public void setCantidadContada(int cantidadContada) {
        this.cantidadContada = cantidadContada;
    }

}
