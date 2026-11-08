package com.aristools.controlador;

import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Salida;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.Transferencia;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import java.sql.SQLException;
import java.util.List;

/**
 * Registro de entradas (RF-006), salidas (RF-007) y transferencias (RF-008).
 * Valida con el modelo y delega la transacción a GestorInventario.
 */
public class MovimientoControlador {

    private final TipoMovimientoDAO tipoDAO = new TipoMovimientoDAO();
    private final GestorInventario gestor = new GestorInventario();

    /** Tipos para el combo de cada pestaña (sin los ajustes, que solo genera el conteo). */
    public List<TipoMovimiento> listarTipos(Naturaleza n) {
        try {
            List<TipoMovimiento> tipos = tipoDAO.listarPorNaturaleza(n);
            tipos.removeIf(t -> t.getNombre().startsWith("Ajuste"));
            return tipos;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Entrada con uno o varios productos (mockup Figura 15): se crea un
     * movimiento por producto, todos Pendientes hasta que el Jefe de Compras
     * los autorice.
     */
    public void registrarEntradas(List<Entrada> entradas) {
        if (entradas == null || entradas.isEmpty()) {
            throw new NegocioException("Agregue al menos un producto a la entrada.");
        }
        prepararYRegistrar(entradas);
    }

    /** Consumo interno se aplica de inmediato; las demás salidas quedan Pendientes. */
    public void registrarSalida(Salida s) {
        prepararYRegistrar(List.of(s));
    }

    /** La transferencia se aplica de inmediato y no cambia el stock total. */
    public void registrarTransferencia(Transferencia t) {
        prepararYRegistrar(List.of(t));
    }

    // Permiso, responsable y validaciones del modelo; luego la transacción.
    private void prepararYRegistrar(List<? extends Movimiento> movimientos) {
        Sesion.exigir(Accion.REGISTRAR_MOVIMIENTOS);
        for (Movimiento m : movimientos) {
            m.setUsuarioRegistra(Sesion.getUsuario());
            m.validar();   // polimorfismo: cada subclase valida sus propias reglas
        }
        gestor.registrar(movimientos);
    }
}
