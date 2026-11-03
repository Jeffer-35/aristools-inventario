package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.util.List;

/**
 * Salida de mercancía (RF-007): consumo interno, merma, producto dañado o
 * vencido y devolución a proveedor. El consumo interno se aplica de
 * inmediato; las demás esperan al Supervisor de Bodega (RF-011).
 */
public class Salida extends Movimiento {

    private Bodega bodegaOrigen;
    private Proveedor proveedor;   // solo en devolución a proveedor

    public Salida() {
        super();
    }

    public Salida(Producto producto, TipoMovimiento tipo, Usuario usuarioRegistra, int cantidad,
                  Bodega bodegaOrigen, Proveedor proveedor) {
        super(producto, tipo, usuarioRegistra, cantidad);
        this.bodegaOrigen = bodegaOrigen;
        this.proveedor = proveedor;
    }

    @Override
    public void validar() {
        validarComunes();
        if (tipo.getNaturaleza() != Naturaleza.SALIDA) {
            throw new NegocioException("El tipo " + tipo.getNombre() + " no es una salida.");
        }
        if (bodegaOrigen == null || !bodegaOrigen.estaActiva()) {
            throw new NegocioException("Seleccione una bodega de origen activa.");
        }
        if (tipo.exigeMotivo() && (motivo == null || motivo.isBlank())) {
            throw new NegocioException("El motivo es obligatorio en merma, producto dañado o vencido.");
        }
        if ("Devolución a proveedor".equalsIgnoreCase(tipo.getNombre())
                && (proveedor == null || !proveedor.estaActivo())) {
            throw new NegocioException("La devolución a proveedor exige un proveedor activo.");
        }
    }

    /** Una salida resta la cantidad en la bodega de origen. */
    @Override
    public List<CambioStock> calcularCambiosStock() {
        return List.of(new CambioStock(bodegaOrigen, -cantidad));
    }

    public Bodega getBodegaOrigen() { return bodegaOrigen; }
    public void setBodegaOrigen(Bodega bodegaOrigen) { this.bodegaOrigen = bodegaOrigen; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
}
