package com.aristools.vista;

import java.awt.GridBagLayout;
import javax.swing.BorderFactory;
import javax.swing.BoxLayout;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Pantalla temporal para las opciones del menú que se construyen en las
 * partes 9.2 a 9.4. Permite probar la navegación desde ya.
 */
public class PanelEnConstruccion extends JPanel {

    public PanelEnConstruccion(String modulo, String parte) {
        setLayout(new GridBagLayout());
        setBackground(Tema.FONDO);
        JPanel caja = new JPanel();
        caja.setLayout(new BoxLayout(caja, BoxLayout.Y_AXIS));
        caja.setBackground(Tema.BLANCO);
        caja.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                BorderFactory.createEmptyBorder(30, 40, 30, 40)));
        JLabel titulo = Tema.titulo(modulo);
        JLabel texto = Tema.subtitulo("Este módulo se construye en la " + parte + " del paso 9.");
        titulo.setAlignmentX(CENTER_ALIGNMENT);
        texto.setAlignmentX(CENTER_ALIGNMENT);
        caja.add(titulo);
        caja.add(javax.swing.Box.createVerticalStrut(10));
        caja.add(texto);
        add(caja);
    }
}
