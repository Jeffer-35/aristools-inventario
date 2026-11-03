package com.aristools.modelo;

/**
 * Rol de usuario del sistema. Existen exactamente cuatro: Administrador del
 * Sistema, Almacenista, Jefe de Compras y Supervisor de Bodega.
 */
public class Rol {

    private int idRol;
    private String nombreRol;
    private String descripcion;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Rol() {
    }

    /** Constructor con todos los atributos. */
    public Rol(int idRol, String nombreRol, String descripcion) {
        this.idRol = idRol;
        this.nombreRol = nombreRol;
        this.descripcion = descripcion;
    }

    /**
     * Indica si el rol es el Administrador del Sistema.
     * Se usa, por ejemplo, para impedir desactivar al último administrador (RF-002).
     */
    public boolean esAdministrador() {
        return "Administrador del Sistema".equalsIgnoreCase(nombreRol);
    }

    // ----- get / set de cada atributo -----

    public int getIdRol() {
        return idRol;
    }

    public void setIdRol(int idRol) {
        this.idRol = idRol;
    }

    public String getNombreRol() {
        return nombreRol;
    }

    public void setNombreRol(String nombreRol) {
        this.nombreRol = nombreRol;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    /** Texto que muestran los combo box y las tablas. */
    @Override
    public String toString() {
        return nombreRol;
    }

}
