package com.aristools.modelo;

import java.time.LocalDateTime;

/** Una línea de la bitácora de auditoría: quién hizo qué y cuándo. */
public class RegistroBitacora {

    private final int idBitacora;
    private final LocalDateTime fecha;
    private final String nombreUsuario;
    private final String accion;
    private final String detalle;

    public RegistroBitacora(int idBitacora, LocalDateTime fecha, String nombreUsuario, String accion, String detalle) {
        this.idBitacora = idBitacora;
        this.fecha = fecha;
        this.nombreUsuario = nombreUsuario;
        this.accion = accion;
        this.detalle = detalle;
    }

    public int getIdBitacora() {
        return idBitacora;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public String getAccion() {
        return accion;
    }

    public String getDetalle() {
        return detalle;
    }
}
