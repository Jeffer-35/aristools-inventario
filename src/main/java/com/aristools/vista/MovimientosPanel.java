package com.aristools.vista;

import java.awt.BorderLayout;
import java.awt.GridLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Pantalla Movimientos del Almacenista: pestañas de Entrada, Salida y
 * Transferencia (mockup, Figuras 12 a 14). Al cambiar de pestaña se recargan
 * los combos para mostrar siempre productos, bodegas y existencias al día.
 */
public class MovimientosPanel extends JPanel implements Recargable {

    private final JTabbedPane pestanas = new JTabbedPane();

    public MovimientosPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Movimientos"));
        textos.add(Tema.subtitulo("Registre lo que entra, sale o se traslada entre bodegas."));
        pestanas.setFont(Tema.NEGRITA);
        pestanas.addTab("Entrada", new EntradaPanel());
        pestanas.addTab("Salida", new SalidaPanel());
        pestanas.addTab("Transferencia", new TransferenciaPanel());
        pestanas.addChangeListener(e -> recargar());
        add(textos, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
    }

    @Override
    public void recargar() {
        if (pestanas.getSelectedComponent() instanceof Recargable r) {
            r.recargar();
        }
    }
}
