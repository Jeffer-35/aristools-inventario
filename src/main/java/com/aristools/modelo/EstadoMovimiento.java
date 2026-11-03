package com.aristools.modelo;

/**
 * Ciclo de vida de un movimiento: solo los AUTORIZADOS cambian el stock.
 */
public enum EstadoMovimiento {
    PENDIENTE,
    AUTORIZADO,
    RECHAZADO
}
