package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.util.List;

/**
 * Entrada de mercancía por compra o devolución (RF-006).
 * Queda Pendiente hasta que el Jefe de Compras la autoriza (RF-010).
 * La compra exige proveedor activo y documento de soporte (reglas del RF-006).
 */
public class Entrada extends Movimiento {

    private Bodega bodegaDestino;
    private Proveedor proveedor;   // obligatorio en compras

    public Entrada() {
        super();
    }

    public Entrada(Producto producto, TipoMovimiento tipo, Usuario usuarioRegistra, int cantidad,
                   Bodega bodegaDestino, Proveedor proveedor) {
        super(producto, tipo, usuarioRegistra, cantidad);
        this.bodegaDestino = bodegaDestino;
        this.proveedor = proveedor;
    }

    @Override
    public void validar() {
        validarComunes();
        if (tipo.getNaturaleza() != Naturaleza.ENTRADA) {
            throw new NegocioException("El tipo " + tipo.getNombre() + " no es una entrada.");
        }
        if (bodegaDestino == null || !bodegaDestino.estaActiva()) {
            throw new NegocioException("Seleccione una bodega de destino activa.");
        }
        if ("Compra".equalsIgnoreCase(tipo.getNombre())
                && (proveedor == null || !proveedor.estaActivo())) {
            throw new NegocioException("Una compra exige un proveedor activo.");
        }
        if ("Compra".equalsIgnoreCase(tipo.getNombre())
                && (documentoSoporte == null || documentoSoporte.isBlank())) {
            throw new NegocioException("Una compra exige el documento de soporte (factura o remisión).");
        }
    }

    /** Una entrada suma la cantidad en la bodega de destino. */
    @Override
    public List<CambioStock> calcularCambiosStock() {
        return List.of(new CambioStock(bodegaDestino, cantidad));
    }

    public Bodega getBodegaDestino() { return bodegaDestino; }
    public void setBodegaDestino(Bodega bodegaDestino) { this.bodegaDestino = bodegaDestino; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
}
