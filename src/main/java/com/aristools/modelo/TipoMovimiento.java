package com.aristools.modelo;

/**
 * Catálogo de eventos de inventario (compra, merma, transferencia, etc.).
 * Define si requiere autorización y qué rol autoriza.
 */
public class TipoMovimiento {

    private int idTipoMovimiento;
    private String nombre;
    private Naturaleza naturaleza;
    private boolean requiereAutorizacion;
    private Rol rolAutorizador;   // null si no requiere autorización

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public TipoMovimiento() {
    }

    /** Constructor con todos los atributos. */
    public TipoMovimiento(int idTipoMovimiento, String nombre, Naturaleza naturaleza, boolean requiereAutorizacion, Rol rolAutorizador) {
        this.idTipoMovimiento = idTipoMovimiento;
        this.nombre = nombre;
        this.naturaleza = naturaleza;
        this.requiereAutorizacion = requiereAutorizacion;
        this.rolAutorizador = rolAutorizador;
    }

    /** Merma y producto dañado o vencido exigen motivo (RF-007). */
    public boolean exigeMotivo() {
        return "Merma".equalsIgnoreCase(nombre)
                || "Producto dañado o vencido".equalsIgnoreCase(nombre);
    }

    // ----- get / set de cada atributo -----

    public int getIdTipoMovimiento() {
        return idTipoMovimiento;
    }

    public void setIdTipoMovimiento(int idTipoMovimiento) {
        this.idTipoMovimiento = idTipoMovimiento;
    }

    public String getNombre() {
        return nombre;
    }

    public void setNombre(String nombre) {
        this.nombre = nombre;
    }

    public Naturaleza getNaturaleza() {
        return naturaleza;
    }

    public void setNaturaleza(Naturaleza naturaleza) {
        this.naturaleza = naturaleza;
    }

    public boolean isRequiereAutorizacion() {
        return requiereAutorizacion;
    }

    public void setRequiereAutorizacion(boolean requiereAutorizacion) {
        this.requiereAutorizacion = requiereAutorizacion;
    }

    public Rol getRolAutorizador() {
        return rolAutorizador;
    }

    public void setRolAutorizador(Rol rolAutorizador) {
        this.rolAutorizador = rolAutorizador;
    }

    /** Texto que muestran los combo box y las tablas. */
    @Override
    public String toString() {
        return nombre;
    }

}
