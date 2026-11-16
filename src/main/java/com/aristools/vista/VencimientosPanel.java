package com.aristools.vista;

import com.aristools.controlador.ParametroControlador;
import com.aristools.controlador.ReporteControlador;
import com.aristools.modelo.LoteVencimiento;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Control de vencimientos con la regla FEFO (First Expired, First Out):
 * lo primero que vence es lo primero que debe salir.
 *
 * Muestra los lotes que todavía tienen unidades en bodega, ordenados por
 * fecha de vencimiento. El primero de la lista es el que el Almacenista debe
 * despachar primero. Los vencidos salen en rojo y los que están por vencer,
 * en naranja.
 */
public class VencimientosPanel extends JPanel implements Recargable {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    private static final int COL_ESTADO = 7;

    private final ReporteControlador reportes = new ReporteControlador();
    private final ParametroControlador parametros = new ParametroControlador();
    private final JCheckBox chkSoloAlerta = new JCheckBox("Solo vencidos y por vencer");
    private final JLabel lblResumen = new JLabel(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"Orden de salida", "Código", "Producto",
        "Lote", "Vence", "Días", "Unidades (estimado)", "Estado", "Recibido", "Proveedor"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };

    public VencimientosPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Vencimientos"));
        textos.add(Tema.subtitulo("Lotes con fecha de vencimiento. Despache primero el que está más arriba (FEFO)."));

        JButton actualizar = Tema.botonPrincipal("Actualizar");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        JButton imprimir = Tema.botonSecundario("Imprimir PDF");
        actualizar.addActionListener(e -> recargar());
        chkSoloAlerta.addActionListener(e -> recargar());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, modelo, "vencimientos")));
        imprimir.addActionListener(e -> ejecutar(() -> ExportadorPdf.exportar(this, modelo, "Control de vencimientos (FEFO)",
                List.of("Lotes con unidades en bodega, ordenados por fecha de vencimiento.",
                        "Las unidades por lote se estiman con la regla FEFO: sale primero lo que vence primero."),
                lblResumen.getText(), "vencimientos")));
        chkSoloAlerta.setOpaque(false);
        chkSoloAlerta.setFont(Tema.NORMAL);
        JPanel barra = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        barra.setBackground(Tema.BLANCO);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(6, 10, 6, 10)));
        barra.add(chkSoloAlerta);
        barra.add(actualizar);
        barra.add(exportar);
        barra.add(imprimir);
        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(barra, BorderLayout.CENTER);

        JTable tabla = new JTable(modelo);
        Tema.estilizarTabla(tabla);
        int[] anchos = {110, 80, 250, 100, 95, 55, 150, 95, 80, 220};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getColumnModel().getColumn(COL_ESTADO).setCellRenderer(new RenderEstado());
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        lblResumen.setFont(Tema.NEGRITA);
        JLabel nota = new JLabel("<html><body style='width:780px'>El lote y la fecha se registran en la compra de los "
                + "productos marcados con <b>Controla vencimiento</b>. El sistema no lleva el stock lote por lote: "
                + "reparte las unidades que hay en bodega suponiendo que siempre sale primero lo que vence primero.</body></html>");
        nota.setFont(Tema.NORMAL.deriveFont(12f));
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setOpaque(false);
        sur.add(lblResumen, BorderLayout.NORTH);
        sur.add(nota, BorderLayout.CENTER);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
    }

    @Override
    public void recargar() {
        ejecutar(() -> {
            int aviso = parametros.diasAvisoVencimiento();
            LocalDate hoy = LocalDate.now();
            modelo.setRowCount(0);
            int vencidos = 0;
            int porVencer = 0;
            int orden = 0;
            for (LoteVencimiento l : reportes.vencimientos()) {
                String estado = l.estado(hoy, aviso);
                if (LoteVencimiento.VENCIDO.equals(estado)) {
                    vencidos++;
                } else if (LoteVencimiento.POR_VENCER.equals(estado)) {
                    porVencer++;
                } else if (chkSoloAlerta.isSelected()) {
                    continue;
                }
                orden++;
                modelo.addRow(new Object[]{orden, l.getProducto().getCodigo(), l.getProducto().getNombre(), l.getLote(),
                    l.getFechaVencimiento().format(FECHA), l.diasParaVencer(hoy), l.getSaldoEstimado(), estado,
                    l.getCantidadRecibida(), l.getProveedor() == null ? "—" : l.getProveedor()});
            }
            lblResumen.setText(modelo.getRowCount() + " lote(s) · " + vencidos + " vencido(s) · " + porVencer
                    + " por vencer en los próximos " + aviso + " días");
        });
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    /** Rojo si ya venció, naranja si está por vencer, verde si está vigente. */
    private static class RenderEstado extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable t, Object v, boolean s, boolean f, int r, int c) {
            Component comp = super.getTableCellRendererComponent(t, v, s, f, r, c);
            String estado = String.valueOf(v);
            comp.setForeground(LoteVencimiento.VENCIDO.equals(estado) ? Tema.ROJO
                    : LoteVencimiento.POR_VENCER.equals(estado) ? Tema.NARANJA : Tema.VERDE);
            comp.setFont(Tema.NEGRITA);
            return comp;
        }
    }
}
