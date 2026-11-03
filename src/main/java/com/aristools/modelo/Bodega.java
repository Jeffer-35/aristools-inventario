package com.aristools.modelo;

/**
 * Lugar físico donde se almacena la mercancía (RF-004).
 */
public class Bodega {

    private int idBodega;
    private String codigo;
    private String nombre;
    private String ubicacion;
    private EstadoRegistro estado;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Bodega() {
        this.estado = EstadoRegistro.ACTIVO;
    }

    /** Constructor con todos los atributos. */
    public Bodega(int idBodega, String codigo, String nombre, String ubicacion, EstadoRegistro estado) {
        this.idBodega = idBodega;
        this.codigo = codigo;
        this.nombre = nombre;
        this.ubicacion = ubicacion;
        this.estado = estado;
    }

    /** Las bodegas inactivas no se pueden usar en movimientos nuevos (RF-004). */
    public boolean estaActiva() {
        return estado == EstadoRegistro.ACTIVO;
    }

    // ----- get / set de cada atributo -----

    public int getIdBodega() {
        return idBodega;
    }

    public void setIdBodega(int idBodega) {
        this.idBodega = idBodega;
    }

    public String getCodigo() {
        return codigo;
    }

    public void setCodigo(String codigo) {
        this.codigo = codigo;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(String ubicacion) {
        this.ubicacion = ubicacion;
    }

    public EstadoRegistro getEstado() {
        return estado;
    }

    public void setEstado(EstadoRegistro estado) {
        this.estado = estado;
    }

    /** Texto que muestran los combo box y las tablas. */
    @Override
    public String toString() {
        return nombre;
    }

}
