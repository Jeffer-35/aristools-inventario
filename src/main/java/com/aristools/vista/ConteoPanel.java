package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.controlador.ConteoControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.LineaConteo;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Conteo físico y ajustes (RF-009), en tres pasos:
 * 1) descargar la plantilla CSV de la bodega, 2) cargarla con lo contado y
 * revisar las diferencias, 3) generar los ajustes. Los ajustes positivos se
 * aplican de inmediato; los negativos esperan al Supervisor (RF-011).
 * El archivo se procesa completo o no se procesa.
 */
public class ConteoPanel extends JPanel implements Recargable {

    private final ConteoControlador controlador = new ConteoControlador();
    private final BodegaControlador bodegas = new BodegaControlador();
    private final JComboBox<Bodega> cmbBodega = new JComboBox<>();
    private final JButton btnGenerar = Tema.botonPrincipal("3. Generar ajustes");
    private final JLabel lblResumen = new JLabel(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Código", "Producto", "Stock sistema", "Contado", "Diferencia"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private List<LineaConteo> lineas = new ArrayList<>();
    private Bodega bodegaCargada;

    public ConteoPanel() {
        setLayout(new BorderLayout(0, 14));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Inventario físico"));
        textos.add(Tema.subtitulo("Compare lo que hay en la bodega con lo que dice el sistema y genere los ajustes."));

        JPanel pasos = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        pasos.setBackground(Tema.BLANCO);
        pasos.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(8, 10, 8, 10)));
        JLabel lblBodega = new JLabel("Bodega:");
        lblBodega.setFont(Tema.NEGRITA);
        cmbBodega.setFont(Tema.NORMAL);
        cmbBodega.setPreferredSize(new java.awt.Dimension(240, 32));
        JButton btnDescargar = Tema.botonSecundario("1. Descargar plantilla");
        JButton btnCargar = Tema.botonSecundario("2. Cargar plantilla contada");
        btnDescargar.addActionListener(e -> descargarPlantilla());
        btnCargar.addActionListener(e -> cargarPlantilla());
        btnGenerar.addActionListener(e -> generarAjustes());
        btnGenerar.setEnabled(false);
        pasos.add(lblBodega);
        pasos.add(cmbBodega);
        pasos.add(btnDescargar);
        pasos.add(btnCargar);
        pasos.add(btnGenerar);

        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(pasos, BorderLayout.CENTER);

        JTable tabla = new JTable(modelo);
        Tema.estilizarTabla(tabla);
        tabla.getColumnModel().getColumn(1).setPreferredWidth(320);
        tabla.getColumnModel().getColumn(4).setCellRenderer(new RenderDiferencia());
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        lblResumen.setFont(Tema.NEGRITA);
        JLabel ayuda = new JLabel("<html><body style='width:760px'>Abra la plantilla en Excel, escriba lo contado en la columna "
                + "<b>cantidad_contada</b> y guárdela como CSV. Diferencia = contado − sistema. Los ajustes "
                + "positivos se aplican de inmediato; los negativos quedan Pendientes del Supervisor.</body></html>");
        ayuda.setFont(Tema.NORMAL.deriveFont(12f));
        ayuda.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setOpaque(false);
        sur.add(lblResumen, BorderLayout.NORTH);
        sur.add(ayuda, BorderLayout.CENTER);

        cmbBodega.addActionListener(e -> limpiarResultado());
        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
    }

    @Override
    public void recargar() {
        try {
            Object antes = cmbBodega.getSelectedItem();
            cmbBodega.removeAllItems();
            for (Bodega b : bodegas.listar(false)) {
                cmbBodega.addItem(b);
                if (antes != null && b.toString().equals(antes.toString())) {
                    cmbBodega.setSelectedItem(b);
                }
            }
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private Bodega bodega() {
        Bodega b = (Bodega) cmbBodega.getSelectedItem();
        if (b == null) {
            throw new NegocioException("Seleccione la bodega que va a contar.");
        }
        return b;
    }

    private void descargarPlantilla() {
        try {
            Bodega b = bodega();
            JFileChooser selector = new JFileChooser();
            selector.setSelectedFile(new File("conteo_" + b.getCodigo() + "_" + LocalDate.now() + ".csv"));
            selector.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
            if (selector.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File archivo = selector.getSelectedFile();
                if (!archivo.getName().toLowerCase().endsWith(".csv")) {
                    archivo = new File(archivo.getParentFile(), archivo.getName() + ".csv");
                }
                controlador.generarPlantilla(b.getIdBodega(), archivo);
                Tema.info(this, "Plantilla guardada en:\n" + archivo.getAbsolutePath());
            }
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void cargarPlantilla() {
        try {
            Bodega b = bodega();
            JFileChooser selector = new JFileChooser();
            selector.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
            if (selector.showOpenDialog(this) != JFileChooser.APPROVE_OPTION) {
                return;
            }
            limpiarResultado();
            lineas = controlador.leerPlantilla(b.getIdBodega(), selector.getSelectedFile());
            bodegaCargada = b;
            int conDiferencia = 0;
            for (LineaConteo l : lineas) {
                modelo.addRow(new Object[]{l.getCodigoProducto(), l.getNombreProducto(),
                    l.getStockSistema(), l.getCantidadContada(), l.diferencia()});
                if (l.diferencia() != 0) {
                    conDiferencia++;
                }
            }
            lblResumen.setText(lineas.size() + " productos contados en " + b.getNombre() + " · "
                    + conDiferencia + " con diferencia");
            btnGenerar.setEnabled(conDiferencia > 0);
        } catch (NegocioException ex) {
            limpiarResultado();
            Tema.error(this, ex.getMessage());
        }
    }

    private void generarAjustes() {
        int opcion = JOptionPane.showConfirmDialog(this,
                "¿Generar los ajustes del conteo de " + bodegaCargada.getNombre() + "?",
                "Confirmar ajustes", JOptionPane.YES_NO_OPTION);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            int cantidad = controlador.generarAjustes(bodegaCargada.getIdBodega(), lineas);
            Tema.info(this, "Se generaron " + cantidad + " ajuste(s). Los positivos ya se aplicaron; "
                    + "los negativos quedan Pendientes del Supervisor.");
            limpiarResultado();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void limpiarResultado() {
        modelo.setRowCount(0);
        lineas = new ArrayList<>();
        bodegaCargada = null;
        lblResumen.setText(" ");
        btnGenerar.setEnabled(false);
    }

    /** Verde si sobra, rojo si falta, gris si coincide. */
    private static class RenderDiferencia extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
            int d = v instanceof Integer i ? i : 0;
            comp.setForeground(d > 0 ? Tema.VERDE : d < 0 ? Tema.ROJO : Tema.TEXTO_SUAVE);
            setText(d > 0 ? "+" + d : String.valueOf(d));
            comp.setFont(Tema.NEGRITA);
            return comp;
        }
    }
}
