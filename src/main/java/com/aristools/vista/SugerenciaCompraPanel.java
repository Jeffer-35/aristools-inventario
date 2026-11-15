package com.aristools.vista;

import com.aristools.controlador.CompraControlador;
import com.aristools.controlador.ProveedorControlador;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.OrdenCompra;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.SugerenciaCompra;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.SwingConstants;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Sugerencia de compra (Jefe de Compras y Administrador). Muestra qué
 * productos llegaron a su punto de reorden y cuánto pedir de cada uno. La
 * columna "Pedir" se puede cambiar antes de generar la orden de compra.
 *
 * La orden se genera para un proveedor: incluye los productos de ese
 * proveedor (y los que nunca se han comprado) con cantidad a pedir mayor
 * que cero, se guarda con su número OC- y se imprime en PDF.
 */
public class SugerenciaCompraPanel extends JPanel implements Recargable {

    private static final int COL_PEDIR = 9;

    private final CompraControlador compras = new CompraControlador();
    private final ProveedorControlador proveedores = new ProveedorControlador();
    private final JComboBox<Object> cmbProveedor = new JComboBox<>();
    private final JCheckBox chkSoloPedir = new JCheckBox("Solo productos que hay que pedir", true);
    private final JLabel lblResumen = new JLabel(" ");
    private final JLabel lblNota = new JLabel(" ");
    private List<SugerenciaCompra> lista = new ArrayList<>();
    // Encabezados cortos para que todas las columnas quepan; "Reorden" es el punto de reorden
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"Código", "Producto", "Proveedor",
        "Stock", "Cons./día", "Días ent.", "Reorden", "Máx.", "Sugerido", "Pedir", "Costo unit.",
        "Valor est."}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return c == COL_PEDIR;   // solo la cantidad a pedir se puede cambiar
        }

        @Override
        public Class<?> getColumnClass(int c) {
            return c == COL_PEDIR ? Integer.class : Object.class;
        }
    };
    private final JTable tabla = new JTable(modelo);

    public SugerenciaCompraPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Sugerencia de compra"));
        textos.add(Tema.subtitulo("Qué pedir y cuánto, según el consumo de los últimos "
                + CompraControlador.DIAS_ANALISIS + " días y lo que tarda cada proveedor en entregar."));

        JButton calcular = Tema.botonPrincipal("Calcular");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        JButton imprimir = Tema.botonSecundario("Imprimir PDF");
        JButton orden = Tema.botonPrincipal("Generar orden de compra");
        calcular.addActionListener(e -> calcular());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, modelo, "sugerencia_compra")));
        imprimir.addActionListener(e -> ejecutar(() -> ExportadorPdf.exportar(this, modelo, "Sugerencia de compra",
                List.of("Proveedor: " + cmbProveedor.getSelectedItem() + "   ·   "
                        + (chkSoloPedir.isSelected() ? "Solo productos que hay que pedir" : "Todos los productos"),
                        "Consumo de los últimos " + CompraControlador.DIAS_ANALISIS + " días. Punto de reorden = consumo "
                        + "diario x días de entrega + stock mínimo."), lblResumen.getText(), "sugerencia_compra")));
        orden.addActionListener(e -> generarOrden());
        chkSoloPedir.setOpaque(false);
        chkSoloPedir.addActionListener(e -> calcular());
        BarraFiltros filtros = new BarraFiltros(2);
        filtros.agregar("Proveedor:", cmbProveedor);
        filtros.agregar("", chkSoloPedir);
        filtros.agregarBotones(calcular, exportar, imprimir, orden);

        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(filtros, BorderLayout.CENTER);

        Tema.estilizarTabla(tabla);
        // Todas las columnas caben en la pantalla, para que "Pedir" siempre se vea
        int[] anchos = {65, 175, 145, 55, 75, 70, 70, 55, 75, 70, 90, 100};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.setToolTipText("Puede cambiar la cantidad de la columna Pedir antes de generar la orden");
        // La columna Pedir va resaltada porque es la única que se puede escribir
        DefaultTableCellRenderer resaltada = new DefaultTableCellRenderer();
        resaltada.setBackground(new Color(0xFFD59E));   // naranja claro, se distingue de las demás columnas
        resaltada.setFont(Tema.NEGRITA);
        resaltada.setHorizontalAlignment(SwingConstants.CENTER);
        resaltada.setToolTipText("Doble clic para cambiar la cantidad a pedir");
        tabla.getColumnModel().getColumn(COL_PEDIR).setCellRenderer(resaltada);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scroll.setPreferredSize(new Dimension(900, 340));

        lblResumen.setFont(Tema.NEGRITA);
        lblNota.setFont(Tema.NORMAL.deriveFont(12f));
        lblNota.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new GridLayout(2, 1, 0, 4));
        sur.setOpaque(false);
        sur.add(lblResumen);
        sur.add(lblNota);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
    }

    @Override
    public void recargar() {
        ejecutar(() -> {
            Object antes = cmbProveedor.getSelectedItem();
            cmbProveedor.removeAllItems();
            cmbProveedor.addItem("Todos");
            for (Proveedor p : proveedores.listar(false)) {
                cmbProveedor.addItem(p);
                if (antes != null && antes.toString().equals(p.toString())) {
                    cmbProveedor.setSelectedItem(p);
                }
            }
            lblNota.setText("Proveedores sin días de entrega propios usan " + compras.diasEntregaPorDefecto()
                    + " días (Configuración). Los productos que nunca se han comprado aparecen sin proveedor.");
        });
        calcular();
    }

    private void calcular() {
        ejecutar(() -> {
            Integer idProveedor = cmbProveedor.getSelectedItem() instanceof Proveedor p ? p.getIdProveedor() : null;
            lista = compras.sugerencias(idProveedor, chkSoloPedir.isSelected());
            modelo.setRowCount(0);
            int porPedir = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (SugerenciaCompra s : lista) {
                modelo.addRow(new Object[]{s.getProducto().getCodigo(), s.getProducto().getNombre(),
                    s.getProveedor() == null ? "Sin compras" : s.getProveedor().getRazonSocial(), s.getStockActual(),
                    s.consumoDiario().toPlainString().replace('.', ','), s.getDiasEntrega(), s.puntoReorden(),
                    s.getStockMaximo(), s.cantidadSugerida(), s.cantidadSugerida(), Formato.pesos(s.getCostoUnitario()),
                    Formato.pesos(s.valorEstimado())});
                if (s.hayQuePedir()) {
                    porPedir++;
                    total = total.add(s.valorEstimado());
                }
            }
            lblResumen.setText(lista.size() + " producto(s) · " + porPedir + " por pedir · Valor estimado: "
                    + Formato.pesos(total));
        });
    }

    /**
     * Genera la orden de compra con los productos que tienen cantidad a pedir.
     * Si el filtro está en "Todos", primero se elige el proveedor.
     */
    private void generarOrden() {
        if (tabla.isEditing()) {
            tabla.getCellEditor().stopCellEditing();   // toma la cantidad que se estaba escribiendo
        }
        ejecutar(() -> {
            Proveedor proveedor = cmbProveedor.getSelectedItem() instanceof Proveedor p ? p : elegirProveedor();
            if (proveedor == null) {
                return;
            }
            List<LineaOrdenCompra> lineas = new ArrayList<>();
            int omitidos = 0;
            for (int f = 0; f < lista.size(); f++) {
                SugerenciaCompra s = lista.get(f);
                Object valor = modelo.getValueAt(f, COL_PEDIR);
                int cantidad = valor instanceof Integer i ? i : 0;
                if (cantidad <= 0) {
                    continue;
                }
                if (s.getProveedor() != null && s.getProveedor().getIdProveedor() != proveedor.getIdProveedor()) {
                    omitidos++;   // es de otro proveedor: va en la orden de ese proveedor
                    continue;
                }
                lineas.add(new LineaOrdenCompra(s.getProducto(), cantidad, s.getCostoUnitario()));
            }
            if (lineas.isEmpty()) {
                throw new NegocioException("No hay productos con cantidad a pedir para " + proveedor.getRazonSocial()
                        + ". Escriba la cantidad en la columna Pedir.");
            }
            BigDecimal total = BigDecimal.ZERO;
            for (LineaOrdenCompra l : lineas) {
                total = total.add(l.subtotal());
            }
            JTextField observacion = new JTextField();
            Object[] mensaje = {"Orden de compra para " + proveedor.getRazonSocial() + ":\n" + lineas.size()
                    + " producto(s) por " + Formato.pesos(total) + " (valor estimado)."
                    + (omitidos > 0 ? "\n" + omitidos + " producto(s) de otros proveedores no se incluyen." : ""),
                "Observación para el proveedor (opcional):", observacion};
            if (JOptionPane.showConfirmDialog(this, mensaje, "Generar orden de compra", JOptionPane.OK_CANCEL_OPTION)
                    != JOptionPane.OK_OPTION) {
                return;
            }
            OrdenCompra orden = compras.generarOrden(proveedor, lineas, observacion.getText());
            int pdf = JOptionPane.showConfirmDialog(this, "Se guardó la orden de compra " + orden.numero()
                    + ".\n¿Desea generar el PDF para enviarlo al proveedor?", "Orden de compra", JOptionPane.YES_NO_OPTION);
            if (pdf == JOptionPane.YES_OPTION) {
                ExportadorPdf.ordenCompra(this, orden);
            }
        });
    }

    // Si el filtro está en "Todos", pregunta a qué proveedor va la orden.
    private Proveedor elegirProveedor() {
        JComboBox<Proveedor> combo = new JComboBox<>();
        for (Proveedor p : proveedores.listar(false)) {
            combo.addItem(p);
        }
        int r = JOptionPane.showConfirmDialog(this, new Object[]{"¿A qué proveedor va la orden de compra?", combo},
                "Proveedor de la orden", JOptionPane.OK_CANCEL_OPTION);
        return r == JOptionPane.OK_OPTION ? (Proveedor) combo.getSelectedItem() : null;
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }
}
