package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;

/**
 * Entrada de mercancía por compra o devolución (RF-006).
 * Queda Pendiente hasta que el Jefe de Compras la autoriza (RF-010).
 * La compra exige proveedor activo, documento de soporte y el costo unitario
 * de la factura (reglas del RF-006).
 *
 * Kardex valorizado: la compra es el único movimiento que cambia el costo
 * promedio del producto (método del costo promedio ponderado).
 *
 * Vencimientos: si el producto controla vencimiento, la compra exige el lote
 * y la fecha de vencimiento que vienen marcados en la mercancía.
 */
public class Entrada extends Movimiento {

    private Bodega bodegaDestino;
    private Proveedor proveedor;   // obligatorio en compras
    private String lote;                  // lote del fabricante (productos con vencimiento)
    private LocalDate fechaVencimiento;   // fecha en que vence ese lote

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
        if (esCompra() && bodegaDestino.isCuarentena()) {
            throw new NegocioException("La bodega de cuarentena no recibe compras: es solo para mercancía averiada.");
        }
        if (esCompra() && (proveedor == null || !proveedor.estaActivo())) {
            throw new NegocioException("Una compra exige un proveedor activo.");
        }
        if (esCompra() && (documentoSoporte == null || documentoSoporte.isBlank())) {
            throw new NegocioException("Una compra exige el documento de soporte (factura o remisión).");
        }
        if (esCompra() && !documentoSoporte.matches(".*\\d.*")) {
            throw new NegocioException("El documento soporte debe llevar el número de la factura o remisión "
                    + "(por ejemplo, FAC-004512), para poder ubicar el documento físico.");
        }
        if (esCompra() && (costoUnitario == null || costoUnitario.compareTo(BigDecimal.ZERO) <= 0)) {
            throw new NegocioException("Una compra exige el costo unitario de la factura, mayor que cero.");
        }
        if (esCompra() && producto.isControlaVencimiento()) {
            if (lote == null || lote.isBlank() || fechaVencimiento == null) {
                throw new NegocioException("El producto " + producto.getNombre() + " controla vencimiento: "
                        + "escriba el lote y la fecha de vencimiento que trae la mercancía.");
            }
            if (!fechaVencimiento.isAfter(LocalDate.now())) {
                throw new NegocioException("El lote " + lote + " de " + producto.getNombre()
                        + " ya está vencido: no se puede recibir mercancía vencida.");
            }
        }
    }

    /**
     * Detecta un costo de factura sospechoso: menos de la mitad o más del doble
     * del costo de referencia (el promedio del producto). Sirve para atrapar
     * errores de digitación, como escribir 5.5 (cinco pesos) en vez de 5.500.
     * No bloquea la compra: la pantalla pide confirmación, porque un precio
     * sí puede cambiar mucho de verdad.
     *
     * @param referencia costo con el que se compara; null o cero si no hay
     */
    public static boolean costoInusual(BigDecimal costo, BigDecimal referencia) {
        if (costo == null || referencia == null || referencia.signum() <= 0) {
            return false;
        }
        BigDecimal dos = BigDecimal.valueOf(2);
        return costo.multiply(dos).compareTo(referencia) < 0 || costo.compareTo(referencia.multiply(dos)) > 0;
    }

    /** @return true si la entrada es una compra a proveedor (y no una devolución) */
    public boolean esCompra() {
        return tipo != null && "Compra".equalsIgnoreCase(tipo.getNombre());
    }

    /** La compra se valora al costo de la factura; la devolución, al costo promedio actual. */
    @Override
    public BigDecimal costoParaValorar(BigDecimal costoPromedioActual) {
        return esCompra() && costoUnitario != null ? costoUnitario : costoPromedioActual;
    }

    /**
     * Costo promedio ponderado después de la compra:
     *
     *   (stock anterior x promedio anterior + cantidad comprada x costo de la factura)
     *   -------------------------------------------------------------------------------
     *                      (stock anterior + cantidad comprada)
     *
     * Ejemplo: hay 10 martillos a $ 20.000 y se compran 10 a $ 24.000:
     * (10 x 20.000 + 10 x 24.000) / 20 = $ 22.000.
     * Si no había existencias, el promedio es el costo de la factura.
     */
    @Override
    public BigDecimal nuevoCostoPromedio(int stockAnterior, BigDecimal costoPromedioActual) {
        if (!esCompra() || costoUnitario == null) {
            return costoPromedioActual;
        }
        int stock = Math.max(stockAnterior, 0);
        BigDecimal valorAnterior = costoPromedioActual.multiply(BigDecimal.valueOf(stock));
        BigDecimal valorCompra = costoUnitario.multiply(BigDecimal.valueOf(cantidad));
        return valorAnterior.add(valorCompra)
                .divide(BigDecimal.valueOf(stock + cantidad), 4, RoundingMode.HALF_UP);
    }

    @Override
    public String prefijoDocumento() {
        return "ENT";
    }

    @Override
    public String tituloDocumento() {
        return "Comprobante de entrada";
    }

    /** Una entrada suma la cantidad en la bodega de destino. */
    @Override
    public List<CambioStock> calcularCambiosStock() {
        return List.of(new CambioStock(bodegaDestino, cantidad));
    }

    public Bodega getBodegaDestino() { return bodegaDestino; }
    public void setBodegaDestino(Bodega bodegaDestino) { this.bodegaDestino = bodegaDestino; }

    public String getLote() { return lote; }
    public void setLote(String lote) { this.lote = lote; }

    public LocalDate getFechaVencimiento() { return fechaVencimiento; }
    public void setFechaVencimiento(LocalDate fechaVencimiento) { this.fechaVencimiento = fechaVencimiento; }

    public Proveedor getProveedor() { return proveedor; }
    public void setProveedor(Proveedor proveedor) { this.proveedor = proveedor; }
}
