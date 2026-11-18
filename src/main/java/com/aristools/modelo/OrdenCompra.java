package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Orden de compra: el pedido que se le envía a un proveedor con los
 * productos y cantidades que sugiere el sistema (o que ajusta el Jefe de
 * Compras). Su número es OC- y el id con seis cifras, por ejemplo OC-000003.
 *
 * La orden es solo el pedido: el stock cambia cuando llega la mercancía y se
 * registra la Entrada (compra) con la factura del proveedor. Esa entrada
 * puede indicar qué orden recibe; al autorizarla, la orden pasa a
 * "Recibida en parte" o "Recibida" según lo que haya llegado (seguimiento).
 */
public class OrdenCompra {

    private int idOrden;
    private final Proveedor proveedor;
    private final Usuario usuario;
    private final List<LineaOrdenCompra> lineas;
    private LocalDateTime fecha;
    private String observacion;
    private EstadoOrden estado = EstadoOrden.PENDIENTE;
    private String motivoAnulacion;

    public OrdenCompra(Proveedor proveedor, Usuario usuario, List<LineaOrdenCompra> lineas) {
        this.proveedor = proveedor;
        this.usuario = usuario;
        this.lineas = List.copyOf(lineas);
        this.fecha = LocalDateTime.now();
    }

    /**
     * Reglas de la orden: proveedor activo, al menos un producto, cantidades
     * mayores que cero, productos activos y cada producto una sola vez.
     */
    public void validar() {
        if (proveedor == null || !proveedor.estaActivo()) {
            throw new NegocioException("Seleccione un proveedor activo para la orden de compra.");
        }
        if (lineas.isEmpty()) {
            throw new NegocioException("La orden de compra no tiene productos. Escriba la cantidad a pedir "
                    + "en al menos un producto.");
        }
        Set<Integer> vistos = new HashSet<>();
        for (LineaOrdenCompra l : lineas) {
            if (l.getCantidad() <= 0) {
                throw new NegocioException("La cantidad de " + l.getProducto().getCodigo() + " debe ser mayor que cero.");
            }
            if (!l.getProducto().estaActivo()) {
                throw new NegocioException("El producto " + l.getProducto().getCodigo() + " está inactivo.");
            }
            if (!vistos.add(l.getProducto().getIdProducto())) {
                throw new NegocioException("El producto " + l.getProducto().getCodigo() + " está repetido en la orden.");
            }
        }
    }

    /** Valor estimado de la orden: suma de los subtotales. */
    public BigDecimal total() {
        BigDecimal total = BigDecimal.ZERO.setScale(2);
        for (LineaOrdenCompra l : lineas) {
            total = total.add(l.subtotal());
        }
        return total;
    }

    /**
     * Estado según lo recibido de cada producto: nada = PENDIENTE, todo =
     * RECIBIDA y lo demás = PARCIAL. (ANULADA no se calcula: la decide el
     * Jefe de Compras.)
     */
    public static EstadoOrden estadoSegunRecibido(List<LineaOrdenCompra> lineas) {
        boolean algo = false;
        boolean todo = true;
        for (LineaOrdenCompra l : lineas) {
            if (l.getCantidadRecibida() > 0) {
                algo = true;
            }
            if (l.pendiente() > 0) {
                todo = false;
            }
        }
        if (todo && algo) {
            return EstadoOrden.RECIBIDA;
        }
        return algo ? EstadoOrden.PARCIAL : EstadoOrden.PENDIENTE;
    }

    /** Busca la línea de un producto en la orden; null si ese producto no se pidió. */
    public LineaOrdenCompra linea(int idProducto) {
        for (LineaOrdenCompra l : lineas) {
            if (l.getProducto().getIdProducto() == idProducto) {
                return l;
            }
        }
        return null;
    }

    /** Unidades pedidas en total (todas las líneas). */
    public int unidadesPedidas() {
        return lineas.stream().mapToInt(LineaOrdenCompra::getCantidad).sum();
    }

    /** Unidades que ya llegaron en total. */
    public int unidadesRecibidas() {
        return lineas.stream().mapToInt(l -> Math.min(l.getCantidadRecibida(), l.getCantidad())).sum();
    }

    /** Porcentaje recibido de la orden (0 a 100), por unidades. */
    public int porcentajeRecibido() {
        int pedidas = unidadesPedidas();
        return pedidas == 0 ? 0 : unidadesRecibidas() * 100 / pedidas;
    }

    /** Texto de la orden en las listas, por ejemplo "OC-000003 · 17/11/2026 · Pendiente". */
    @Override
    public String toString() {
        return numero() + " · " + fecha.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " · " + estado.texto();
    }

    /** Número del documento, por ejemplo OC-000003. */
    public String numero() {
        return "OC-" + String.format("%06d", idOrden);
    }

    public int getIdOrden() {
        return idOrden;
    }

    public void setIdOrden(int idOrden) {
        this.idOrden = idOrden;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public List<LineaOrdenCompra> getLineas() {
        return lineas;
    }

    public LocalDateTime getFecha() {
        return fecha;
    }

    public void setFecha(LocalDateTime fecha) {
        this.fecha = fecha;
    }

    public String getObservacion() {
        return observacion;
    }

    public void setObservacion(String observacion) {
        this.observacion = observacion;
    }

    public EstadoOrden getEstado() {
        return estado;
    }

    public void setEstado(EstadoOrden estado) {
        this.estado = estado;
    }

    public String getMotivoAnulacion() {
        return motivoAnulacion;
    }

    public void setMotivoAnulacion(String motivoAnulacion) {
        this.motivoAnulacion = motivoAnulacion;
    }
}
