package com.aristools.modelo;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Una fila del kardex (RF-014): Saldo = saldo anterior + entradas − salidas.
 *
 * Kardex valorizado: además de las unidades lleva los valores en pesos.
 * Valor del saldo = valor anterior + valor de la entrada − valor de la salida,
 * donde cada valor es cantidad x costo unitario del movimiento.
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
    private BigDecimal costoUnitario;                      // costo con el que se valoró el movimiento
    private BigDecimal valorEntrada = BigDecimal.ZERO;     // entrada x costo unitario
    private BigDecimal valorSalida = BigDecimal.ZERO;      // salida x costo unitario
    private BigDecimal valorSaldo = BigDecimal.ZERO;       // valor en pesos de las unidades del saldo

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

    // ----- Kardex valorizado -----

    /** Guarda de una vez los cuatro valores en pesos de la línea. */
    public void setValores(BigDecimal costoUnitario, BigDecimal valorEntrada, BigDecimal valorSalida, BigDecimal valorSaldo) {
        this.costoUnitario = costoUnitario;
        this.valorEntrada = valorEntrada;
        this.valorSalida = valorSalida;
        this.valorSaldo = valorSaldo;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }

    public BigDecimal getValorEntrada() {
        return valorEntrada;
    }

    public BigDecimal getValorSalida() {
        return valorSalida;
    }

    public BigDecimal getValorSaldo() {
        return valorSaldo;
    }

}
