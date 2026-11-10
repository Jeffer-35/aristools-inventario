package com.aristools.vista;

import com.aristools.controlador.AutorizacionControlador;
import com.aristools.controlador.ReporteControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.CambioStock;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.Existencia;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.Salida;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Bandeja de autorizaciones. La misma clase sirve para los dos roles:
 *  - Naturaleza.ENTRADA: el Jefe de Compras autoriza o rechaza entradas (RF-010).
 *  - Naturaleza.SALIDA: el Supervisor autoriza o rechaza mermas, productos
 *    dañados o vencidos, devoluciones a proveedor y ajustes negativos (RF-011).
 * Quien registró un movimiento no puede autorizarlo (segregación de funciones);
 * esa regla la valida el modelo y la base de datos.
 */
public class AutorizacionesPanel extends JPanel implements Recargable {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final Naturaleza naturaleza;
    private final AutorizacionControlador controlador = new AutorizacionControlador();
    private final ReporteControlador reportes = new ReporteControlador();
    private final DefaultTableModel modelo;
    private final JTable tabla;
    private final JLabel lblDetalle = new JLabel("Seleccione un movimiento de la tabla.");
    private final JTextField txtMotivoRechazo = new JTextField();
    private final JLabel lblCantidad = new JLabel(" ");
    private List<Movimiento> pendientes = new ArrayList<>();

    public AutorizacionesPanel(Naturaleza naturaleza) {
        this.naturaleza = naturaleza;
        boolean entradas = naturaleza == Naturaleza.ENTRADA;
        setLayout(new BorderLayout(0, 14));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        // Encabezado
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo(entradas ? "Autorizar entradas" : "Autorizaciones"));
        textos.add(Tema.subtitulo(entradas
                ? "Revise las entradas de mercancía y autorícelas o recházelas."
                : "Revise las salidas críticas y los ajustes negativos del conteo físico."));
        JButton btnActualizar = Tema.botonSecundario("Actualizar");
        btnActualizar.addActionListener(e -> recargar());
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        derecha.setOpaque(false);
        lblCantidad.setFont(Tema.NEGRITA);
        derecha.add(lblCantidad);
        derecha.add(btnActualizar);
        encabezado.add(textos, BorderLayout.WEST);
        encabezado.add(derecha, BorderLayout.EAST);

        // Tabla de pendientes
        String[] columnas = entradas
                ? new String[]{"N.º", "Fecha", "Tipo", "Producto", "Bodega", "Cantidad", "Proveedor", "Documento", "Registró"}
                : new String[]{"N.º", "Fecha", "Tipo", "Producto", "Bodega", "Cantidad", "Stock actual", "Motivo", "Registró"};
        modelo = new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int f, int c) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        Tema.estilizarTabla(tabla);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = {50, 120, 150, 250, 140, 80, 140, 160, 100};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                mostrarDetalle();
            }
        });
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        scroll.setPreferredSize(new Dimension(800, 300));

        // Panel de decisión
        JPanel decision = new JPanel(new BorderLayout(0, 10));
        decision.setBackground(Tema.BLANCO);
        decision.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));
        lblDetalle.setFont(Tema.NORMAL);
        JPanel filaMotivo = new JPanel(new BorderLayout(10, 0));
        filaMotivo.setOpaque(false);
        JLabel lblMotivo = new JLabel("Motivo del rechazo");
        lblMotivo.setFont(Tema.NEGRITA);
        txtMotivoRechazo.setFont(Tema.NORMAL);
        txtMotivoRechazo.putClientProperty("JTextField.placeholderText", "Obligatorio solo si va a rechazar");
        filaMotivo.add(lblMotivo, BorderLayout.WEST);
        filaMotivo.add(txtMotivoRechazo, BorderLayout.CENTER);
        JButton btnRechazar = Tema.botonSecundario("Rechazar");
        JButton btnAutorizar = Tema.botonPrincipal("Autorizar");
        btnRechazar.addActionListener(e -> rechazar());
        btnAutorizar.addActionListener(e -> autorizar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(btnRechazar);
        botones.add(btnAutorizar);
        decision.add(lblDetalle, BorderLayout.NORTH);
        decision.add(filaMotivo, BorderLayout.CENTER);
        decision.add(botones, BorderLayout.SOUTH);

        add(encabezado, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(decision, BorderLayout.SOUTH);
    }

    /** Vuelve a consultar los movimientos pendientes. */
    @Override
    public void recargar() {
        try {
            pendientes = controlador.listarPendientes(naturaleza);
            modelo.setRowCount(0);
            for (Movimiento m : pendientes) {
                modelo.addRow(fila(m));
            }
            lblCantidad.setText(pendientes.size() + " pendiente(s)");
            lblDetalle.setText("Seleccione un movimiento de la tabla.");
            txtMotivoRechazo.setText("");
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private Object[] fila(Movimiento m) {
        Bodega bodega = bodegaAfectada(m);
        String registro = m.getUsuarioRegistra() == null ? "" : m.getUsuarioRegistra().getNombreUsuario();
        String fecha = m.getFecha() == null ? "" : m.getFecha().format(FECHA);
        if (naturaleza == Naturaleza.ENTRADA) {
            Proveedor p = m instanceof Entrada e ? e.getProveedor() : null;
            return new Object[]{m.getIdMovimiento(), fecha, m.getTipo().getNombre(), m.getProducto().getNombre(),
                bodega == null ? "" : bodega.getNombre(), m.getCantidad(), p == null ? "—" : p.getRazonSocial(),
                m.getDocumentoSoporte() == null ? "—" : m.getDocumentoSoporte(), registro};
        }
        return new Object[]{m.getIdMovimiento(), fecha, m.getTipo().getNombre(), m.getProducto().getNombre(),
            bodega == null ? "" : bodega.getNombre(), m.getCantidad(), stockActual(m, bodega),
            m.getMotivo() == null ? "—" : m.getMotivo(), registro};
    }

    /**
     * Bodega que afecta el movimiento. Se obtiene con polimorfismo: cada
     * subclase (Entrada, Salida, Ajuste) calcula sus propios cambios de stock.
     */
    private static Bodega bodegaAfectada(Movimiento m) {
        List<CambioStock> cambios = m.calcularCambiosStock();
        return cambios.isEmpty() ? null : cambios.get(0).getBodega();
    }

    /** Unidades que hay hoy en la bodega, para decidir si la salida se puede autorizar. */
    private int stockActual(Movimiento m, Bodega bodega) {
        if (bodega == null) {
            return 0;
        }
        for (Existencia e : reportes.consultarExistencias(m.getProducto().getCodigo(), null, bodega.getIdBodega(), true)) {
            if (e.getProducto().getIdProducto() == m.getProducto().getIdProducto()) {
                return e.getCantidad();
            }
        }
        return 0;
    }

    private Movimiento seleccionado() {
        int fila = tabla.getSelectedRow();
        return fila >= 0 && fila < pendientes.size() ? pendientes.get(fila) : null;
    }

    private void mostrarDetalle() {
        Movimiento m = seleccionado();
        if (m == null) {
            lblDetalle.setText("Seleccione un movimiento de la tabla.");
            return;
        }
        String extra = "";
        if (m instanceof Salida s && s.getProveedor() != null) {
            extra = " · Proveedor: " + s.getProveedor().getRazonSocial();
        }
        lblDetalle.setText("<html><b>Movimiento N.º " + m.getIdMovimiento() + ":</b> " + m.getTipo().getNombre()
                + " de " + m.getCantidad() + " · " + m.getProducto().getNombre() + extra
                + (m.getObservacion() == null ? "" : " · Observación: " + m.getObservacion()) + "</html>");
    }

    private void autorizar() {
        Movimiento m = seleccionado();
        if (m == null) {
            Tema.error(this, "Seleccione un movimiento de la tabla.");
            return;
        }
        int opcion = JOptionPane.showConfirmDialog(this, "¿Autorizar el movimiento N.º " + m.getIdMovimiento()
                + " (" + m.getTipo().getNombre() + " de " + m.getCantidad() + ")?\nEl stock se actualizará.",
                "Confirmar autorización", JOptionPane.YES_NO_OPTION);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }
        try {
            controlador.autorizar(m.getIdMovimiento());
            Tema.info(this, "Movimiento autorizado. El stock se actualizó.");
            recargar();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
            recargar();   // por si otro usuario ya lo había resuelto
        }
    }

    private void rechazar() {
        Movimiento m = seleccionado();
        if (m == null) {
            Tema.error(this, "Seleccione un movimiento de la tabla.");
            return;
        }
        try {
            controlador.rechazar(m.getIdMovimiento(), txtMotivoRechazo.getText().trim());
            Tema.info(this, "Movimiento rechazado. El stock no cambió.");
            recargar();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }
}
