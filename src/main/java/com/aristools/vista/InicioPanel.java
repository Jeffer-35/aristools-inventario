package com.aristools.vista;

import com.aristools.controlador.AlertaControlador;
import com.aristools.controlador.PanelControlador;
import com.aristools.modelo.Alerta;
import com.aristools.modelo.TipoAlerta;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Panel principal (pantalla de Inicio): cuatro tarjetas con el resumen del
 * inventario y la tabla de alertas activas de bajo stock y sobrestock (RF-012).
 * Lo ven todos los roles.
 */
public class InicioPanel extends JPanel {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final PanelControlador panelControlador = new PanelControlador();
    private final AlertaControlador alertaControlador = new AlertaControlador();
    private final JPanel tarjetas = new JPanel(new GridLayout(1, 4, 16, 0));
    private final DefaultTableModel modeloAlertas = new DefaultTableModel(
            new String[]{"Tipo", "Código", "Producto", "Stock detectado", "Umbral", "Fecha"}, 0) {
        @Override
        public boolean isCellEditable(int fila, int columna) {
            return false;   // la tabla es solo de consulta
        }
    };

    public InicioPanel(String nombreUsuario) {
        setLayout(new BorderLayout(0, 18));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        // Encabezado: título, saludo y botón Actualizar
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Panel principal"));
        textos.add(Tema.subtitulo("Bienvenido, " + nombreUsuario + ". Este es el estado actual del inventario."));
        JButton btnActualizar = Tema.botonSecundario("Actualizar");
        btnActualizar.addActionListener(e -> actualizar());
        JPanel derecha = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 8));
        derecha.setOpaque(false);
        derecha.add(btnActualizar);
        encabezado.add(textos, BorderLayout.CENTER);
        encabezado.add(derecha, BorderLayout.EAST);

        // Centro: tarjetas arriba y tabla de alertas abajo
        tarjetas.setOpaque(false);
        JTable tabla = new JTable(modeloAlertas);
        Tema.estilizarTabla(tabla);
        tabla.getColumnModel().getColumn(0).setCellRenderer(new RenderTipoAlerta());
        int[] anchos = {110, 90, 330, 120, 90, 150};
        for (int i = 0; i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        JPanel bloqueAlertas = new JPanel(new BorderLayout(0, 8));
        bloqueAlertas.setOpaque(false);
        JLabel tituloAlertas = new JLabel("Alertas activas");
        tituloAlertas.setFont(Tema.NEGRITA.deriveFont(16f));
        bloqueAlertas.add(tituloAlertas, BorderLayout.NORTH);
        bloqueAlertas.add(scroll, BorderLayout.CENTER);

        JPanel centro = new JPanel(new BorderLayout(0, 22));
        centro.setOpaque(false);
        centro.add(tarjetas, BorderLayout.NORTH);
        centro.add(bloqueAlertas, BorderLayout.CENTER);

        add(encabezado, BorderLayout.NORTH);
        add(centro, BorderLayout.CENTER);
    }

    /** Vuelve a consultar la base y repinta las tarjetas y la tabla. */
    public void actualizar() {
        try {
            mostrarDatos(panelControlador.resumen(), alertaControlador.listarActivas());
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    /** Pinta los datos recibidos (separado de actualizar() para poder probarlo sin base). */
    void mostrarDatos(Map<String, Integer> resumen, List<Alerta> alertas) {
        tarjetas.removeAll();
        for (Map.Entry<String, Integer> dato : resumen.entrySet()) {
            tarjetas.add(tarjeta(dato.getKey(), dato.getValue()));
        }
        modeloAlertas.setRowCount(0);
        for (Alerta a : alertas) {
            modeloAlertas.addRow(new Object[]{
                a.getTipo() == TipoAlerta.BAJO_STOCK ? "Bajo stock" : "Sobrestock",
                a.getProducto().getCodigo(),
                a.getProducto().getNombre(),
                a.getStockDetectado(),
                a.getUmbral(),
                a.getFechaGeneracion() == null ? "" : a.getFechaGeneracion().format(FECHA)});
        }
        tarjetas.revalidate();
        tarjetas.repaint();
    }

    /** Tarjeta blanca con una franja de color a la izquierda según el dato. */
    private JPanel tarjeta(String titulo, int valor) {
        Color color = switch (titulo) {
            case "Bajo stock" -> Tema.ROJO;
            case "Sobrestock" -> Tema.NARANJA;
            case "Movimientos pendientes" -> Tema.AZUL_TABLA;
            default -> Tema.VERDE;
        };
        JPanel t = new JPanel(new BorderLayout(0, 4));
        t.setBackground(Tema.BLANCO);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 6, 0, 0, color),
                        BorderFactory.createMatteBorder(1, 0, 1, 1, Tema.BORDE)),
                BorderFactory.createEmptyBorder(14, 18, 14, 18)));
        JLabel numero = new JLabel(String.valueOf(valor));
        numero.setFont(Tema.NUMERO_TARJETA);
        numero.setForeground(color);
        JLabel texto = new JLabel(titulo);
        texto.setFont(Tema.NORMAL);
        texto.setForeground(Tema.TEXTO_SUAVE);
        t.add(numero, BorderLayout.CENTER);
        t.add(texto, BorderLayout.SOUTH);
        return t;
    }

    /** Pinta en rojo las alertas de bajo stock y en naranja las de sobrestock. */
    private static class RenderTipoAlerta extends DefaultTableCellRenderer {
        @Override
        public Component getTableCellRendererComponent(JTable tabla, Object valor, boolean seleccionado,
                boolean foco, int fila, int columna) {
            Component c = super.getTableCellRendererComponent(tabla, valor, seleccionado, foco, fila, columna);
            c.setForeground("Bajo stock".equals(valor) ? Tema.ROJO : Tema.NARANJA);
            c.setFont(Tema.NEGRITA);
            return c;
        }
    }
}
