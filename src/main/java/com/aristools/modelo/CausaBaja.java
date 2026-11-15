package com.aristools.modelo;

/**
 * Causas estándar por las que se pierde mercancía. Se piden en las mermas y
 * en los productos dañados o vencidos; el faltante lo asigna el sistema a
 * los ajustes negativos del conteo físico.
 *
 * Al ser una lista fija (y no un texto libre), el reporte de pérdidas puede
 * totalizar por causa. El detalle de lo que pasó se sigue escribiendo en el
 * motivo del movimiento.
 */
public enum CausaBaja {

    VENCIDO("Vencido"),
    ROTO_O_GOLPEADO("Roto o golpeado en bodega"),
    DANO_TRANSPORTE("Daño en transporte"),
    HUMEDAD("Humedad"),
    DEFECTO_FABRICA("Defecto de fábrica"),
    FALTANTE("Faltante sin explicación"),
    OTRO("Otro");

    private final String descripcion;

    CausaBaja(String descripcion) {
        this.descripcion = descripcion;
    }

    public String getDescripcion() {
        return descripcion;
    }

    /** Texto que muestran los combo box y los reportes. */
    @Override
    public String toString() {
        return descripcion;
    }
}
