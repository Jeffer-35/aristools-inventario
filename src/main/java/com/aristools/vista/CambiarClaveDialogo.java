package com.aristools.vista;

import com.aristools.controlador.LoginControlador;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;

/**
 * Ventana para que el usuario conectado cambie su propia contraseña (RNF-001).
 * Se usa de dos formas:
 *  - obligatoria, al iniciar sesión, si la clave es temporal o venció;
 *  - voluntaria, desde la opción "Cambiar mi contraseña" del menú.
 */
public class CambiarClaveDialogo extends JDialog {

    private final LoginControlador controlador = new LoginControlador();
    private final JPasswordField txtActual = new JPasswordField();
    private final JPasswordField txtNueva = new JPasswordField();
    private final JPasswordField txtConfirmar = new JPasswordField();
    private boolean cambiada = false;

    private CambiarClaveDialogo(Window padre, String motivo, boolean obligatorio) {
        super(padre, "Aristools - Cambiar contraseña", ModalityType.APPLICATION_MODAL);
        setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
        setResizable(false);

        JPanel fondo = new JPanel(new BorderLayout(0, 14));
        fondo.setBackground(Tema.BLANCO);
        fondo.setBorder(BorderFactory.createEmptyBorder(22, 26, 20, 26));

        JPanel arriba = new JPanel(new BorderLayout(0, 8));
        arriba.setOpaque(false);
        arriba.add(Tema.titulo("Cambiar contraseña"), BorderLayout.NORTH);
        if (motivo != null) {
            JLabel aviso = new JLabel("<html><body style='width:330px'>" + motivo + "</body></html>");
            aviso.setFont(Tema.NEGRITA);
            aviso.setForeground(Tema.AZUL_OSCURO);
            aviso.setOpaque(true);
            aviso.setBackground(new java.awt.Color(0xFFF4E0));
            aviso.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 4, 0, 0, Tema.NARANJA),
                    BorderFactory.createEmptyBorder(8, 10, 8, 10)));
            arriba.add(aviso, BorderLayout.CENTER);
        }

        JPanel campos = new JPanel(new GridBagLayout());
        campos.setOpaque(false);
        int fila = agregar(campos, 0, "Contraseña actual", txtActual);
        fila = agregar(campos, fila, "Nueva contraseña", txtNueva);
        fila = agregar(campos, fila, "Confirmar nueva contraseña", txtConfirmar);
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.gridx = 0;
        c.gridwidth = 2;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(8, 0, 0, 0);
        JLabel requisitos = new JLabel("<html><body style='width:330px'>La contraseña debe tener mínimo 8 "
                + "caracteres, con al menos una mayúscula, una minúscula, un número y un símbolo "
                + "(por ejemplo * # @ !), y ser distinta de la actual.</body></html>");
        requisitos.setFont(Tema.NORMAL.deriveFont(12f));
        requisitos.setForeground(Tema.TEXTO_SUAVE);
        campos.add(requisitos, c);
        GridBagConstraints c2 = (GridBagConstraints) c.clone();
        c2.gridy = fila + 1;
        c2.insets = new Insets(6, 0, 0, 0);
        campos.add(MostrarClave.casilla("Mostrar contraseñas", txtActual, txtNueva, txtConfirmar), c2);

        JButton btnCancelar = Tema.botonSecundario(obligatorio ? "Cerrar sesión" : "Cancelar");
        JButton btnGuardar = Tema.botonPrincipal("Guardar contraseña");
        btnCancelar.addActionListener(e -> dispose());
        btnGuardar.addActionListener(e -> guardar());
        getRootPane().setDefaultButton(btnGuardar);
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        botones.setOpaque(false);
        botones.add(btnCancelar);
        botones.add(btnGuardar);

        fondo.add(arriba, BorderLayout.NORTH);
        fondo.add(campos, BorderLayout.CENTER);
        fondo.add(botones, BorderLayout.SOUTH);
        setContentPane(fondo);
        pack();
        setLocationRelativeTo(padre);
    }

    private int agregar(JPanel panel, int fila, String etiqueta, JPasswordField campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 14);
        JLabel l = new JLabel(etiqueta);
        l.setFont(Tema.NEGRITA);
        panel.add(l, c);
        c.gridx = 1;
        c.insets = new Insets(6, 0, 6, 0);
        campo.setFont(Tema.NORMAL);
        campo.setPreferredSize(new Dimension(220, 34));
        panel.add(campo, c);
        return fila + 1;
    }

    private void guardar() {
        try {
            controlador.cambiarMiClave(new String(txtActual.getPassword()),
                    new String(txtNueva.getPassword()), new String(txtConfirmar.getPassword()));
            cambiada = true;
            Tema.info(this, "Contraseña cambiada correctamente.");
            dispose();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
            txtNueva.setText("");
            txtConfirmar.setText("");
            txtNueva.requestFocus();
        }
    }

    /**
     * Muestra la ventana y espera.
     * @param motivo texto que explica por qué debe cambiarla (null si es voluntario)
     * @param obligatorio true al iniciar sesión: cerrar la ventana equivale a no entrar
     * @return true si la contraseña se cambió
     */
    public static boolean mostrar(Component padre, String motivo, boolean obligatorio) {
        Window ventana = padre == null ? null : SwingUtilities.getWindowAncestor(padre);
        if (padre instanceof Window w) {
            ventana = w;
        }
        CambiarClaveDialogo d = new CambiarClaveDialogo(ventana, motivo, obligatorio);
        d.setVisible(true);
        return d.cambiada;
    }
}
