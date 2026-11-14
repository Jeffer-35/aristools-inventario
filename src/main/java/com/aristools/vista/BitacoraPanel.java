package com.aristools.vista;

import com.aristools.controlador.BitacoraControlador;
import com.aristools.modelo.RegistroBitacora;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Bitácora de auditoría (solo lectura, Administrador del Sistema): quién
 * inició sesión, quién falló al entrar y quién cambió contraseñas, usuarios,
 * productos, precios, bodegas, proveedores o parámetros, y cuándo.
 */
public class BitacoraPanel extends JPanel implements Recargable {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss");

    private final BitacoraControlador controlador = new BitacoraControlador();
    private final JTextField txtDesde = campoFecha();
    private final JTextField txtHasta = campoFecha();
    private final JComboBox<String> cmbAccion = new JComboBox<>();
    private final JTextField txtBuscar = new JTextField();
    private final JLabel lblResumen = new JLabel(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Fecha y hora", "Usuario", "Acción", "Detalle"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };

    public BitacoraPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Bitácora de auditoría"));
        textos.add(Tema.subtitulo("Quién hizo cada cambio sensible y cuándo. Los registros no se pueden modificar ni borrar."));

        txtBuscar.putClientProperty("JTextField.placeholderText", "Usuario o texto del detalle");
        JButton btnConsultar = Tema.botonPrincipal("Consultar");
        JButton btnExportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        btnConsultar.addActionListener(e -> consultar());
        btnExportar.addActionListener(e -> {
            try {
                ExportadorCsv.exportar(this, modelo, "bitacora");
            } catch (NegocioException ex) {
                Tema.error(this, ex.getMessage());
            }
        });
        txtBuscar.addActionListener(e -> consultar());
        BarraFiltros filtros = new BarraFiltros(2);
        filtros.agregar("Desde:", txtDesde);
        filtros.agregar("Hasta:", txtHasta);
        filtros.agregar("Acción:", cmbAccion);
        filtros.agregar("Buscar:", txtBuscar);
        filtros.agregarBotones(btnConsultar, btnExportar);

        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(filtros, BorderLayout.CENTER);

        JTable tabla = new JTable(modelo);
        Tema.estilizarTabla(tabla);
        int[] anchos = {150, 110, 160, 560};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.setToolTipText("Pase el mouse por el detalle para leerlo completo");
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scroll.setPreferredSize(new Dimension(900, 360));
        lblResumen.setFont(Tema.NEGRITA);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
    }

    /** Por defecto muestra los últimos 7 días. */
    @Override
    public void recargar() {
        if (cmbAccion.getItemCount() == 0) {
            cmbAccion.addItem("Todas");
            for (String a : controlador.listarAcciones()) {
                cmbAccion.addItem(a);
            }
            txtDesde.setText(LocalDate.now().minusDays(7).toString());
            txtHasta.setText(LocalDate.now().toString());
        }
        consultar();
    }

    private void consultar() {
        try {
            String accion = cmbAccion.getSelectedIndex() > 0 ? (String) cmbAccion.getSelectedItem() : null;
            List<RegistroBitacora> lista = controlador.consultar(fecha(txtDesde, "Desde"), fecha(txtHasta, "Hasta"),
                    accion, txtBuscar.getText());
            modelo.setRowCount(0);
            for (RegistroBitacora r : lista) {
                modelo.addRow(new Object[]{r.getFecha().format(FECHA_HORA),
                    r.getNombreUsuario() == null ? "—" : r.getNombreUsuario(), r.getAccion(), r.getDetalle()});
            }
            lblResumen.setText(lista.size() + " registro(s)" + (lista.size() >= 2000 ? " (se muestran los 2000 más recientes)" : ""));
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private static LocalDate fecha(JTextField campo, String nombre) {
        String texto = campo.getText().trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return texto.contains("/") ? LocalDate.parse(texto, DateTimeFormatter.ofPattern("d/M/yyyy")) : LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new NegocioException("La fecha '" + nombre + "' no es válida. Escríbala como AAAA-MM-DD o DD/MM/AAAA.");
        }
    }

    private static JTextField campoFecha() {
        JTextField t = new JTextField(9);
        t.putClientProperty("JTextField.placeholderText", "AAAA-MM-DD");
        return t;
    }
}
