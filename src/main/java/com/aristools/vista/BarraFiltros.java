package com.aristools.vista;

import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JTextField;
import javax.swing.JPanel;

/**
 * Barra de filtros ordenada en cuadrícula: los pares "etiqueta: campo" se
 * acomodan en filas y los botones van en la última fila, a la derecha.
 * Así nada queda cortado aunque la pantalla sea angosta.
 */
public class BarraFiltros extends JPanel {

    private final int paresPorFila;
    private final JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    private int pares = 0;

    public BarraFiltros(int paresPorFila) {
        super(new GridBagLayout());
        this.paresPorFila = paresPorFila;
        setBackground(Tema.BLANCO);
        setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(8, 12, 8, 12)));
        botones.setOpaque(false);
    }

    /** Agrega un filtro con su etiqueta (etiqueta vacía para casillas de verificación). */
    public void agregar(String etiqueta, JComponent campo) {
        int fila = pares / paresPorFila;
        int columna = (pares % paresPorFila) * 2;
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.gridx = columna;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(4, columna == 0 ? 0 : 14, 4, 6);
        JLabel l = new JLabel(etiqueta);
        l.setFont(Tema.NEGRITA);
        add(l, c);
        c.gridx = columna + 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);
        campo.setFont(Tema.NORMAL);
        // Mínimo pequeño: si la ventana es angosta, el campo se encoge en vez de
        // empujar los botones y la tabla fuera de la pantalla (los combos con
        // nombres largos de productos o proveedores eran los que más ocupaban).
        // Los grupos de botones (como Hoy / Este mes) conservan su tamaño.
        if (campo instanceof JComboBox || campo instanceof JTextField || campo instanceof CampoFecha) {
            Dimension preferido = campo.getPreferredSize();
            campo.setMinimumSize(new Dimension(Math.min(preferido.width, 110), preferido.height));
        }
        add(campo, c);
        pares++;
    }

    /** Agrega los botones en una fila propia, alineados a la derecha. */
    public void agregarBotones(JButton... lista) {
        for (JButton b : lista) {
            botones.add(b);
        }
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = (pares + paresPorFila - 1) / paresPorFila;
        c.gridx = 0;
        c.gridwidth = paresPorFila * 2;
        c.anchor = GridBagConstraints.EAST;
        c.insets = new Insets(8, 0, 2, 0);
        add(botones, c);
    }
}
