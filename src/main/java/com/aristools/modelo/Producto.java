package com.aristools.modelo;

import java.math.BigDecimal;

/**
 * Artículo del inventario. No guarda su stock: la cantidad disponible se
 * obtiene sumando sus existencias por bodega (sección 4.3.1).
 */
public class Producto {

    private int idProducto;
    private String codigo;
    private String nombre;
    private String descripcion;
    private Categoria categoria;
    private String unidadMedida;
    private BigDecimal costoReferencia;
    private Integer stockMinimo;   // null = usa el umbral general
    private Integer stockMaximo;   // null = usa el umbral general
    private EstadoRegistro estado;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Producto() {
        this.estado = EstadoRegistro.ACTIVO;
        this.costoReferencia = BigDecimal.ZERO;
    }

    /** Constructor con todos los atributos. */
    public Producto(int idProducto, String codigo, String nombre, String descripcion, Categoria categoria, String unidadMedida, BigDecimal costoReferencia, Integer stockMinimo, Integer stockMaximo, EstadoRegistro estado) {
        this.idProducto = idProducto;
        this.codigo = codigo;
        this.nombre = nombre;
        this.descripcion = descripcion;
        this.categoria = categoria;
        this.unidadMedida = unidadMedida;
        this.costoReferencia = costoReferencia;
        this.stockMinimo = stockMinimo;
        this.stockMaximo = stockMaximo;
        this.estado = estado;
    }

    /**
     * Valida los umbrales propios del producto (RF-003): no pueden ser
     * negativos y el mínimo no puede superar al máximo.
     * Si alguno está vacío se considera válido, porque se usará el general.
     */
    public boolean umbralesValidos() {
        if (stockMinimo != null && stockMinimo < 0) {
            return false;
        }
        if (stockMaximo != null && stockMaximo < 0) {
            return false;
        }
        return stockMinimo == null || stockMaximo == null || stockMinimo <= stockMaximo;
    }

    /** Umbral mínimo que aplica: el propio o, si no tiene, el general (RF-012). */
    public int minimoEfectivo(int defecto) {
        return stockMinimo != null ? stockMinimo : defecto;
    }

    /** Umbral máximo que aplica: el propio o, si no tiene, el general (RF-012). */
    public int maximoEfectivo(int defecto) {
        return stockMaximo != null ? stockMaximo : defecto;
    }

    /** @return true si el producto puede usarse en movimientos nuevos */
    public boolean estaActivo() {
        return estado == EstadoRegistro.ACTIVO;
    }

    // ----- get / set de cada atributo -----

    public int getIdProducto() {
        return idProducto;
    }

    public void setIdProducto(int idProducto) {
        this.idProducto = idProducto;
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

    public String getDescripcion() {
        return descripcion;
    }

    public void setDescripcion(String descripcion) {
        this.descripcion = descripcion;
    }

    public Categoria getCategoria() {
        return categoria;
    }

    public void setCategoria(Categoria categoria) {
        this.categoria = categoria;
    }

    public String getUnidadMedida() {
        return unidadMedida;
    }

    public void setUnidadMedida(String unidadMedida) {
        this.unidadMedida = unidadMedida;
    }

    public BigDecimal getCostoReferencia() {
        return costoReferencia;
    }

    public void setCostoReferencia(BigDecimal costoReferencia) {
        this.costoReferencia = costoReferencia;
    }

    public Integer getStockMinimo() {
        return stockMinimo;
    }

    public void setStockMinimo(Integer stockMinimo) {
        this.stockMinimo = stockMinimo;
    }

    public Integer getStockMaximo() {
        return stockMaximo;
    }

    public void setStockMaximo(Integer stockMaximo) {
        this.stockMaximo = stockMaximo;
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
        return codigo + " — " + nombre;
    }

}
