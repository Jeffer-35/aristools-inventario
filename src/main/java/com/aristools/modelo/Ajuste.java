package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.util.List;

/**
 * Ajuste generado por el conteo físico (RF-009). El positivo se aplica de
 * inmediato; el negativo espera al Supervisor de Bodega (RF-011).
 */
public class Ajuste extends Movimiento {

    private Bodega bodega;
    private boolean positivo;   // true: sobraron unidades; false: faltaron

    public Ajuste() {
        super();
    }

    public Ajuste(Producto producto, TipoMovimiento tipo, Usuario usuarioRegistra, int cantidad,
                  Bodega bodega, boolean positivo) {
        super(producto, tipo, usuarioRegistra, cantidad);
        this.bodega = bodega;
        this.positivo = positivo;
    }

    @Override
    public void validar() {
        validarComunes();
        Naturaleza esperada = positivo ? Naturaleza.ENTRADA : Naturaleza.SALIDA;
        if (tipo.getNaturaleza() != esperada) {
            throw new NegocioException("El tipo " + tipo.getNombre() + " no corresponde a un ajuste "
                    + (positivo ? "positivo." : "negativo."));
        }
        if (bodega == null || !bodega.estaActiva()) {
            throw new NegocioException("Seleccione una bodega activa para el ajuste.");
        }
    }

    /** Suma o resta la diferencia del conteo en la bodega contada. */
    @Override
    public List<CambioStock> calcularCambiosStock() {
        return List.of(new CambioStock(bodega, positivo ? cantidad : -cantidad));
    }

    public Bodega getBodega() { return bodega; }
    public void setBodega(Bodega bodega) { this.bodega = bodega; }

    public boolean isPositivo() { return positivo; }
    public void setPositivo(boolean positivo) { this.positivo = positivo; }
}
