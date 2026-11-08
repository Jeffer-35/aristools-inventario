package com.aristools.controlador;

import com.aristools.conexion.ConexionDB;
import com.aristools.dao.MovimientoDAO;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * Autorizar o rechazar entradas (RF-010, Jefe de Compras) y salidas
 * críticas y ajustes negativos (RF-011, Supervisor de Bodega).
 */
public class AutorizacionControlador {

    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final GestorInventario gestor = new GestorInventario();

    /** Bandeja de pendientes: ENTRADA para el Jefe de Compras, SALIDA para el Supervisor. */
    public List<Movimiento> listarPendientes(Naturaleza n) {
        Sesion.exigir(accionPara(n));
        try {
            return movimientoDAO.listarPendientes(n);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Aprueba el movimiento y aplica su efecto en el stock, todo en una transacción. */
    public void autorizar(int idMovimiento) {
        resolver(idMovimiento, true, null);
    }

    /** Rechaza el movimiento con su motivo; el stock no cambia. */
    public void rechazar(int idMovimiento, String motivo) {
        resolver(idMovimiento, false, motivo);
    }

    // Pasos: 1) bloquear el movimiento, 2) verificar permiso, segregación y estado,
    // 3) aplicar el stock si se autoriza, 4) guardar la decisión, 5) COMMIT.
    private void resolver(int idMovimiento, boolean autorizar, String motivo) {
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                Movimiento m = movimientoDAO.buscarParaResolver(con, idMovimiento);
                if (m == null) {
                    throw new NegocioException("El movimiento " + idMovimiento + " no existe.");
                }
                Sesion.exigir(accionPara(m.getTipo().getNaturaleza()));
                if (autorizar) {
                    m.autorizar(Sesion.getUsuario());   // segregación de funciones y rol autorizador
                    gestor.aplicar(con, m);             // vuelve a verificar la existencia (RF-011)
                } else {
                    m.rechazar(Sesion.getUsuario(), motivo);
                }
                movimientoDAO.actualizarResolucion(con, m);
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    private Accion accionPara(Naturaleza n) {
        return n == Naturaleza.ENTRADA ? Accion.AUTORIZAR_ENTRADAS : Accion.AUTORIZAR_SALIDAS;
    }
}
