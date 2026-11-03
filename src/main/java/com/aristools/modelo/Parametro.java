package com.aristools.modelo;

import com.aristools.util.NegocioException;

/**
 * Configuración global guardada como clave-valor (RF-015), por ejemplo el
 * nombre del establecimiento y los umbrales por defecto.
 */
public class Parametro {

    private int idParametro;
    private String clave;
    private String valor;
    private String descripcion;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Parametro() {
    }

    /** Constructor con todos los atributos. */
    public Parametro(int idParametro, String clave, String valor, String descripcion) {
        this.idParametro = idParametro;
        this.clave = clave;
        this.valor = valor;
        this.descripcion = descripcion;
    }

    /** Devuelve el valor como número entero; informa si no es numérico. */
    public int valorEntero() {
        try {
            return Integer.parseInt(valor.trim());
        } catch (NumberFormatException | NullPointerException e) {
            throw new NegocioException("El parámetro '" + clave + "' no tiene un valor numérico.");
        }
    }

    // ----- get / set de cada atributo -----

    public int getIdParametro() {
        return idParametro;
    }

    public void setIdParametro(int idParametro) {
        this.idParametro = idParametro;
    }

    public String getClave() {
        return clave;
    }

    public void setClave(String clave) {
        this.clave = clave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

}
