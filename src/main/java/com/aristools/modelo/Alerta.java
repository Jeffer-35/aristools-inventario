package com.aristools.modelo;

import java.time.LocalDateTime;

/**
 * Aviso de bajo stock o sobrestock de un producto (RF-012).
 */
public class Alerta {

    private int idAlerta;
    private Producto producto;
    private TipoAlerta tipo;
    private int stockDetectado;
    private int umbral;
    private LocalDateTime fechaGeneracion;
    private EstadoAlerta estado;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Alerta() {
        this.estado = EstadoAlerta.ACTIVA;
        this.fechaGeneracion = LocalDateTime.now();
    }

    /** Constructor con todos los atributos. */
    public Alerta(int idAlerta, Producto producto, TipoAlerta tipo, int stockDetectado, int umbral, LocalDateTime fechaGeneracion, EstadoAlerta estado) {
        this.idAlerta = idAlerta;
        this.producto = producto;
        this.tipo = tipo;
        this.stockDetectado = stockDetectado;
        this.umbral = umbral;
        this.fechaGeneracion = fechaGeneracion;
        this.estado = estado;
    }

    /** Marca la alerta como resuelta cuando el stock vuelve a su rango normal. */
    public void resolver() {
        estado = EstadoAlerta.RESUELTA;
    }

    // ----- get / set de cada atributo -----

    public int getIdAlerta() {
        return idAlerta;
    }

    public void setIdAlerta(int idAlerta) {
        this.idAlerta = idAlerta;
    }

    public Producto getProducto() {
        return producto;
    }

    public void setProducto(Producto producto) {
        this.producto = producto;
    }

    public TipoAlerta getTipo() {
        return tipo;
    }

    public void setTipo(TipoAlerta tipo) {
        this.tipo = tipo;
    }

    public int getStockDetectado() {
        return stockDetectado;
    }

    public void setStockDetectado(int stockDetectado) {
        this.stockDetectado = stockDetectado;
    }

    public int getUmbral() {
        return umbral;
    }

    public void setUmbral(int umbral) {
        this.umbral = umbral;
    }

    public LocalDateTime getFechaGeneracion() {
        return fechaGeneracion;
    }

    public void setFechaGeneracion(LocalDateTime fechaGeneracion) {
        this.fechaGeneracion = fechaGeneracion;
    }

    public EstadoAlerta getEstado() {
        return estado;
    }

    public void setEstado(EstadoAlerta estado) {
        this.estado = estado;
    }

}
