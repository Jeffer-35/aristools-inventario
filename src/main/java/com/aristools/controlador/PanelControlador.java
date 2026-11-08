package com.aristools.controlador;

import com.aristools.dao.AlertaDAO;
import com.aristools.dao.MovimientoDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.modelo.TipoAlerta;
import com.aristools.util.NegocioException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

/** Datos de las tarjetas del panel principal (mockup Figura 10). */
public class PanelControlador {

    private final ProductoDAO productoDAO = new ProductoDAO();
    private final AlertaDAO alertaDAO = new AlertaDAO();
    private final MovimientoDAO movimientoDAO = new MovimientoDAO();

    /** @return mapa ordenado: título de la tarjeta → número a mostrar */
    public Map<String, Integer> resumen() {
        try {
            Map<String, Integer> datos = new LinkedHashMap<>();
            datos.put("Productos activos", productoDAO.contarActivos());
            datos.put("Bajo stock", alertaDAO.contarActivas(TipoAlerta.BAJO_STOCK));
            datos.put("Sobrestock", alertaDAO.contarActivas(TipoAlerta.SOBRESTOCK));
            datos.put("Movimientos pendientes", movimientoDAO.contarPendientes());
            return datos;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }
}
