package com.aristools.vista;

import com.aristools.modelo.Bodega;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.TipoMovimiento;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Registro de entradas de mercancía (RF-006). Una entrada puede traer varios
 * productos: se crea un movimiento por producto y todos quedan Pendientes
 * hasta que el Jefe de Compras los autorice (RF-010). El stock no cambia aún.
 */
public class EntradaPanel extends PanelMovimiento {

    private final JComboBox<TipoMovimiento> cmbTipo = new JComboBox<>();
    private final JComboBox<Proveedor> cmbProveedor = new JComboBox<>();
    private final JComboBox<Bodega> cmbBodega = new JComboBox<>();
    private final JTextField txtDocumento = new JTextField();
    private final JTextField txtObservacion = new JTextField();
    private final JComboBox<Producto> cmbProducto = new JComboBox<>();
    private final JTextField txtCantidad = new JTextField();
    private final List<Producto> productosLista = new ArrayList<>();
    private final List<Integer> cantidadesLista = new ArrayList<>();
    private final DefaultTableModel modeloLista = new DefaultTableModel(
            new String[]{"Código", "Producto", "Cantidad"}, 0) {
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
        txtDocumento.putClientProperty("JTextField.placeholderText", "Factura o remisión (obligatorio en compras)");

        // Línea para agregar productos a la lista
        JPanel lineaProducto = new JPanel(new BorderLayout(8, 0));
        lineaProducto.setOpaque(false);
        txtCantidad.setPreferredSize(new Dimension(90, 32));
        txtCantidad.putClientProperty("JTextField.placeholderText", "Cantidad");
        JButton btnAgregar = Tema.botonSecundario("Agregar");
        btnAgregar.addActionListener(e -> ejecutar(this::agregarProducto));
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        derecha.setOpaque(false);
        derecha.add(txtCantidad);
        derecha.add(btnAgregar);
        lineaProducto.add(cmbProducto, BorderLayout.CENTER);
        lineaProducto.add(derecha, BorderLayout.EAST);
        agregarCampo("Producto", lineaProducto);

        Tema.estilizarTabla(tablaLista);
        tablaLista.getColumnModel().getColumn(1).setPreferredWidth(320);
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
                + "el stock aumenta solo en ese momento. La compra exige proveedor y documento soporte."));

        // El proveedor solo aplica a las compras
        cmbTipo.addActionListener(e -> {
            boolean esCompra = cmbTipo.getSelectedItem() != null
                    && "Compra".equalsIgnoreCase(cmbTipo.getSelectedItem().toString());
            cmbProveedor.setEnabled(esCompra);
            if (!esCompra) {
                cmbProveedor.setSelectedIndex(-1);
            }
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

    private void agregarProducto() {
        Producto p = (Producto) cmbProducto.getSelectedItem();
        if (p == null) {
            Tema.error(this, "Seleccione un producto.");
            return;
        }
        int cantidad = leerCantidad(txtCantidad.getText());
        productosLista.add(p);
        cantidadesLista.add(cantidad);
        modeloLista.addRow(new Object[]{p.getCodigo(), p.getNombre(), cantidad});
        txtCantidad.setText("");
        cmbProducto.requestFocus();
    }

    private void quitarProducto() {
        int fila = tablaLista.getSelectedRow();
        if (fila >= 0) {
            productosLista.remove(fila);
            cantidadesLista.remove(fila);
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
            entradas.add(e);
        }
        movimientos.registrarEntradas(entradas);   // valida y guarda en una sola transacción
        Tema.info(this, "Se registró la entrada con " + entradas.size() + " producto(s). "
                + "Queda Pendiente hasta que el Jefe de Compras la autorice.");
        productosLista.clear();
        cantidadesLista.clear();
        modeloLista.setRowCount(0);
        txtDocumento.setText("");
        txtObservacion.setText("");
    }
}
