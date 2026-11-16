package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.controlador.ConteoControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Conteo;
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
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
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
 *
 * Tipo de conteo: general (toda la bodega) o cíclico (solo una clase ABC).
 * A ciegas: la plantilla no trae el stock del sistema. Cada conteo queda en
 * la pestaña Historial con su exactitud (productos que coincidieron).
 */
public class ConteoPanel extends JPanel implements Recargable {

    private final ConteoControlador controlador = new ConteoControlador();
    private final BodegaControlador bodegas = new BodegaControlador();
    private final JComboBox<Bodega> cmbBodega = new JComboBox<>();
    private final JButton btnGenerar = Tema.botonPrincipal("3. Guardar conteo y ajustes");
    private final JComboBox<String> cmbTipo = new JComboBox<>(new String[]{
        "General (toda la bodega)", "Cíclico: solo clase A", "Cíclico: solo clase B", "Cíclico: solo clase C"});
    private final JCheckBox chkCiegas = new JCheckBox("A ciegas (sin stock del sistema)");
    private final DefaultTableModel modeloHistorial = new DefaultTableModel(
            new String[]{"N.º", "Fecha", "Bodega", "Tipo", "Contó", "Productos", "Con diferencia", "Exactitud"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private final DefaultTableModel modeloDetalle = new DefaultTableModel(
            new String[]{"Código", "Producto", "Stock sistema", "Contado", "Diferencia"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private final JTable tablaHistorial = new JTable(modeloHistorial);
    private List<Conteo> conteos = new ArrayList<>();
    private Character claseCargada;
    private boolean ciegasCargado;
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

        // Fila 1: qué se va a contar. Fila 2: los tres pasos.
        JPanel pasos = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        pasos.setOpaque(false);
        JPanel opciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        opciones.setOpaque(false);
        JPanel barra = new JPanel(new GridLayout(2, 1));
        barra.setBackground(Tema.BLANCO);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        JLabel lblTipo = new JLabel("Tipo de conteo:");
        lblTipo.setFont(Tema.NEGRITA);
        cmbTipo.setFont(Tema.NORMAL);
        cmbTipo.setToolTipText("El conteo cíclico cuenta solo una clase ABC (consumo de los últimos 90 días).");
        chkCiegas.setOpaque(false);
        chkCiegas.setFont(Tema.NORMAL);
        chkCiegas.setToolTipText("La plantilla no muestra el stock del sistema, para que el conteo no se guíe por él.");
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
        opciones.add(lblBodega);
        opciones.add(cmbBodega);
        opciones.add(lblTipo);
        opciones.add(cmbTipo);
        opciones.add(chkCiegas);
        pasos.add(btnDescargar);
        pasos.add(btnCargar);
        pasos.add(btnGenerar);
        barra.add(opciones);
        barra.add(pasos);

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
        JPanel conteo = new JPanel(new BorderLayout(0, 10));
        conteo.setBackground(Tema.FONDO);
        conteo.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        conteo.add(barra, BorderLayout.NORTH);
        conteo.add(scroll, BorderLayout.CENTER);
        conteo.add(sur, BorderLayout.SOUTH);

        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setFont(Tema.NEGRITA);
        pestanas.addTab("Conteo", conteo);
        pestanas.addTab("Historial", crearHistorial());
        pestanas.addChangeListener(e -> {
            if (pestanas.getSelectedIndex() == 1) {
                cargarHistorial();
            }
        });
        add(textos, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
    }

    // Arriba la lista de conteos guardados; abajo las líneas del conteo seleccionado.
    private JPanel crearHistorial() {
        Tema.estilizarTabla(tablaHistorial);
        tablaHistorial.getColumnModel().getColumn(3).setPreferredWidth(220);
        tablaHistorial.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrarDetalle();
            }
        });
        JScrollPane lista = new JScrollPane(tablaHistorial);
        lista.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        JTable tablaDetalle = new JTable(modeloDetalle);
        Tema.estilizarTabla(tablaDetalle);
        tablaDetalle.getColumnModel().getColumn(1).setPreferredWidth(320);
        tablaDetalle.getColumnModel().getColumn(4).setCellRenderer(new RenderDiferencia());
        JScrollPane detalle = new JScrollPane(tablaDetalle);
        detalle.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, lista, detalle);
        division.setResizeWeight(0.45);
        division.setBorder(null);
        JLabel nota = new JLabel("Exactitud = productos que coincidieron ÷ productos contados. "
                + "Seleccione un conteo para ver sus líneas.");
        nota.setFont(Tema.NORMAL.deriveFont(12f));
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel p = new JPanel(new BorderLayout(0, 8));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(10, 4, 4, 4));
        p.add(division, BorderLayout.CENTER);
        p.add(nota, BorderLayout.SOUTH);
        return p;
    }

    private void cargarHistorial() {
        try {
            conteos = controlador.historial(null);
            modeloHistorial.setRowCount(0);
            modeloDetalle.setRowCount(0);
            for (Conteo c : conteos) {
                modeloHistorial.addRow(new Object[]{c.getIdConteo(),
                    c.getFecha().format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")),
                    c.getBodega().getNombre(), c.tipo(), c.getUsuario(), c.getProductosContados(),
                    c.getConDiferencia(), String.format("%.1f %%", c.exactitud())});
            }
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void mostrarDetalle() {
        int fila = tablaHistorial.getSelectedRow();
        modeloDetalle.setRowCount(0);
        if (fila < 0 || fila >= conteos.size()) {
            return;
        }
        try {
            for (LineaConteo l : controlador.detalleConteo(conteos.get(fila).getIdConteo())) {
                modeloDetalle.addRow(new Object[]{l.getCodigoProducto(), l.getNombreProducto(),
                    l.getStockSistema(), l.getCantidadContada(), l.diferencia()});
            }
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    // Clase ABC elegida en el tipo de conteo; null si es general.
    private Character clase() {
        int i = cmbTipo.getSelectedIndex();
        return i <= 0 ? null : "ABC".charAt(i - 1);
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
            selector.setSelectedFile(new File("conteo_" + b.getCodigo() + (clase() == null ? "" : "_clase" + clase())
                    + "_" + LocalDate.now() + ".csv"));
            selector.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
            if (selector.showSaveDialog(this) == JFileChooser.APPROVE_OPTION) {
                File archivo = selector.getSelectedFile();
                if (!archivo.getName().toLowerCase().endsWith(".csv")) {
                    archivo = new File(archivo.getParentFile(), archivo.getName() + ".csv");
                }
                int productos = controlador.generarPlantilla(b.getIdBodega(), archivo, clase(), chkCiegas.isSelected());
                Tema.info(this, "Plantilla con " + productos + " producto(s) guardada en:\n" + archivo.getAbsolutePath());
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
            claseCargada = clase();
            ciegasCargado = chkCiegas.isSelected();
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
            btnGenerar.setEnabled(true);   // un conteo sin diferencias también se guarda en el historial
        } catch (NegocioException ex) {
            limpiarResultado();
            Tema.error(this, ex.getMessage());
        }
    }

    private void generarAjustes() {
        int opcion = JOptionPane.showConfirmDialog(this,
                "¿Guardar el conteo de " + bodegaCargada.getNombre() + " y generar los ajustes de las diferencias?",
                "Confirmar conteo", JOptionPane.YES_NO_OPTION);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            Conteo c = controlador.guardarConteo(bodegaCargada.getIdBodega(), lineas, claseCargada, ciegasCargado);
            Tema.info(this, "Conteo N.º " + c.getIdConteo() + " guardado. Exactitud: "
                    + String.format("%.1f %%", c.exactitud()) + ".\n"
                    + (c.getConDiferencia() == 0 ? "No hubo diferencias: no se generaron ajustes."
                    : "Se generaron " + c.getConDiferencia() + " ajuste(s). Los positivos ya se aplicaron; "
                    + "los negativos quedan Pendientes del Supervisor."));
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
