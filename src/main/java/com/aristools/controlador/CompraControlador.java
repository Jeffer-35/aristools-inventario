package com.aristools.controlador;

import com.aristools.dao.CompraDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.ProveedorDAO;
import com.aristools.modelo.EstadoOrden;
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
 *
 * Seguimiento: la orden nace Pendiente; cuando el Jefe de Compras autoriza
 * la entrada que la recibe pasa a Recibida en parte o Recibida. Si el
 * proveedor no va a entregar lo que falta, la orden se anula con un motivo.
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

    /**
     * Órdenes de compra para la pantalla de seguimiento.
     *
     * @param soloAbiertas true = solo las que todavía esperan mercancía
     */
    public List<OrdenCompra> listarOrdenes(boolean soloAbiertas) {
        Sesion.exigir(Accion.SUGERIR_COMPRA);
        try {
            return compraDAO.listarOrdenes(soloAbiertas, null);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Órdenes abiertas de un proveedor, para elegir cuál se recibe en la
     * pantalla de Entrada (la usa el Almacenista).
     */
    public List<OrdenCompra> ordenesAbiertasDe(int idProveedor) {
        Sesion.exigir(Accion.REGISTRAR_MOVIMIENTOS);
        try {
            return compraDAO.listarOrdenes(true, idProveedor);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Anula una orden que todavía espera mercancía. Lo que ya llegó queda en
     * el inventario; lo que faltaba deja de esperarse. Reglas: motivo
     * obligatorio, la orden debe estar abierta y no puede tener entradas
     * esperando autorización (primero se autorizan o se rechazan).
     */
    public void anularOrden(int idOrden, String motivo) {
        Sesion.exigir(Accion.SUGERIR_COMPRA);
        if (motivo == null || motivo.trim().length() < 5) {
            throw new NegocioException("Escriba el motivo de la anulación (mínimo 5 caracteres), "
                    + "por ejemplo: el proveedor no tiene el producto.");
        }
        try {
            OrdenCompra orden = compraDAO.buscarOrden(idOrden);
            if (orden == null) {
                throw new NegocioException("La orden de compra no existe.");
            }
            if (!orden.getEstado().estaAbierta()) {
                throw new NegocioException("La orden " + orden.numero() + " ya está " + orden.getEstado().texto().toLowerCase()
                        + ": no se puede anular.");
            }
            int enEspera = compraDAO.entradasEnEspera(idOrden);
            if (enEspera > 0) {
                throw new NegocioException("La orden " + orden.numero() + " tiene " + enEspera + " entrada(s) esperando "
                        + "autorización. Autorícelas o recházelas antes de anular la orden.");
            }
            if (!compraDAO.anular(idOrden, motivo.trim())) {
                throw new NegocioException("La orden " + orden.numero() + " cambió mientras tanto. Actualice la lista.");
            }
            Auditoria.registrar("ANULAR_ORDEN", "Anuló la orden " + orden.numero() + " de "
                    + orden.getProveedor().getRazonSocial() + " (" + orden.getEstado().texto().toLowerCase()
                    + ", recibido " + orden.porcentajeRecibido() + " %). Motivo: " + motivo.trim());
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Estados que se pueden elegir en el filtro de la pantalla. */
    public static EstadoOrden[] estados() {
        return EstadoOrden.values();
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
