package com.aristools.vista;

/**
 * Pantallas que deben volver a consultar la base cada vez que se abren
 * desde el menú (así muestran siempre datos actualizados).
 */
public interface Recargable {

    void recargar();
}
