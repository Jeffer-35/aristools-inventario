package com.aristools.modelo;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Sugerencia de compra de un producto. Con lo que ha salido de la bodega en
 * los últimos días y lo que tarda el proveedor en entregar, calcula si hay
 * que pedir y cuánto:
 *
 *   consumo diario   = unidades que salieron en el período / días del período
 *   punto de reorden = consumo diario x días de entrega + stock mínimo
 *   hay que pedir    = stock actual <= punto de reorden
 *   cantidad         = stock máximo - stock actual
 *
 * El stock mínimo funciona como colchón de seguridad: es lo que debe quedar
 * en bodega cuando llegue el pedido. Ejemplo: salen 2 por día, el proveedor
 * tarda 5 días y el mínimo es 10: el punto de reorden es 2 x 5 + 10 = 20.
 * Si quedan 18 y el máximo es 100, se sugiere pedir 82.
 */
public class SugerenciaCompra {

    private final Producto producto;
    private final Proveedor proveedor;      // el de la última compra; null si nunca se ha comprado
    private final int stockActual;          // unidades disponibles (sin contar la cuarentena)
    private final int unidadesSalidas;      // unidades que salieron en el período analizado
    private final int diasPeriodo;          // días analizados (por ejemplo, 90)
    private final int diasEntrega;          // del proveedor o el general
    private final int stockMinimo;          // propio del producto o el general
    private final int stockMaximo;          // propio del producto o el general
    private final BigDecimal costoUnitario; // última compra o costo promedio

    public SugerenciaCompra(Producto producto, Proveedor proveedor, int stockActual, int unidadesSalidas,
                            int diasPeriodo, int diasEntrega, int stockMinimo, int stockMaximo,
                            BigDecimal costoUnitario) {
        this.producto = producto;
        this.proveedor = proveedor;
        this.stockActual = stockActual;
        this.unidadesSalidas = unidadesSalidas;
        this.diasPeriodo = diasPeriodo;
        this.diasEntrega = diasEntrega;
        this.stockMinimo = stockMinimo;
        this.stockMaximo = stockMaximo;
        this.costoUnitario = costoUnitario == null ? BigDecimal.ZERO : costoUnitario;
    }

    /** Unidades que salen en promedio cada día, con dos decimales. */
    public BigDecimal consumoDiario() {
        if (diasPeriodo <= 0) {
            return BigDecimal.ZERO.setScale(2);
        }
        return BigDecimal.valueOf(unidadesSalidas).divide(BigDecimal.valueOf(diasPeriodo), 2, RoundingMode.HALF_UP);
    }

    /**
     * Stock en el que hay que pedir: lo que se va a consumir mientras llega el
     * pedido (redondeado hacia arriba, porque no se piden fracciones) más el
     * stock mínimo de seguridad.
     */
    public int puntoReorden() {
        BigDecimal mientrasLlega = BigDecimal.valueOf(unidadesSalidas)
                .multiply(BigDecimal.valueOf(diasEntrega))
                .divide(BigDecimal.valueOf(Math.max(diasPeriodo, 1)), 0, RoundingMode.CEILING);
        return mientrasLlega.intValue() + stockMinimo;
    }

    /** @return true si el stock ya llegó al punto de reorden */
    public boolean hayQuePedir() {
        return stockActual <= puntoReorden();
    }

    /**
     * Cuánto pedir para volver al stock máximo. Si el máximo quedara por
     * debajo del punto de reorden (consumo muy alto), se pide hasta el punto
     * de reorden más el mínimo, para no quedar pidiendo todos los días.
     */
    public int cantidadSugerida() {
        if (!hayQuePedir()) {
            return 0;
        }
        int meta = Math.max(stockMaximo, puntoReorden() + stockMinimo);
        return Math.max(meta - stockActual, 0);
    }

    /** Valor estimado del pedido sugerido: cantidad x costo unitario. */
    public BigDecimal valorEstimado() {
        return costoUnitario.multiply(BigDecimal.valueOf(cantidadSugerida())).setScale(2, RoundingMode.HALF_UP);
    }

    public Producto getProducto() {
        return producto;
    }

    public Proveedor getProveedor() {
        return proveedor;
    }

    public int getStockActual() {
        return stockActual;
    }

    public int getUnidadesSalidas() {
        return unidadesSalidas;
    }

    public int getDiasPeriodo() {
        return diasPeriodo;
    }

    public int getDiasEntrega() {
        return diasEntrega;
    }

    public int getStockMinimo() {
        return stockMinimo;
    }

    public int getStockMaximo() {
        return stockMaximo;
    }

    public BigDecimal getCostoUnitario() {
        return costoUnitario;
    }
}
