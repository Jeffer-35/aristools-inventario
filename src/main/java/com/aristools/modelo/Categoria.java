package com.aristools.modelo;

/**
 * Agrupa los productos del inventario (RF-003).
 */
public class Categoria {

    private int idCategoria;
    private String nombre;
    private String descripcion;
    private EstadoRegistro estado;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Categoria() {
        this.estado = EstadoRegistro.ACTIVO;
    }

    /** Constructor con todos los atributos. */
    public Categoria(int idCategoria, String nombre, String descripcion, EstadoRegistro estado) {
        this.idCategoria = idCategoria;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.estado = estado;
    }

    /** @return true si la categoría se puede asignar a productos */
    public boolean estaActiva() {
        return estado == EstadoRegistro.ACTIVO;
    }

    // ----- get / set de cada atributo -----

    public int getIdCategoria() {
        return idCategoria;
    }

    public void setIdCategoria(int idCategoria) {
        this.idCategoria = idCategoria;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
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
