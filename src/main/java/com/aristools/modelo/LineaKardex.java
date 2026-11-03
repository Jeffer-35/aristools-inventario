package com.aristools.modelo;

import java.time.LocalDateTime;

/**
 * Una fila del kardex (RF-014): Saldo = saldo anterior + entradas − salidas.
 */
public class LineaKardex {

    private LocalDateTime fecha;
    private String tipo;
    private String documento;
    private String bodega;
    private int entrada;
    private int salida;
    private int saldo;
    private String registra;
    private String autoriza;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public LineaKardex() {
    }

    /** Constructor con todos los atributos. */
    public LineaKardex(LocalDateTime fecha, String tipo, String documento, String bodega, int entrada, int salida, int saldo, String registra, String autoriza) {
        this.fecha = fecha;
        this.tipo = tipo;
        this.documento = documento;
        this.bodega = bodega;
        this.entrada = entrada;
        this.salida = salida;
        this.saldo = saldo;
        this.registra = registra;
        this.autoriza = autoriza;
    }

    // ----- get / set de cada atributo -----

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getTipo() {
        return tipo;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getBodega() {
        return bodega;
    }

    public void setBodega(String bodega) {
        this.bodega = bodega;
    }

    public int getEntrada() {
        return entrada;
    }

    public void setEntrada(int entrada) {
        this.entrada = entrada;
    }

    public int getSalida() {
        return salida;
    }

    public void setSalida(int salida) {
        this.salida = salida;
    }

    public int getSaldo() {
        return saldo;
    }

    public void setSaldo(int saldo) {
        this.saldo = saldo;
    }

    public String getRegistra() {
        return registra;
    }

    public void setRegistra(String registra) {
        this.registra = registra;
    }

    public String getAutoriza() {
        return autoriza;
    }

    public void setAutoriza(String autoriza) {
        this.autoriza = autoriza;
    }

}
