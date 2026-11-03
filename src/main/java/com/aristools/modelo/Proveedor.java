package com.aristools.modelo;

/**
 * Tercero que entrega mercancía o recibe devoluciones (RF-005). No usa el
 * sistema.
 */
public class Proveedor {

    private int idProveedor;
    private String nit;
    private String razonSocial;
    private String contacto;
    private String telefono;
    private String correo;
    private String direccion;
    private EstadoRegistro estado;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Proveedor() {
        this.estado = EstadoRegistro.ACTIVO;
    }

    /** Constructor con todos los atributos. */
    public Proveedor(int idProveedor, String nit, String razonSocial, String contacto, String telefono, String correo, String direccion, EstadoRegistro estado) {
        this.idProveedor = idProveedor;
        this.nit = nit;
        this.razonSocial = razonSocial;
        this.contacto = contacto;
        this.telefono = telefono;
        this.correo = correo;
        this.direccion = direccion;
        this.estado = estado;
    }

    /** @return true si el proveedor puede usarse en compras y devoluciones */
    public boolean estaActivo() {
        return estado == EstadoRegistro.ACTIVO;
    }

    // ----- get / set de cada atributo -----

    public int getIdProveedor() {
        return idProveedor;
    }

    public void setIdProveedor(int idProveedor) {
        this.idProveedor = idProveedor;
    }

    public String getNit() {
        return nit;
    }

    public void setNit(String nit) {
        this.nit = nit;
    }

    public String getRazonSocial() {
        return razonSocial;
    }

    public void setRazonSocial(String razonSocial) {
        this.razonSocial = razonSocial;
    }

    public String getContacto() {
        return contacto;
    }

    public void setContacto(String contacto) {
        this.contacto = contacto;
    }

    public String getTelefono() {
        return telefono;
    }

    public void setTelefono(String telefono) {
        this.telefono = telefono;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getDireccion() {
        return direccion;
    }

    public void setDireccion(String direccion) {
        this.direccion = direccion;
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
        return razonSocial;
    }

}
