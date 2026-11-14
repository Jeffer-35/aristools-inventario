package com.aristools.controlador;

import com.aristools.dao.BitacoraDAO;
import com.aristools.modelo.RegistroBitacora;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

/** Consulta de la bitácora de auditoría (solo el Administrador del Sistema). */
public class BitacoraControlador {

    private final BitacoraDAO bitacoraDAO = new BitacoraDAO();

    public List<RegistroBitacora> consultar(LocalDate desde, LocalDate hasta, String accion, String texto) {
        Sesion.exigir(Accion.VER_BITACORA);
        if (desde != null && hasta != null && desde.isAfter(hasta)) {
            throw new NegocioException("La fecha inicial no puede ser posterior a la final.");
        }
        try {
            return bitacoraDAO.consultar(desde, hasta, accion, texto);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public List<String> listarAcciones() {
        return Auditoria.ACCIONES;
    }
}
