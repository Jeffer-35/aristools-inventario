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
            // Lotes vencidos o por vencer que todavía tienen unidades en bodega
            int aviso = new ParametroControlador().diasAvisoVencimiento();
            java.time.LocalDate hoy = java.time.LocalDate.now();
            int porVencer = 0;
            for (com.aristools.modelo.LoteVencimiento l : new com.aristools.dao.ReporteDAO().lotesConSaldo()) {
                if (!com.aristools.modelo.LoteVencimiento.VIGENTE.equals(l.estado(hoy, aviso))) {
                    porVencer++;
                }
            }
            datos.put("Lotes por vencer", porVencer);
            return datos;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }
}
