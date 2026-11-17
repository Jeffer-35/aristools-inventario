package com.aristools.vista;

import com.aristools.controlador.IndicadorControlador;
import com.aristools.modelo.Indicador;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.table.DefaultTableModel;

/**
 * Tablero de indicadores (KPI): nueve tarjetas que resumen cómo va la bodega
 * en un período. Cada tarjeta muestra el valor, la meta y un color:
 * verde (bueno), naranja (alerta), rojo (crítico) o azul (dato informativo).
 * Debajo de cada valor se explica cómo se calcula.
 */
public class IndicadoresPanel extends JPanel implements Recargable {

    private final IndicadorControlador controlador = new IndicadorControlador();
    private final CampoFecha desde = new CampoFecha();
    private final CampoFecha hasta = new CampoFecha();
    private final JPanel tarjetas = new JPanel(new GridLayout(3, 3, 14, 14));
    private final JLabel lblPeriodo = new JLabel(" ");
    private List<Indicador> indicadores = new ArrayList<>();

    public IndicadoresPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Indicadores"));
        textos.add(Tema.subtitulo("Tablero de gestión de la bodega. Elija el período y compare cada indicador con su meta."));

        JButton calcular = Tema.botonPrincipal("Calcular");
        JButton imprimir = Tema.botonSecundario("Imprimir PDF");
        calcular.addActionListener(e -> calcular());
        imprimir.addActionListener(e -> imprimir());
        BarraFiltros filtros = new BarraFiltros(3);
        filtros.agregar("Desde:", desde);
        filtros.agregar("Hasta:", hasta);
        filtros.agregar("Período:", CampoFecha.accesosRapidos(desde, hasta));
        filtros.agregarBotones(calcular, imprimir);
        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(filtros, BorderLayout.CENTER);

        tarjetas.setOpaque(false);
        lblPeriodo.setFont(Tema.NORMAL.deriveFont(12f));
        lblPeriodo.setForeground(Tema.TEXTO_SUAVE);
        add(norte, BorderLayout.NORTH);
        add(tarjetas, BorderLayout.CENTER);
        add(lblPeriodo, BorderLayout.SOUTH);
    }

    /** Al abrir la pantalla se calcula el mes en curso. */
    @Override
    public void recargar() {
        if (desde.texto().isEmpty() && hasta.texto().isEmpty()) {
            LocalDate hoy = LocalDate.now();
            desde.setFecha(hoy.withDayOfMonth(1));
            hasta.setFecha(hoy);
        }
        calcular();
    }

    private void calcular() {
        try {
            indicadores = controlador.indicadores(desde.getFecha("Desde"), hasta.getFecha("Hasta"));
            tarjetas.removeAll();
            for (Indicador i : indicadores) {
                tarjetas.add(tarjeta(i));
            }
            lblPeriodo.setText("Período del " + desde.texto() + " al " + hasta.texto()
                    + ". Verde: cumple la meta · Naranja: en alerta · Rojo: crítico · Azul: dato informativo.");
            tarjetas.revalidate();
            tarjetas.repaint();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void imprimir() {
        try {
            DefaultTableModel modelo = new DefaultTableModel(new String[]{"Indicador", "Valor", "Meta", "Estado", "Cómo se calcula"}, 0);
            for (Indicador i : indicadores) {
                modelo.addRow(new Object[]{i.getNombre(), i.getValor(), i.getMeta(), texto(i.getEstado()), i.getFormula()});
            }
            ExportadorPdf.exportar(this, modelo, "Tablero de indicadores de la bodega",
                    List.of("Período: del " + desde.texto() + " al " + hasta.texto()),
                    indicadores.size() + " indicadores", "indicadores");
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private JPanel tarjeta(Indicador i) {
        Color color = color(i.getEstado());
        JPanel t = new JPanel(new BorderLayout(0, 6));
        t.setBackground(Tema.BLANCO);
        t.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createCompoundBorder(
                        BorderFactory.createMatteBorder(0, 6, 0, 0, color),
                        BorderFactory.createMatteBorder(1, 0, 1, 1, Tema.BORDE)),
                BorderFactory.createEmptyBorder(12, 16, 12, 16)));
        JLabel nombre = new JLabel(i.getNombre());
        nombre.setFont(Tema.NEGRITA);
        nombre.setForeground(Tema.TEXTO);
        JLabel valor = new JLabel(i.getValor());
        valor.setFont(Tema.TITULO);
        valor.setForeground(color);
        JLabel meta = new JLabel(i.getMeta() + (i.getEstado() == Indicador.Estado.INFORMATIVO ? "" : "  ·  " + texto(i.getEstado())));
        meta.setFont(Tema.NORMAL.deriveFont(13f));
        meta.setForeground(Tema.TEXTO_SUAVE);
        JLabel formula = new JLabel("<html><body style='width:230px'>" + i.getFormula() + "</body></html>");
        formula.setFont(Tema.NORMAL.deriveFont(11.5f));
        formula.setForeground(Tema.TEXTO_SUAVE);
        JPanel centro = new JPanel(new GridLayout(2, 1, 0, 2));
        centro.setOpaque(false);
        centro.add(valor);
        centro.add(meta);
        t.add(nombre, BorderLayout.NORTH);
        t.add(centro, BorderLayout.CENTER);
        t.add(formula, BorderLayout.SOUTH);
        return t;
    }

    private static Color color(Indicador.Estado e) {
        return switch (e) {
            case BUENO -> Tema.VERDE;
            case ALERTA -> Tema.NARANJA;
            case CRITICO -> Tema.ROJO;
            default -> Tema.AZUL_TABLA;
        };
    }

    private static String texto(Indicador.Estado e) {
        return switch (e) {
            case BUENO -> "Cumple";
            case ALERTA -> "En alerta";
            case CRITICO -> "Crítico";
            default -> "Informativo";
        };
    }
}
