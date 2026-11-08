package com.aristools.controlador;

import com.aristools.dao.AlertaDAO;
import com.aristools.dao.ExistenciaDAO;
import com.aristools.dao.ParametroDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.modelo.Alerta;
import com.aristools.modelo.Producto;
import com.aristools.modelo.TipoAlerta;
import com.aristools.util.NegocioException;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Alertas de stock mínimo y máximo (RF-012).
 * Bajo stock: stock total ≤ mínimo. Sobrestock: stock total > máximo.
 * Si el producto no tiene umbrales propios se usan los generales (RF-015).
 */
public class AlertaControlador {

    private final ExistenciaDAO existenciaDAO = new ExistenciaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final ParametroDAO parametroDAO = new ParametroDAO();
    private final AlertaDAO alertaDAO = new AlertaDAO();

    /**
     * Evalúa el producto dentro de la transacción del movimiento que acaba de
     * cambiar su stock, y crea, actualiza o resuelve su alerta.
     */
    public void evaluar(Connection con, int idProducto) throws SQLException {
        Producto p = productoDAO.buscarPorId(idProducto);
        int total = existenciaDAO.stockTotal(con, idProducto);
        int minimo = p.minimoEfectivo(parametroDAO.obtenerEntero(con, "stock_minimo_defecto"));
        int maximo = p.maximoEfectivo(parametroDAO.obtenerEntero(con, "stock_maximo_defecto"));
        if (total <= minimo) {
            alertaDAO.guardarOActualizar(con, nueva(p, TipoAlerta.BAJO_STOCK, total, minimo));
        } else if (total > maximo) {
            alertaDAO.guardarOActualizar(con, nueva(p, TipoAlerta.SOBRESTOCK, total, maximo));
        } else {
            alertaDAO.resolver(con, idProducto);   // el stock volvió a la normalidad
        }
    }

    /** Alertas activas que muestra el panel principal (visibles para todos los roles). */
    public List<Alerta> listarActivas() {
        try {
            return alertaDAO.listarActivas();
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    // Crea el objeto Alerta con la fecha actual.
    private Alerta nueva(Producto p, TipoAlerta tipo, int stock, int umbral) {
        Alerta a = new Alerta();
        a.setProducto(p);
        a.setTipo(tipo);
        a.setStockDetectado(stock);
        a.setUmbral(umbral);
        a.setFechaGeneracion(LocalDateTime.now());
        return a;
    }
}
