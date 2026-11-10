package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.controlador.ProductoControlador;
import com.aristools.controlador.ReporteControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.Existencia;
import com.aristools.modelo.Producto;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.GridLayout;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Consulta de existencias (RF-013): solo lectura, con filtros por texto,
 * categoría y bodega. Los productos inactivos solo se muestran si se pide.
 * Las alertas de bajo stock y sobrestock (RF-012) se calculan con el total de
 * todas las bodegas y se ven en el panel principal.
 */
public class ExistenciasPanel extends JPanel implements Recargable {

    private final ReporteControlador reportes = new ReporteControlador();
    private final ProductoControlador productos = new ProductoControlador();
    private final BodegaControlador bodegas = new BodegaControlador();
    private final JTextField txtBuscar = new JTextField(16);
    private final JComboBox<Object> cmbCategoria = new JComboBox<>();
    private final JComboBox<Object> cmbBodega = new JComboBox<>();
    private final JCheckBox chkInactivos = new JCheckBox("Ver inactivos");
    private final JLabel lblResumen = new JLabel(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(
            new String[]{"Código", "Producto", "Categoría", "Bodega", "Stock", "Mínimo", "Máximo", "Estado"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private boolean cargandoFiltros;

    public ExistenciasPanel() {
        setLayout(new BorderLayout(0, 14));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Existencias"));
        textos.add(Tema.subtitulo("Cuánto hay de cada producto y en qué bodega. Consulta de solo lectura."));

        txtBuscar.putClientProperty("JTextField.placeholderText", "Código o nombre");
        chkInactivos.setOpaque(false);
        JButton btnConsultar = Tema.botonPrincipal("Consultar");
        JButton btnExportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        btnConsultar.addActionListener(e -> consultar());
        btnExportar.addActionListener(e -> ExportadorCsv.exportar(this, modelo, "existencias"));
        txtBuscar.addActionListener(e -> consultar());
        BarraFiltros filtros = new BarraFiltros(3);
        filtros.agregar("Buscar:", txtBuscar);
        filtros.agregar("Categoría:", cmbCategoria);
        filtros.agregar("Bodega:", cmbBodega);
        filtros.agregar("", chkInactivos);
        filtros.agregarBotones(btnConsultar, btnExportar);

        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(filtros, BorderLayout.CENTER);

        JTable tabla = new JTable(modelo);
        Tema.estilizarTabla(tabla);
        int[] anchos = {80, 280, 130, 150, 70, 70, 70, 80};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        lblResumen.setFont(Tema.NEGRITA);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(lblResumen, BorderLayout.SOUTH);
    }

    /** Carga los filtros y hace la consulta con todo. */
    @Override
    public void recargar() {
        try {
            cargandoFiltros = true;
            cmbCategoria.removeAllItems();
            cmbCategoria.addItem("Todas");
            for (Categoria c : productos.listarCategorias(false)) {
                cmbCategoria.addItem(c);
            }
            cmbBodega.removeAllItems();
            cmbBodega.addItem("Todas");
            for (Bodega b : bodegas.listar(false)) {
                cmbBodega.addItem(b);
            }
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        } finally {
            cargandoFiltros = false;
        }
        consultar();
    }

    private void consultar() {
        if (cargandoFiltros) {
            return;
        }
        try {
            Integer idCategoria = cmbCategoria.getSelectedItem() instanceof Categoria c ? c.getIdCategoria() : null;
            Integer idBodega = cmbBodega.getSelectedItem() instanceof Bodega b ? b.getIdBodega() : null;
            String filtro = txtBuscar.getText().trim();
            List<Existencia> lista = reportes.consultarExistencias(filtro.isEmpty() ? null : filtro,
                    idCategoria, idBodega, chkInactivos.isSelected());
            modelo.setRowCount(0);
            int unidades = 0;
            for (Existencia e : lista) {
                Producto p = e.getProducto();
                modelo.addRow(new Object[]{p.getCodigo(), p.getNombre(),
                    p.getCategoria() == null ? "" : p.getCategoria().getNombre(),
                    e.getBodega() == null ? "" : e.getBodega().getNombre(), e.getCantidad(),
                    p.getStockMinimo() == null ? "General" : p.getStockMinimo(),
                    p.getStockMaximo() == null ? "General" : p.getStockMaximo(), p.getEstado()});
                unidades += e.getCantidad();
            }
            lblResumen.setText(lista.size() + " registro(s) · " + unidades + " unidades en total");
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }
}
