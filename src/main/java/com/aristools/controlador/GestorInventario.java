package com.aristools.controlador;

import com.aristools.conexion.ConexionDB;
import com.aristools.dao.ExistenciaDAO;
import com.aristools.dao.MovimientoDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.modelo.CambioStock;
import com.aristools.modelo.EstadoMovimiento;
import com.aristools.modelo.Movimiento;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Corazón del inventario: guarda movimientos y aplica sus cambios de stock
 * dentro de transacciones (COMMIT / ROLLBACK, RNF-003).
 *
 * Gracias al polimorfismo no pregunta si el movimiento es una entrada, una
 * salida, una transferencia o un ajuste: le pide a cada uno su lista de
 * cambios con calcularCambiosStock() y la aplica.
 *
 * Kardex valorizado: antes de mover el stock valora el movimiento con el
 * método del costo promedio ponderado. También aquí hay polimorfismo: cada
 * movimiento dice a qué costo se valora y cómo queda el promedio.
 */
public class GestorInventario {

    private final ExistenciaDAO existenciaDAO = new ExistenciaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final AlertaControlador alertaControlador = new AlertaControlador();

    /**
     * Registra una lista de movimientos en UNA sola transacción: se guardan
     * todos o ninguno. Los que no requieren autorización se aplican de
     * inmediato; los demás quedan Pendientes sin tocar el stock.
     * Lo usan MovimientoControlador y ConteoControlador.
     */
    public void registrar(List<? extends Movimiento> movimientos) {
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                for (Movimiento m : movimientos) {
                    if (!m.requiereAutorizacion()) {
                        m.setEstado(EstadoMovimiento.AUTORIZADO);   // consumo interno, transferencia, ajuste +
                    } else {
                        verificarStockDisponible(con, m);        // una salida pendiente también debe alcanzar
                    }
                    movimientoDAO.insertar(con, m);
                    if (m.getEstado() == EstadoMovimiento.AUTORIZADO) {
                        aplicar(con, m);
                    }
                }
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();                                   // no queda nada a medias
                throw e;
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Aplica los cambios de stock de un movimiento autorizado. Primero lo
     * valora (costo promedio ponderado); luego bloquea cada existencia
     * (SELECT ... FOR UPDATE) y verifica que ninguna quede negativa.
     * Debe llamarse dentro de una transacción abierta.
     */
    public void aplicar(Connection con, Movimiento m) throws SQLException {
        int idProducto = m.getProducto().getIdProducto();
        valorar(con, m);
        for (CambioStock c : ordenados(m.calcularCambiosStock())) {
            int actual = existenciaDAO.bloquear(con, idProducto, c.getBodega().getIdBodega());
            if (actual + c.getDelta() < 0) {
                throw new NegocioException("Stock insuficiente en " + c.getBodega().getNombre()
                        + ": hay " + actual + " unidades y se necesitan " + (-c.getDelta()) + ".");
            }
            existenciaDAO.ajustar(con, idProducto, c.getBodega().getIdBodega(), c.getDelta());
        }
        alertaControlador.evaluar(con, idProducto);
    }

    /**
     * Kardex valorizado. Con el stock y el costo promedio de ANTES del movimiento:
     *   1) guarda en el movimiento el costo unitario con el que se valora;
     *   2) si es una compra, recalcula y guarda el costo promedio del producto.
     * La fila del producto queda bloqueada hasta el COMMIT, así dos compras
     * simultáneas no calculan el promedio con los mismos datos.
     */
    private void valorar(Connection con, Movimiento m) throws SQLException {
        int idProducto = m.getProducto().getIdProducto();
        BigDecimal promedio = productoDAO.bloquearCostoPromedio(con, idProducto);
        int stock = existenciaDAO.stockTotal(con, idProducto);
        BigDecimal costo = m.costoParaValorar(promedio);                 // polimorfismo
        BigDecimal nuevoPromedio = m.nuevoCostoPromedio(stock, promedio); // polimorfismo
        m.setCostoUnitario(costo);
        movimientoDAO.guardarCosto(con, m.getIdMovimiento(), costo);
        if (nuevoPromedio.compareTo(promedio) != 0) {
            productoDAO.actualizarCostoPromedio(con, idProducto, nuevoPromedio);
        }
    }

    // Para movimientos pendientes que restan stock: la cantidad no puede superar la existencia (RF-007).
    private void verificarStockDisponible(Connection con, Movimiento m) throws SQLException {
        for (CambioStock c : m.calcularCambiosStock()) {
            if (c.getDelta() < 0) {
                int actual = existenciaDAO.bloquear(con, m.getProducto().getIdProducto(), c.getBodega().getIdBodega());
                if (actual + c.getDelta() < 0) {
                    throw new NegocioException("La cantidad supera la existencia en " + c.getBodega().getNombre()
                            + ": hay " + actual + " unidades disponibles.");
                }
            }
        }
    }

    // Bloquea siempre en el mismo orden (por id de bodega) para evitar bloqueos cruzados.
    private List<CambioStock> ordenados(List<CambioStock> cambios) {
        List<CambioStock> lista = new ArrayList<>(cambios);
        lista.sort(Comparator.comparingInt(c -> c.getBodega().getIdBodega()));
        return lista;
    }
}
