package com.aristools.controlador;

import com.aristools.dao.CompraDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.ProveedorDAO;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.OrdenCompra;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.SugerenciaCompra;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sugerencia de compra y órdenes de compra (Jefe de Compras y Administrador).
 *
 * Para cada producto activo calcula el consumo de los últimos 90 días, el
 * punto de reorden con los días de entrega de su proveedor y la cantidad a
 * pedir (la fórmula está en SugerenciaCompra). Con lo que se decide pedir
 * se genera la orden de compra para un proveedor.
 */
public class CompraControlador {

    /** Días hacia atrás que se analizan para calcular el consumo diario. */
    public static final int DIAS_ANALISIS = 90;

    private final CompraDAO compraDAO = new CompraDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final ProveedorDAO proveedorDAO = new ProveedorDAO();
    private final ParametroControlador parametros = new ParametroControlador();

    /**
     * Sugerencias de compra de los productos activos.
     *
     * @param idProveedor  solo los productos de ese proveedor (null = todos)
     * @param soloPorPedir true para mostrar solo los que llegaron al punto de reorden
     */
    public List<SugerenciaCompra> sugerencias(Integer idProveedor, boolean soloPorPedir) {
        Sesion.exigir(Accion.SUGERIR_COMPRA);
        try {
            int diasDefecto = entero("dias_entrega_defecto", 5);
            int minimoDefecto = entero("stock_minimo_defecto", 10);
            int maximoDefecto = entero("stock_maximo_defecto", 100);
            Map<Integer, Proveedor> proveedores = new HashMap<>();
            for (Proveedor p : proveedorDAO.listar(true)) {
                proveedores.put(p.getIdProveedor(), p);
            }
            Map<Integer, Producto> productos = new HashMap<>();
            for (Producto p : productoDAO.listar("", false)) {
                productos.put(p.getIdProducto(), p);
            }
            List<SugerenciaCompra> lista = new ArrayList<>();
            for (CompraDAO.DatosProducto d : compraDAO.datosSugerencia(LocalDate.now().minusDays(DIAS_ANALISIS).atStartOfDay())) {
                Producto producto = productos.get(d.idProducto);
                Proveedor proveedor = d.idProveedor == null ? null : proveedores.get(d.idProveedor);
                if (producto == null || (idProveedor != null && (proveedor == null || proveedor.getIdProveedor() != idProveedor))) {
                    continue;
                }
                int dias = proveedor == null ? diasDefecto : proveedor.diasEntregaEfectivos(diasDefecto);
                BigDecimal costo = d.ultimoCosto != null ? d.ultimoCosto
                        : producto.getCostoPromedio().signum() > 0 ? producto.getCostoPromedio() : producto.getCostoReferencia();
                SugerenciaCompra s = new SugerenciaCompra(producto, proveedor, d.stock, d.salidas, DIAS_ANALISIS, dias,
                        producto.minimoEfectivo(minimoDefecto), producto.maximoEfectivo(maximoDefecto), costo);
                if (!soloPorPedir || s.hayQuePedir()) {
                    lista.add(s);
                }
            }
            // Primero lo que hay que pedir, de mayor a menor valor
            lista.sort((a, b) -> b.valorEstimado().compareTo(a.valorEstimado()));
            return lista;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Guarda la orden de compra (proveedor y productos) y la registra en la
     * bitácora. Devuelve la orden con su número, lista para imprimir.
     */
    public OrdenCompra generarOrden(Proveedor proveedor, List<LineaOrdenCompra> lineas, String observacion) {
        Sesion.exigir(Accion.SUGERIR_COMPRA);
        OrdenCompra orden = new OrdenCompra(proveedor, Sesion.getUsuario(), lineas);
        orden.setObservacion(observacion == null || observacion.isBlank() ? null : observacion.trim());
        orden.validar();
        try {
            compraDAO.insertar(orden);
            Auditoria.registrar("ORDEN_COMPRA", "Generó la orden " + orden.numero() + " a " + proveedor.getRazonSocial()
                    + " con " + lineas.size() + " producto(s) por " + Formato.pesos(orden.total()));
            return orden;
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Días de entrega del parámetro general (se muestran en la pantalla). */
    public int diasEntregaPorDefecto() {
        return entero("dias_entrega_defecto", 5);
    }

    private int entero(String clave, int porDefecto) {
        try {
            return Integer.parseInt(parametros.valor(clave, String.valueOf(porDefecto)).trim());
        } catch (NumberFormatException e) {
            return porDefecto;
        }
    }
}
