package com.aristools.controlador;

import com.aristools.dao.BodegaDAO;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.sql.SQLException;
import java.util.List;

/** Gestión de bodegas (RF-004). Las inactivas no se usan en movimientos nuevos. */
public class BodegaControlador {

    private final BodegaDAO bodegaDAO = new BodegaDAO();

    public List<Bodega> listar(boolean inactivas) {
        try {
            return bodegaDAO.listar(inactivas);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Crea (idBodega = 0) o actualiza; el código debe ser único. */
    public void guardar(Bodega b) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        Validador.textoObligatorio(b.getCodigo(), "código");
        Validador.textoObligatorio(b.getNombre(), "nombre");
        try {
            if (b.getIdBodega() == 0) {
                b.setIdBodega(bodegaDAO.insertar(b));
            } else {
                bodegaDAO.actualizar(b);
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        try {
            bodegaDAO.cambiarEstado(id, e);
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
    }
}
