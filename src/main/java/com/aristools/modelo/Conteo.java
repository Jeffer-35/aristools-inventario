package com.aristools.modelo;

import java.time.LocalDateTime;

/**
 * Conteo físico guardado en el historial. Puede ser general (toda la
 * bodega) o cíclico (solo una clase ABC), y a ciegas (la plantilla no
 * mostraba el stock del sistema, para que quien cuenta no se guíe por él).
 */
public class Conteo {

    private int idConteo;
    private final Bodega bodega;
    private final String usuario;        // nombre de usuario de quien contó
    private final LocalDateTime fecha;
    private final Character claseAbc;    // null: conteo general
    private final boolean aCiegas;
    private final int productosContados;
    private final int conDiferencia;

    public Conteo(int idConteo, Bodega bodega, String usuario, LocalDateTime fecha, Character claseAbc,
                  boolean aCiegas, int productosContados, int conDiferencia) {
        this.idConteo = idConteo;
        this.bodega = bodega;
        this.usuario = usuario;
        this.fecha = fecha;
        this.claseAbc = claseAbc;
        this.aCiegas = aCiegas;
        this.productosContados = productosContados;
        this.conDiferencia = conDiferencia;
    }

    public boolean esCiclico() {
        return claseAbc != null;
    }

    /** Texto que se muestra en el historial. */
    public String tipo() {
        return (esCiclico() ? "Cíclico (clase " + claseAbc + ")" : "General") + (aCiegas ? ", a ciegas" : "");
    }

    /**
     * Exactitud del inventario: porcentaje de productos cuyo conteo coincidió
     * con el sistema. Ejemplo: 20 contados y 2 con diferencia = 90 %.
     */
    public double exactitud() {
        return productosContados == 0 ? 0 : (productosContados - conDiferencia) * 100.0 / productosContados;
    }

    public int getIdConteo() { return idConteo; }
    public void setIdConteo(int idConteo) { this.idConteo = idConteo; }
    public Bodega getBodega() { return bodega; }
    public String getUsuario() { return usuario; }
    public LocalDateTime getFecha() { return fecha; }
    public Character getClaseAbc() { return claseAbc; }
    public boolean isACiegas() { return aCiegas; }
    public int getProductosContados() { return productosContados; }
    public int getConDiferencia() { return conDiferencia; }
}
