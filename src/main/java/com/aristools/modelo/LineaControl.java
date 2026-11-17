package com.aristools.modelo;

import java.math.BigDecimal;

/**
 * Línea del control de inventario de un período (por ejemplo, un mes) para
 * un producto: con cuánto empezó, qué entró, qué salió, qué se perdió, qué
 * sobró y con cuánto terminó, en unidades y en pesos.
 *
 *   Saldo final = saldo inicial + entradas + sobrantes − salidas − pérdidas
 *
 * Las transferencias no se cuentan porque solo cambian el producto de
 * bodega: el total no se mueve.
 */
public class LineaControl {

    private final Producto producto;
    private int saldoInicial;
    private int entradas;       // compras, devoluciones de clientes e inventario inicial
    private int salidas;        // consumo interno y devoluciones a proveedor
    private int perdidas;       // mermas, dañados o vencidos y faltantes del conteo
    private int sobrantes;      // ajustes positivos del conteo
    private BigDecimal valorInicial = BigDecimal.ZERO;
    private BigDecimal valorEntradas = BigDecimal.ZERO;
    private BigDecimal valorSalidas = BigDecimal.ZERO;
    private BigDecimal valorPerdidas = BigDecimal.ZERO;
    private BigDecimal valorSobrantes = BigDecimal.ZERO;

    public LineaControl(Producto producto) {
        this.producto = producto;
    }

    /**
     * Suma un movimiento autorizado a la columna que le corresponde.
     *
     * @param naturaleza ENTRADA o SALIDA (las transferencias se ignoran)
     * @param tipo       nombre del tipo de movimiento
     * @param anterior   true si el movimiento es de antes del período: va al saldo inicial
     * @param valor      cantidad x costo con que se valoró el movimiento
     */
    public void acumular(Naturaleza naturaleza, String tipo, boolean anterior, int cantidad, BigDecimal valor) {
        if (naturaleza == Naturaleza.TRANSFERENCIA) {
            return;
        }
        BigDecimal v = valor == null ? BigDecimal.ZERO : valor;
        boolean entra = naturaleza == Naturaleza.ENTRADA;
        if (anterior) {
            saldoInicial += entra ? cantidad : -cantidad;
            valorInicial = entra ? valorInicial.add(v) : valorInicial.subtract(v);
        } else if (entra && "Ajuste positivo".equals(tipo)) {
            sobrantes += cantidad;
            valorSobrantes = valorSobrantes.add(v);
        } else if (entra) {
            entradas += cantidad;
            valorEntradas = valorEntradas.add(v);
        } else if (esPerdida(tipo)) {
            perdidas += cantidad;
            valorPerdidas = valorPerdidas.add(v);
        } else {
            salidas += cantidad;
            valorSalidas = valorSalidas.add(v);
        }
    }

    /** Los mismos tipos que usa el reporte de pérdidas. */
    public static boolean esPerdida(String tipo) {
        return "Merma".equals(tipo) || "Producto dañado o vencido".equals(tipo) || "Ajuste negativo".equals(tipo);
    }

    public int saldoFinal() {
        return saldoInicial + entradas + sobrantes - salidas - perdidas;
    }

    public BigDecimal valorFinal() {
        return valorInicial.add(valorEntradas).add(valorSobrantes).subtract(valorSalidas).subtract(valorPerdidas);
    }

    /** @return true si en el período no entró ni salió nada de este producto */
    public boolean sinMovimiento() {
        return entradas == 0 && salidas == 0 && perdidas == 0 && sobrantes == 0;
    }

    /** @return true si la línea no aporta nada al informe (nunca tuvo existencias ni movimientos) */
    public boolean vacia() {
        return saldoInicial == 0 && sinMovimiento();
    }

    public Producto getProducto() { return producto; }
    public int getSaldoInicial() { return saldoInicial; }
    public int getEntradas() { return entradas; }
    public int getSalidas() { return salidas; }
    public int getPerdidas() { return perdidas; }
    public int getSobrantes() { return sobrantes; }
    public BigDecimal getValorInicial() { return valorInicial; }
    public BigDecimal getValorEntradas() { return valorEntradas; }
    public BigDecimal getValorSalidas() { return valorSalidas; }
    public BigDecimal getValorPerdidas() { return valorPerdidas; }
    public BigDecimal getValorSobrantes() { return valorSobrantes; }
}
