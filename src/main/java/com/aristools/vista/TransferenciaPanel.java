package com.aristools.vista;

import com.aristools.modelo.Bodega;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.Transferencia;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Transferencias entre bodegas (RF-008). Se aplica de inmediato: resta en el
 * origen y suma en el destino en la misma transacción, sin cambiar el total.
 */
public class TransferenciaPanel extends PanelMovimiento {

    private final JComboBox<Producto> cmbProducto = new JComboBox<>();
    private final JComboBox<Bodega> cmbOrigen = new JComboBox<>();
    private final JComboBox<Bodega> cmbDestino = new JComboBox<>();
    private final JLabel lblExistencia = new JLabel(" ");
    private final JTextField txtCantidad = new JTextField();
    private final JTextField txtObservacion = new JTextField();
    private TipoMovimiento tipoTransferencia;

    public TransferenciaPanel() {
        agregarCampo("Producto", cmbProducto);
        agregarCampo("Bodega origen", cmbOrigen);
        agregarCampo("Existencia en origen", lblExistencia);
        agregarCampo("Bodega destino", cmbDestino);
        agregarCampo("Cantidad", txtCantidad);
        agregarCampo("Observación", txtObservacion);

        JButton btnRegistrar = Tema.botonPrincipal("Registrar transferencia");
        btnRegistrar.addActionListener(e -> ejecutar(this::registrar));
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        botones.setOpaque(false);
        botones.add(btnRegistrar);
        agregarFilaCompleta(botones);
        agregarFilaCompleta(nota("La transferencia se aplica de inmediato: resta en la bodega de origen y suma "
                + "en la de destino. El stock total del producto no cambia."));

        cmbProducto.addActionListener(e -> mostrarExistencia(cmbProducto, cmbOrigen, lblExistencia));
        cmbOrigen.addActionListener(e -> mostrarExistencia(cmbProducto, cmbOrigen, lblExistencia));

        JPanel arriba = new JPanel(new BorderLayout());
        arriba.setOpaque(false);
        arriba.add(formulario, BorderLayout.NORTH);
        add(arriba, BorderLayout.CENTER);
    }

    @Override
    public void recargar() {
        ejecutar(() -> {
            if (tipoTransferencia == null) {
                List<TipoMovimiento> tipos = movimientos.listarTipos(Naturaleza.TRANSFERENCIA);
                if (tipos.isEmpty()) {
                    throw new NegocioException("No existe el tipo de movimiento Transferencia en la base.");
                }
                tipoTransferencia = tipos.get(0);
            }
            cargarProductos(cmbProducto);
            cargarBodegas(cmbOrigen);
            cargarBodegas(cmbDestino);
            mostrarExistencia(cmbProducto, cmbOrigen, lblExistencia);
        });
    }

    private void registrar() {
        Transferencia t = new Transferencia((Producto) cmbProducto.getSelectedItem(), tipoTransferencia, null,
                leerCantidad(txtCantidad.getText()), (Bodega) cmbOrigen.getSelectedItem(),
                (Bodega) cmbDestino.getSelectedItem());
        t.setObservacion(textoOpcional(txtObservacion.getText()));
        movimientos.registrarTransferencia(t);
        txtCantidad.setText("");
        txtObservacion.setText("");
        mostrarExistencia(cmbProducto, cmbOrigen, lblExistencia);   // se ve el nuevo saldo antes del aviso
        Tema.info(this, "Transferencia registrada. El stock se movió entre las bodegas.");
    }
}
