package com.aristools.controlador;

import com.aristools.util.Cambios;
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
                Auditoria.registrar("CREAR_BODEGA", "Creó la bodega " + b.getCodigo() + " " + b.getNombre());
            } else {
                Bodega anterior = bodegaDAO.buscarPorId(b.getIdBodega());
                bodegaDAO.actualizar(b);
                if (anterior != null) {
                    Cambios c = new Cambios()
                            .campo("código", anterior.getCodigo(), b.getCodigo())
                            .campo("nombre", anterior.getNombre(), b.getNombre())
                            .campo("ubicación", anterior.getUbicacion(), b.getUbicacion());
                    if (c.hayCambios()) {
                        Auditoria.registrar("MODIFICAR_BODEGA", "Bodega " + anterior.getCodigo() + ": " + c);
                    }
                }
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        try {
            Bodega anterior = bodegaDAO.buscarPorId(id);
            bodegaDAO.cambiarEstado(id, e);
            if (anterior != null) {
                Auditoria.registrar("ESTADO_BODEGA", "Bodega " + anterior.getCodigo() + " " + anterior.getNombre()
                        + ": " + anterior.getEstado() + " → " + e);
            }
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
    }
}
