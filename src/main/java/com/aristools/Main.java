package com.aristools;

import com.aristools.modelo.Usuario;
import com.aristools.vista.LoginVista;
import com.aristools.vista.PrincipalVista;
import com.aristools.vista.Tema;
import javax.swing.SwingUtilities;

/**
 * Punto de entrada del aplicativo: aplica la apariencia, pide el inicio de
 * sesión y abre la ventana principal con el menú del rol del usuario.
 */
public class Main {

    public static void main(String[] args) {
        Tema.instalar();
        SwingUtilities.invokeLater(() -> {
            Usuario usuario = LoginVista.mostrarLogin();
            if (usuario == null) {
                System.exit(0);   // cerró el inicio de sesión
            }
            new PrincipalVista(usuario).setVisible(true);
        });
    }
}
