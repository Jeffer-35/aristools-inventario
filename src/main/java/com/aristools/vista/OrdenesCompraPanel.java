package com.aristools.vista;

import com.aristools.controlador.CompraControlador;
import com.aristools.modelo.EstadoOrden;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.OrdenCompra;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Seguimiento de las órdenes de compra (Jefe de Compras y Administrador).
 *
 * Arriba están las órdenes con su estado y cuánto ha llegado; abajo, los
 * productos de la orden seleccionada: lo pedido, lo recibido y lo que falta.
 * La orden pasa sola a "Recibida en parte" o "Recibida" cuando se autoriza la
 * entrada que la recibe. Si el proveedor no va a entregar lo que falta, la
 * orden se anula con un motivo (queda en la bitácora).
 */
public class OrdenesCompraPanel extends JPanel implements Recargable {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final int COL_ESTADO = 3;

    private final CompraControlador compras = new CompraControlador();
    private final JCheckBox chkAbiertas = new JCheckBox("Solo las que esperan mercancía", true);
    private final JLabel lblResumen = new JLabel(" ");
    private final JLabel lblDetalle = new JLabel("Seleccione una orden para ver sus productos.");
    private List<OrdenCompra> ordenes = new ArrayList<>();
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"N.º", "Fecha", "Proveedor", "Estado",
        "Prod.", "Pedido", "Recibido", "% recibido", "Valor estimado", "Generó"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private final DefaultTableModel detalle = new DefaultTableModel(new String[]{"Código", "Producto", "Unidad",
        "Pedido", "Recibido", "Falta", "En espera", "Costo unit.", "Subtotal"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);

    public OrdenesCompraPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Órdenes de compra"));
        textos.add(Tema.subtitulo("Qué se le pidió a cada proveedor y cuánto ha llegado."));

        JButton actualizar = Tema.botonPrincipal("Actualizar");
        JButton pdf = Tema.botonSecundario("Imprimir la orden (PDF)");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        JButton anular = Tema.botonSecundario("Anular la orden");
        actualizar.addActionListener(e -> recargar());
        chkAbiertas.addActionListener(e -> recargar());
        pdf.addActionListener(e -> ejecutar(() -> {
            OrdenCompra o = seleccionada();
            if (o != null) {
                ExportadorPdf.ordenCompra(this, o);
            }
        }));
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, modelo, "ordenes_compra")));
        anular.addActionListener(e -> ejecutar(this::anular));
        chkAbiertas.setOpaque(false);
        chkAbiertas.setFont(Tema.NORMAL);
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        barra.setBackground(Tema.BLANCO);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        barra.add(chkAbiertas);
        barra.add(actualizar);
        barra.add(pdf);
        barra.add(exportar);
        barra.add(anular);
        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(barra, BorderLayout.CENTER);

        Tema.estilizarTabla(tabla);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = {95, 120, 220, 125, 50, 65, 70, 80, 115, 90};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getColumnModel().getColumn(COL_ESTADO).setCellRenderer(new RenderEstado());
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrarDetalle();
            }
        });
        JScrollPane scrollOrdenes = new JScrollPane(tabla);
        scrollOrdenes.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scrollOrdenes.setPreferredSize(new java.awt.Dimension(900, 190));

        JTable tablaDetalle = new JTable(detalle);
        Tema.estilizarTabla(tablaDetalle);
        tablaDetalle.getColumnModel().getColumn(1).setPreferredWidth(260);
        JScrollPane scrollDetalle = new JScrollPane(tablaDetalle);
        scrollDetalle.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scrollDetalle.setPreferredSize(new java.awt.Dimension(900, 160));
        lblDetalle.setFont(Tema.NEGRITA);
        JPanel abajo = new JPanel(new BorderLayout(0, 6));
        abajo.setOpaque(false);
        abajo.add(lblDetalle, BorderLayout.NORTH);
        abajo.add(scrollDetalle, BorderLayout.CENTER);

        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, scrollOrdenes, abajo);
        division.setResizeWeight(0.5);
        division.setBorder(null);
        division.setOpaque(false);

        lblResumen.setFont(Tema.NEGRITA);
        JLabel nota = new JLabel("<html><body style='width:700px'>La orden se recibe en <b>Movimientos → Entrada</b>: "
                + "el Almacenista elige la orden al registrar la compra y, cuando el Jefe de Compras autoriza esa "
                + "entrada, la orden pasa a <b>Recibida en parte</b> o <b>Recibida</b>. Anular una orden no borra lo "
                + "que ya llegó: solo deja de esperarse lo que faltaba.</body></html>");
        nota.setFont(Tema.NORMAL.deriveFont(12f));
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setOpaque(false);
        sur.add(lblResumen, BorderLayout.NORTH);
        sur.add(nota, BorderLayout.CENTER);

        add(norte, BorderLayout.NORTH);
        add(division, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
    }

    @Override
    public void recargar() {
        ejecutar(() -> {
            ordenes = compras.listarOrdenes(chkAbiertas.isSelected());
            modelo.setRowCount(0);
            int abiertas = 0;
            for (OrdenCompra o : ordenes) {
                if (o.getEstado().estaAbierta()) {
                    abiertas++;
                }
                modelo.addRow(new Object[]{o.numero(), o.getFecha().format(FECHA),
                    o.getProveedor() == null ? "—" : o.getProveedor().getRazonSocial(), o.getEstado().texto(),
                    o.getLineas().size(), o.unidadesPedidas(), o.unidadesRecibidas(), o.porcentajeRecibido() + " %",
                    Formato.pesos(o.total()), o.getUsuario().getNombreUsuario()});
            }
            detalle.setRowCount(0);
            lblDetalle.setText("Seleccione una orden para ver sus productos.");
            lblResumen.setText(ordenes.size() + " orden(es) · " + abiertas + " esperando mercancía");
        });
    }

    private OrdenCompra seleccionada() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= ordenes.size()) {
            Tema.error(this, "Seleccione una orden de la tabla.");
            return null;
        }
        return ordenes.get(fila);
    }

    private void mostrarDetalle() {
        detalle.setRowCount(0);
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= ordenes.size()) {
            return;
        }
        OrdenCompra o = ordenes.get(fila);
        for (LineaOrdenCompra l : o.getLineas()) {
            detalle.addRow(new Object[]{l.getProducto().getCodigo(), l.getProducto().getNombre(),
                l.getProducto().getUnidadMedida(), l.getCantidad(), l.getCantidadRecibida(),
                o.getEstado() == EstadoOrden.ANULADA ? "Anulado" : String.valueOf(l.pendiente()), l.getEnEspera(),
                Formato.pesos(l.getCostoUnitario()), Formato.pesos(l.subtotal())});
        }
        lblDetalle.setText("Productos de la orden " + o.numero() + " · " + o.getEstado().texto()
                + (o.getMotivoAnulacion() == null ? "" : " · Motivo: " + o.getMotivoAnulacion()));
    }

    private void anular() {
        OrdenCompra o = seleccionada();
        if (o == null) {
            return;
        }
        JTextField motivo = new JTextField(30);
        Object[] mensaje = {"¿Anular la orden " + o.numero() + " de " + o.getProveedor().getRazonSocial() + "?",
            "Lo que ya llegó (" + o.porcentajeRecibido() + " %) se queda en el inventario; lo que falta deja de esperarse.",
            " ", "Motivo (obligatorio):", motivo};
        if (JOptionPane.showConfirmDialog(this, mensaje, "Anular orden de compra", JOptionPane.OK_CANCEL_OPTION,
                JOptionPane.WARNING_MESSAGE) != JOptionPane.OK_OPTION) {
            return;
        }
        compras.anularOrden(o.getIdOrden(), motivo.getText());
        Tema.info(this, "La orden " + o.numero() + " quedó anulada.");
        recargar();
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    /** Naranja si espera mercancía, verde si llegó todo y gris si se anuló. */
    private static class RenderEstado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
            String estado = String.valueOf(v);
            comp.setForeground(EstadoOrden.RECIBIDA.texto().equals(estado) ? Tema.VERDE
                    : EstadoOrden.ANULADA.texto().equals(estado) ? Tema.TEXTO_SUAVE : Tema.NARANJA);
            comp.setFont(Tema.NEGRITA);
            return comp;
        }
    }
}
