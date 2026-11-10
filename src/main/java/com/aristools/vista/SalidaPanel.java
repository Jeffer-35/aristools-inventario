package com.aristools.vista;

import com.aristools.modelo.Bodega;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.Salida;
import com.aristools.modelo.TipoMovimiento;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Registro de salidas (RF-007). El consumo interno se aplica de inmediato;
 * merma, producto dañado o vencido y devolución a proveedor quedan
 * Pendientes del Supervisor (RF-011). El stock nunca queda negativo.
 */
public class SalidaPanel extends PanelMovimiento {

    private final JComboBox<TipoMovimiento> cmbTipo = new JComboBox<>();
    private final JComboBox<Proveedor> cmbProveedor = new JComboBox<>();
    private final JComboBox<Producto> cmbProducto = new JComboBox<>();
    private final JComboBox<Bodega> cmbBodega = new JComboBox<>();
    private final JTextField txtCantidad = new JTextField();
    private final JLabel lblExistencia = new JLabel(" ");
    private final JTextField txtMotivo = new JTextField();
    private final JTextField txtDocumento = new JTextField();
    private final JTextField txtObservacion = new JTextField();

    public SalidaPanel() {
        agregarCampo("Tipo de salida", cmbTipo);
        agregarCampo("Proveedor", cmbProveedor);
        agregarCampo("Producto", cmbProducto);
        agregarCampo("Bodega origen", cmbBodega);
        agregarCampo("Existencia", lblExistencia);
        agregarCampo("Cantidad", txtCantidad);
        agregarCampo("Motivo", txtMotivo);
        agregarCampo("Documento soporte", txtDocumento);
        agregarCampo("Observación", txtObservacion);
        txtMotivo.putClientProperty("JTextField.placeholderText", "Obligatorio en merma y producto dañado o vencido");

        JButton btnRegistrar = Tema.botonPrincipal("Registrar salida");
        btnRegistrar.addActionListener(e -> ejecutar(this::registrar));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        botones.setOpaque(false);
        botones.add(btnRegistrar);
        agregarFilaCompleta(botones);
        agregarFilaCompleta(nota("El consumo interno descuenta el stock de inmediato. Merma, producto dañado o "
                + "vencido y devolución a proveedor quedan Pendientes hasta que el Supervisor las autorice."));

        cmbTipo.addActionListener(e -> {
            boolean devolucion = cmbTipo.getSelectedItem() != null
                    && "Devolución a proveedor".equalsIgnoreCase(cmbTipo.getSelectedItem().toString());
            cmbProveedor.setEnabled(devolucion);
            if (!devolucion) {
                cmbProveedor.setSelectedIndex(-1);
            }
        });
        cmbProducto.addActionListener(e -> mostrarExistencia(cmbProducto, cmbBodega, lblExistencia));
        cmbBodega.addActionListener(e -> mostrarExistencia(cmbProducto, cmbBodega, lblExistencia));

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(formulario, BorderLayout.NORTH);
        add(arriba, BorderLayout.CENTER);
    }

    @Override
    public void recargar() {
        ejecutar(() -> {
            if (cmbTipo.getItemCount() == 0) {
                for (TipoMovimiento t : movimientos.listarTipos(Naturaleza.SALIDA)) {
                    cmbTipo.addItem(t);
                }
            }
            cargarProveedores(cmbProveedor);
            cargarProductos(cmbProducto);
            cargarBodegas(cmbBodega);
            cmbTipo.setSelectedIndex(cmbTipo.getItemCount() > 0 ? 0 : -1);
            mostrarExistencia(cmbProducto, cmbBodega, lblExistencia);
        });
    }

    private void registrar() {
        TipoMovimiento tipo = (TipoMovimiento) cmbTipo.getSelectedItem();
        Proveedor proveedor = cmbProveedor.isEnabled() ? (Proveedor) cmbProveedor.getSelectedItem() : null;
        Salida s = new Salida((Producto) cmbProducto.getSelectedItem(), tipo, null,
                leerCantidad(txtCantidad.getText()), (Bodega) cmbBodega.getSelectedItem(), proveedor);
        s.setMotivo(textoOpcional(txtMotivo.getText()));
        s.setDocumentoSoporte(textoOpcional(txtDocumento.getText()));
        s.setObservacion(textoOpcional(txtObservacion.getText()));
        movimientos.registrarSalida(s);
        boolean inmediata = tipo != null && !tipo.isRequiereAutorizacion();
        txtCantidad.setText("");
        txtMotivo.setText("");
        txtDocumento.setText("");
        txtObservacion.setText("");
        mostrarExistencia(cmbProducto, cmbBodega, lblExistencia);   // se ve el nuevo saldo antes del aviso
        Tema.info(this, inmediata
                ? "Salida registrada. El stock ya se descontó."
                : "Salida registrada. Queda Pendiente hasta que el Supervisor la autorice.");
    }
}
