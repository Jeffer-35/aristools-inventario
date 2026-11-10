package com.aristools.vista;

import java.awt.BorderLayout;
import java.awt.Container;
import java.awt.Dimension;
import java.awt.Rectangle;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JViewport;
import javax.swing.Scrollable;

/**
 * Envuelve una pantalla para que tenga barra de desplazamiento vertical
 * cuando no cabe (portátiles con pantalla pequeña o escala de Windows alta).
 * Si la pantalla sí cabe, ocupa todo el espacio como siempre.
 */
public class PanelDesplazable extends JPanel implements Scrollable {

    private PanelDesplazable(JPanel contenido) {
        super(new BorderLayout());
        setBackground(Tema.FONDO);
        add(contenido, BorderLayout.CENTER);
    }

    /** @return la pantalla dentro de un JScrollPane listo para el CardLayout */
    public static JScrollPane envolver(JPanel contenido) {
        JScrollPane scroll = new JScrollPane(new PanelDesplazable(contenido),
                JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scroll.setBorder(BorderFactory.createEmptyBorder());
        scroll.getVerticalScrollBar().setUnitIncrement(18);   // desplazamiento cómodo con la rueda
        scroll.getViewport().setBackground(Tema.FONDO);
        return scroll;
    }

    @Override
    public Dimension getPreferredScrollableViewportSize() {
        return getPreferredSize();
    }

    @Override
    public int getScrollableUnitIncrement(Rectangle visible, int orientacion, int direccion) {
        return 18;
    }

    @Override
    public int getScrollableBlockIncrement(Rectangle visible, int orientacion, int direccion) {
        return visible.height - 36;
    }

    /** Ocupa el ancho disponible mientras no sea menor que el mínimo de la pantalla. */
    @Override
    public boolean getScrollableTracksViewportWidth() {
        Container padre = getParent();
        return padre instanceof JViewport v && v.getWidth() >= getMinimumSize().width;
    }

    /** Ocupa el alto disponible si cabe; si no cabe, aparece la barra vertical. */
    @Override
    public boolean getScrollableTracksViewportHeight() {
        Container padre = getParent();
        return padre instanceof JViewport v && v.getHeight() >= getPreferredSize().height;
    }
}
