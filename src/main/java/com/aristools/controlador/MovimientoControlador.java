package com.aristools.controlador;

import com.aristools.dao.CompraDAO;
import com.aristools.dao.MovimientoDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.OrdenCompra;
import com.aristools.modelo.Salida;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.Transferencia;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Registro de entradas (RF-006), salidas (RF-007) y transferencias (RF-008).
 * Valida con el modelo y delega la transacción a GestorInventario.
 */
public class MovimientoControlador {

    private final TipoMovimientoDAO tipoDAO = new TipoMovimientoDAO();
    private final GestorInventario gestor = new GestorInventario();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final CompraDAO compraDAO = new CompraDAO();

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
     * Costo de la última compra autorizada del producto, para proponerlo en
     * la pantalla de Entrada. null si el producto nunca se ha comprado.
     */
    public BigDecimal ultimoCostoCompra(int idProducto) {
        Sesion.exigir(Accion.REGISTRAR_MOVIMIENTOS);
        try {
            return movimientoDAO.ultimoCostoCompra(idProducto);
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
        validarOrden(entradas);
        prepararYRegistrar(entradas);
    }

    /**
     * Si la entrada recibe una orden de compra, revisa que coincida con ella:
     * orden abierta, mismo proveedor, productos que estén en la orden y sin
     * pasarse de lo que falta (contando lo que ya viene en entradas pendientes).
     */
    private void validarOrden(List<Entrada> entradas) {
        Sesion.exigir(Accion.REGISTRAR_MOVIMIENTOS);
        Map<Integer, OrdenCompra> ordenes = new HashMap<>();
        Map<String, Integer> pedidoAhora = new HashMap<>();   // "orden-producto" -> unidades en esta entrada
        try {
            for (Entrada e : entradas) {
                Integer idOrden = e.getIdOrden();
                if (idOrden == null) {
                    continue;
                }
                OrdenCompra orden = ordenes.get(idOrden);
                if (orden == null) {
                    orden = compraDAO.buscarOrden(idOrden);
                    if (orden == null) {
                        throw new NegocioException("La orden de compra elegida no existe.");
                    }
                    ordenes.put(idOrden, orden);
                }
                if (!orden.getEstado().estaAbierta()) {
                    throw new NegocioException("La orden " + orden.numero() + " está "
                            + orden.getEstado().texto().toLowerCase() + ": ya no recibe mercancía.");
                }
                if (e.getProveedor() == null || e.getProveedor().getIdProveedor() != orden.getProveedor().getIdProveedor()) {
                    throw new NegocioException("La orden " + orden.numero() + " es de " + orden.getProveedor().getRazonSocial()
                            + ": el proveedor de la entrada debe ser el mismo.");
                }
                int idProducto = e.getProducto().getIdProducto();
                LineaOrdenCompra linea = orden.linea(idProducto);
                if (linea == null) {
                    throw new NegocioException("El producto " + e.getProducto().getCodigo() + " no está en la orden "
                            + orden.numero() + ". Regístrelo en otra entrada, sin orden.");
                }
                String clave = idOrden + "-" + idProducto;
                int total = pedidoAhora.merge(clave, e.getCantidad(), Integer::sum);
                int disponible = linea.pendiente() - compraDAO.unidadesEnEspera(idOrden, idProducto);
                if (total > disponible) {
                    throw new NegocioException("De " + e.getProducto().getCodigo() + " a la orden " + orden.numero()
                            + " le faltan " + Math.max(disponible, 0) + " unidades"
                            + (linea.pendiente() > disponible ? " (las demás ya vienen en entradas pendientes)" : "")
                            + ". La entrada trae " + total + ".");
                }
            }
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
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
