package com.aristools.vista;

import com.aristools.modelo.Bodega;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Registro de entradas de mercancía (RF-006). Una entrada puede traer varios
 * productos: se crea un movimiento por producto y todos quedan Pendientes
 * hasta que el Jefe de Compras los autorice (RF-010). El stock no cambia aún.
 *
 * En las compras se escribe el costo unitario de la factura: con ese dato el
 * sistema recalcula el costo promedio del producto cuando la compra se autoriza.
 * Para ahorrar digitación, al elegir el producto el campo se llena con el costo
 * de la última compra (o el promedio), y si el costo escrito es muy distinto
 * del promedio se pide confirmación antes de agregarlo.
 *
 * Si el producto controla vencimiento, la compra pide además el lote y la
 * fecha de vencimiento que trae la mercancía (control FEFO).
 */
public class EntradaPanel extends PanelMovimiento {

    private final JComboBox<TipoMovimiento> cmbTipo = new JComboBox<>();
    private final JComboBox<Proveedor> cmbProveedor = new JComboBox<>();
    private final JComboBox<Bodega> cmbBodega = new JComboBox<>();
    private final JTextField txtDocumento = new JTextField();
    private final JTextField txtObservacion = new JTextField();
    private final JComboBox<Producto> cmbProducto = new JComboBox<>();
    private final JTextField txtCantidad = new JTextField();
    private final JTextField txtCosto = new JTextField();
    private final JLabel lblCostoReferencia = new JLabel(" ");
    private final JTextField txtLote = new JTextField();
    private final CampoFecha fVence = new CampoFecha();
    private final JLabel lblVencimiento = new JLabel(" ");
    private final List<Producto> productosLista = new ArrayList<>();
    private final List<Integer> cantidadesLista = new ArrayList<>();
    private final List<BigDecimal> costosLista = new ArrayList<>();   // null en las devoluciones
    private final List<String> lotesLista = new ArrayList<>();        // null si el producto no vence
    private final List<LocalDate> vencimientosLista = new ArrayList<>();
    private final DefaultTableModel modeloLista = new DefaultTableModel(
            new String[]{"Código", "Producto", "Cantidad", "Costo unit.", "Subtotal", "Lote", "Vence"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private final JTable tablaLista = new JTable(modeloLista);

    public EntradaPanel() {
        agregarCampo("Tipo de entrada", cmbTipo);
        agregarCampo("Proveedor", cmbProveedor);
        agregarCampo("Bodega destino", cmbBodega);
        agregarCampo("Documento soporte", txtDocumento);
        agregarCampo("Observación", txtObservacion);
        txtDocumento.putClientProperty("JTextField.placeholderText", "N.º de factura o remisión, ej. FAC-004512 (obligatorio en compras)");

        // Línea para agregar productos a la lista
        JPanel lineaProducto = new JPanel(new BorderLayout(8, 0));
        lineaProducto.setOpaque(false);
        txtCantidad.setPreferredSize(new Dimension(90, 32));
        txtCantidad.putClientProperty("JTextField.placeholderText", "Cantidad");
        txtCosto.setPreferredSize(new Dimension(150, 32));
        txtCosto.putClientProperty("JTextField.placeholderText", "Costo unit. (factura)");
        txtCosto.setToolTipText("Costo de una unidad según la factura del proveedor. Solo en compras.");
        JButton btnAgregar = Tema.botonSecundario("Agregar");
        btnAgregar.addActionListener(e -> ejecutar(this::agregarProducto));
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        derecha.add(txtCantidad);
        derecha.add(txtCosto);
        derecha.add(btnAgregar);
        lineaProducto.add(cmbProducto, BorderLayout.CENTER);
        lineaProducto.add(derecha, BorderLayout.EAST);
        agregarCampo("Producto", lineaProducto);
        lblCostoReferencia.setForeground(Tema.TEXTO_SUAVE);
        agregarCampo("Costo de referencia", lblCostoReferencia);

        // Lote y fecha de vencimiento: solo para los productos que controlan vencimiento
        JPanel lineaLote = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 0));
        lineaLote.setOpaque(false);
        txtLote.setPreferredSize(new Dimension(150, 32));
        txtLote.putClientProperty("JTextField.placeholderText", "Lote, ej. L-2291");
        lblVencimiento.setForeground(Tema.TEXTO_SUAVE);
        lineaLote.add(txtLote);
        lineaLote.add(new JLabel("Vence:"));
        lineaLote.add(fVence);
        lineaLote.add(lblVencimiento);
        agregarCampo("Lote y vencimiento", lineaLote);

        Tema.estilizarTabla(tablaLista);
        tablaLista.getColumnModel().getColumn(1).setPreferredWidth(280);
        JScrollPane scroll = new JScrollPane(tablaLista);
        scroll.setPreferredSize(new Dimension(500, 150));
        agregarFilaCompleta(scroll);

        JButton btnQuitar = Tema.botonSecundario("Quitar producto seleccionado");
        btnQuitar.addActionListener(e -> quitarProducto());
        JButton btnRegistrar = Tema.botonPrincipal("Registrar entrada");
        btnRegistrar.addActionListener(e -> ejecutar(this::registrar));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(btnQuitar);
        botones.add(btnRegistrar);
        agregarFilaCompleta(botones);
        agregarFilaCompleta(nota("La entrada queda Pendiente hasta que el Jefe de Compras la autorice; "
                + "el stock aumenta solo en ese momento. La compra exige proveedor, documento soporte y el costo "
                + "unitario de la factura, con el que se recalcula el costo promedio del producto."));

        // El proveedor y el costo de la factura solo aplican a las compras
        cmbTipo.addActionListener(e -> {
            boolean esCompra = esCompra();
            cmbProveedor.setEnabled(esCompra);
            txtCosto.setEnabled(esCompra);
            if (!esCompra) {
                cmbProveedor.setSelectedIndex(-1);
                txtCosto.setText("");
            }
            sugerirCosto();
            avisarVencimiento();
        });
        cmbProducto.addActionListener(e -> {
            sugerirCosto();
            avisarVencimiento();
        });

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(formulario, BorderLayout.NORTH);
        add(arriba, BorderLayout.CENTER);
    }

    @Override
    public void recargar() {
        ejecutar(() -> {
            if (cmbTipo.getItemCount() == 0) {
                for (TipoMovimiento t : movimientos.listarTipos(Naturaleza.ENTRADA)) {
                    cmbTipo.addItem(t);
                }
            }
            cargarProveedores(cmbProveedor);
            cargarBodegas(cmbBodega);
            cargarProductos(cmbProducto);
            cmbTipo.setSelectedIndex(cmbTipo.getItemCount() > 0 ? 0 : -1);
        });
    }

    private boolean esCompra() {
        return cmbTipo.getSelectedItem() != null
                && "Compra".equalsIgnoreCase(cmbTipo.getSelectedItem().toString());
    }

    /**
     * Llena el costo con el de la última compra del producto (o su costo
     * promedio si nunca se ha comprado) y muestra ambos como referencia.
     * El usuario lo cambia si la factura trae otro precio.
     */
    private void sugerirCosto() {
        Producto p = (Producto) cmbProducto.getSelectedItem();
        if (!esCompra() || p == null) {
            lblCostoReferencia.setText(esCompra() ? "Seleccione un producto" : "Solo aplica a las compras");
            return;
        }
        try {
            BigDecimal ultimo = movimientos.ultimoCostoCompra(p.getIdProducto());
            BigDecimal sugerido = ultimo != null ? ultimo : referencia(p);
            txtCosto.setText(sugerido == null ? "" : Formato.pesos(sugerido));
            lblCostoReferencia.setText((ultimo == null ? "Sin compras anteriores" : "Última compra: " + Formato.pesos(ultimo))
                    + "  ·  Costo promedio: " + Formato.pesos(p.getCostoPromedio()) + "  ·  Verifique con la factura");
        } catch (NegocioException ex) {
            lblCostoReferencia.setText("No se pudo consultar el costo: " + ex.getMessage());
        }
    }

    // Costo con el que se compara: el promedio del producto o, si aún no tiene, su costo de referencia.
    private static BigDecimal referencia(Producto p) {
        if (p.getCostoPromedio() != null && p.getCostoPromedio().signum() > 0) {
            return p.getCostoPromedio();
        }
        return p.getCostoReferencia() != null && p.getCostoReferencia().signum() > 0 ? p.getCostoReferencia() : null;
    }

    /** Indica si el producto elegido pide lote y fecha de vencimiento. */
    private void avisarVencimiento() {
        Producto p = (Producto) cmbProducto.getSelectedItem();
        boolean pide = esCompra() && p != null && p.isControlaVencimiento();
        txtLote.setEnabled(pide);
        lblVencimiento.setText(pide ? "Obligatorio: este producto controla vencimiento"
                : "No aplica para este producto");
        lblVencimiento.setForeground(pide ? Tema.NARANJA : Tema.TEXTO_SUAVE);
        if (!pide) {
            txtLote.setText("");
            fVence.setFecha(null);
        }
    }

    private void agregarProducto() {
        Producto p = (Producto) cmbProducto.getSelectedItem();
        if (p == null) {
            Tema.error(this, "Seleccione un producto.");
            return;
        }
        int cantidad = leerCantidad(txtCantidad.getText());
        // Productos que vencen: el lote y la fecha se leen de la etiqueta de la mercancía
        String lote = null;
        LocalDate vence = null;
        if (esCompra() && p.isControlaVencimiento()) {
            lote = textoOpcional(txtLote.getText());
            vence = fVence.getFecha("fecha de vencimiento");
            if (lote == null || vence == null) {
                throw new NegocioException("El producto " + p.getNombre() + " controla vencimiento: "
                        + "escriba el lote y elija la fecha de vencimiento.");
            }
            if (!vence.isAfter(LocalDate.now())) {
                throw new NegocioException("Esa fecha de vencimiento ya pasó: no se puede recibir mercancía vencida.");
            }
        }
        // En la compra el costo es obligatorio; en la devolución no se pide
        BigDecimal costo = esCompra() ? Formato.leerPesos(txtCosto.getText(), "costo unitario") : null;
        // Un costo muy distinto del promedio suele ser un error de digitación (ej. 5.5 en vez de 5.500)
        if (Entrada.costoInusual(costo, referencia(p))) {
            int respuesta = JOptionPane.showConfirmDialog(this, "El costo escrito (" + Formato.pesos(costo)
                    + ") es muy distinto del costo promedio de este producto (" + Formato.pesos(referencia(p)) + ").\n\n"
                    + "¿Está seguro de que ese es el valor de la factura?\n"
                    + "Recuerde: para miles escriba 5500 o 5.500, no 5.5.", "Revisar el costo",
                    JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
            if (respuesta != JOptionPane.YES_OPTION) {
                txtCosto.requestFocus();
                return;
            }
        }
        productosLista.add(p);
        cantidadesLista.add(cantidad);
        costosLista.add(costo);
        lotesLista.add(lote);
        vencimientosLista.add(vence);
        modeloLista.addRow(new Object[]{p.getCodigo(), p.getNombre(), cantidad,
            costo == null ? "—" : Formato.pesos(costo),
            costo == null ? "—" : Formato.pesos(costo.multiply(BigDecimal.valueOf(cantidad))),
            lote == null ? "—" : lote,
            vence == null ? "—" : vence.format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy"))});
        txtCantidad.setText("");
        txtLote.setText("");
        fVence.setFecha(null);
        sugerirCosto();
        cmbProducto.requestFocus();
    }

    private void quitarProducto() {
        int fila = tablaLista.getSelectedRow();
        if (fila >= 0) {
            productosLista.remove(fila);
            cantidadesLista.remove(fila);
            costosLista.remove(fila);
            lotesLista.remove(fila);
            vencimientosLista.remove(fila);
            modeloLista.removeRow(fila);
        }
    }

    /** Crea una Entrada por cada producto de la lista y las envía juntas al controlador. */
    private void registrar() {
        TipoMovimiento tipo = (TipoMovimiento) cmbTipo.getSelectedItem();
        Bodega bodega = (Bodega) cmbBodega.getSelectedItem();
        Proveedor proveedor = cmbProveedor.isEnabled() ? (Proveedor) cmbProveedor.getSelectedItem() : null;
        List<Entrada> entradas = new ArrayList<>();
        for (int i = 0; i < productosLista.size(); i++) {
            Entrada e = new Entrada(productosLista.get(i), tipo, null, cantidadesLista.get(i), bodega, proveedor);
            e.setDocumentoSoporte(textoOpcional(txtDocumento.getText()));
            e.setObservacion(textoOpcional(txtObservacion.getText()));
            e.setCostoUnitario(costosLista.get(i));   // costo de la factura (solo compras)
            e.setLote(lotesLista.get(i));
            e.setFechaVencimiento(vencimientosLista.get(i));
            entradas.add(e);
        }
        movimientos.registrarEntradas(entradas);   // valida y guarda en una sola transacción
        Tema.info(this, "Se registró la entrada con " + entradas.size() + " producto(s). "
                + "Queda Pendiente hasta que el Jefe de Compras la autorice.");
        productosLista.clear();
        cantidadesLista.clear();
        costosLista.clear();
        lotesLista.clear();
        vencimientosLista.clear();
        modeloLista.setRowCount(0);
        txtDocumento.setText("");
        txtObservacion.setText("");
    }
}
