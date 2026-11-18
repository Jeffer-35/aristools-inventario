package com.aristools.modelo;

/**
 * Ciclo de vida de una orden de compra.
 *
 *   PENDIENTE  todavía no ha llegado nada
 *   PARCIAL    llegó una parte (por ejemplo, el proveedor envió la mitad)
 *   RECIBIDA   llegó todo lo pedido
 *   ANULADA    se canceló; lo que faltaba ya no se espera
 *
 * La mercancía "llega" cuando el Jefe de Compras autoriza la entrada que
 * recibe la orden; mientras la entrada esté pendiente, la orden no cambia.
 */
public enum EstadoOrden {
    PENDIENTE,
    PARCIAL,
    RECIBIDA,
    ANULADA;

    /** Abierta = todavía se puede recibir mercancía con ella. */
    public boolean estaAbierta() {
        return this == PENDIENTE || this == PARCIAL;
    }

    /** Texto para las pantallas y los PDF. */
    public String texto() {
        return switch (this) {
            case PENDIENTE -> "Pendiente";
            case PARCIAL -> "Recibida en parte";
            case RECIBIDA -> "Recibida";
            case ANULADA -> "Anulada";
        };
    }
}
