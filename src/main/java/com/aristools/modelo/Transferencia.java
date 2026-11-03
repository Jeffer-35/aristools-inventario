package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.util.List;

/**
 * Traslado de mercancía entre dos bodegas (RF-008). Se aplica de inmediato
 * y no cambia el stock total del producto.
 */
public class Transferencia extends Movimiento {

    private Bodega bodegaOrigen;
    private Bodega bodegaDestino;

    public Transferencia() {
        super();
    }

    public Transferencia(Producto producto, TipoMovimiento tipo, Usuario usuarioRegistra, int cantidad,
                         Bodega bodegaOrigen, Bodega bodegaDestino) {
        super(producto, tipo, usuarioRegistra, cantidad);
        this.bodegaOrigen = bodegaOrigen;
        this.bodegaDestino = bodegaDestino;
    }

    @Override
    public void validar() {
        validarComunes();
        if (tipo.getNaturaleza() != Naturaleza.TRANSFERENCIA) {
            throw new NegocioException("El tipo " + tipo.getNombre() + " no es una transferencia.");
        }
        if (bodegaOrigen == null || bodegaDestino == null) {
            throw new NegocioException("Seleccione la bodega de origen y la de destino.");
        }
        if (!bodegaOrigen.estaActiva() || !bodegaDestino.estaActiva()) {
            throw new NegocioException("Ambas bodegas deben estar activas.");
        }
        if (bodegaOrigen.getIdBodega() == bodegaDestino.getIdBodega()) {
            throw new NegocioException("El origen debe ser distinto del destino.");
        }
    }

    /** Resta en el origen y suma lo mismo en el destino: el total no cambia. */
    @Override
    public List<CambioStock> calcularCambiosStock() {
        return List.of(new CambioStock(bodegaOrigen, -cantidad),
                       new CambioStock(bodegaDestino, cantidad));
    }

    public Bodega getBodegaOrigen() { return bodegaOrigen; }
    public void setBodegaOrigen(Bodega bodegaOrigen) { this.bodegaOrigen = bodegaOrigen; }

    public Bodega getBodegaDestino() { return bodegaDestino; }
    public void setBodegaDestino(Bodega bodegaDestino) { this.bodegaDestino = bodegaDestino; }
}
