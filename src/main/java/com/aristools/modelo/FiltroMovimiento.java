package com.aristools.modelo;

import java.time.LocalDate;

/**
 * Filtros opcionales de los reportes de movimientos (RF-014). Un valor null
 * significa "todos".
 */
public class FiltroMovimiento {

    private Integer idProducto;
    private Integer idBodega;
    private Integer idProveedor;
    private Integer idTipoMovimiento;
    private LocalDate desde;
    private LocalDate hasta;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public FiltroMovimiento() {
    }

    /** Constructor con todos los atributos. */
    public FiltroMovimiento(Integer idProducto, Integer idBodega, Integer idProveedor, Integer idTipoMovimiento, LocalDate desde, LocalDate hasta) {
        this.idProducto = idProducto;
        this.idBodega = idBodega;
        this.idProveedor = idProveedor;
        this.idTipoMovimiento = idTipoMovimiento;
        this.desde = desde;
        this.hasta = hasta;
    }

    // ----- get / set de cada atributo -----

    public Integer getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(Integer idProducto) {
        this.idProducto = idProducto;
    }

    public Integer getIdBodega() {
        return idBodega;
    }

    public void setIdBodega(Integer idBodega) {
        this.idBodega = idBodega;
    }

    public Integer getIdProveedor() {
        return idProveedor;
    }

    public void setIdProveedor(Integer idProveedor) {
        this.idProveedor = idProveedor;
    }

    public Integer getIdTipoMovimiento() {
        return idTipoMovimiento;
    }

    public void setIdTipoMovimiento(Integer idTipoMovimiento) {
        this.idTipoMovimiento = idTipoMovimiento;
    }

    public LocalDate getDesde() {
        return desde;
    }

    public void setDesde(LocalDate desde) {
        this.desde = desde;
    }

    public LocalDate getHasta() {
        return hasta;
    }

    public void setHasta(LocalDate hasta) {
        this.hasta = hasta;
    }

}
