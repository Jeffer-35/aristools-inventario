package com.aristools.vista;

import javax.swing.JCheckBox;
import javax.swing.JPasswordField;

/**
 * Casilla "Mostrar contraseña": al marcarla se ven los caracteres escritos
 * en los campos de contraseña; al desmarcarla vuelven los puntos. Ayuda a no
 * equivocarse al crear o cambiar una contraseña (el inicio de sesión no la usa).
 */
public final class MostrarClave {

    private MostrarClave() {
    }

    /** Crea la casilla conectada a uno o varios campos de contraseña. */
    public static JCheckBox casilla(String texto, JPasswordField... campos) {
        char puntos = campos[0].getEchoChar();          // carácter que oculta la clave
        JCheckBox c = new JCheckBox(texto);
        c.setOpaque(false);
        c.setFont(Tema.NORMAL.deriveFont(12.5f));
        c.setForeground(Tema.TEXTO_SUAVE);
        c.addActionListener(e -> {
            for (JPasswordField f : campos) {
                f.setEchoChar(c.isSelected() ? (char) 0 : puntos);   // (char) 0 = mostrar el texto
            }
        });
        // si el primer campo se deshabilita, la casilla también
        campos[0].addPropertyChangeListener("enabled", e -> c.setEnabled(campos[0].isEnabled()));
        return c;
    }
}
