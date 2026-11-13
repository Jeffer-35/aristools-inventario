package com.aristools.vista;

import com.aristools.controlador.LoginControlador;
import com.aristools.modelo.Usuario;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;
import javax.swing.SwingConstants;

/**
 * Ventana de inicio de sesión (RF-001). Es un diálogo modal: el programa
 * espera aquí hasta que el usuario ingresa correctamente o cierra la ventana.
 * La lógica (validar, bloquear al tercer intento) está en LoginControlador;
 * esta clase solo muestra los datos y los mensajes.
 */
public class LoginVista extends JDialog {

    private final LoginControlador controlador = new LoginControlador();
    private final JTextField txtUsuario = new JTextField(18);
    private final JPasswordField txtClave = new JPasswordField(18);
    private final JLabel lblMensaje = new JLabel(" ");
    private Usuario usuarioAutenticado;

    public LoginVista() {
        super((java.awt.Frame) null, "Aristools - Iniciar sesión", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        setResizable(false);
        construir();
        pack();
        setLocationRelativeTo(null);
    }

    private void construir() {
        JPanel fondo = new JPanel(new BorderLayout());

        // Franja superior con el nombre del sistema
        JPanel cabecera = new JPanel(new BorderLayout());
        cabecera.setBackground(Tema.AZUL_OSCURO);
        cabecera.setBorder(BorderFactory.createEmptyBorder(22, 30, 22, 30));
        JLabel nombre = new JLabel("ARISTOOLS", SwingConstants.CENTER);
        nombre.setFont(Tema.TITULO.deriveFont(26f));
        nombre.setForeground(Tema.NARANJA);
        JLabel lema = new JLabel("Sistema de gestión de inventarios", SwingConstants.CENTER);
        lema.setFont(Tema.SUBTITULO);
        lema.setForeground(Tema.BLANCO);
        cabecera.add(nombre, BorderLayout.CENTER);
        cabecera.add(lema, BorderLayout.SOUTH);

        // Formulario
        JPanel formulario = new JPanel(new GridBagLayout());
        formulario.setBackground(Tema.BLANCO);
        formulario.setBorder(BorderFactory.createEmptyBorder(24, 34, 24, 34));
        GridBagConstraints c = new GridBagConstraints();
        c.gridx = 0;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(4, 0, 4, 0);

        JLabel lblUsuario = new JLabel("Usuario");
        lblUsuario.setFont(Tema.NEGRITA);
        JLabel lblClave = new JLabel("Contraseña");
        lblClave.setFont(Tema.NEGRITA);
        txtUsuario.setFont(Tema.NORMAL);
        txtClave.setFont(Tema.NORMAL);
        txtUsuario.setPreferredSize(new Dimension(260, 34));
        txtClave.setPreferredSize(new Dimension(260, 34));
        lblMensaje.setForeground(Tema.ROJO);
        lblMensaje.setFont(Tema.NORMAL);

        JButton btnIngresar = Tema.botonPrincipal("Ingresar");
        btnIngresar.setPreferredSize(new Dimension(260, 38));
        btnIngresar.addActionListener(e -> ingresar());
        getRootPane().setDefaultButton(btnIngresar);   // Enter = Ingresar

        c.gridy = 0; formulario.add(lblUsuario, c);
        c.gridy = 1; formulario.add(txtUsuario, c);
        c.gridy = 2; c.insets = new Insets(12, 0, 4, 0); formulario.add(lblClave, c);
        c.gridy = 3; c.insets = new Insets(4, 0, 4, 0); formulario.add(txtClave, c);
        c.gridy = 4; formulario.add(lblMensaje, c);
        c.gridy = 5; c.insets = new Insets(8, 0, 0, 0); formulario.add(btnIngresar, c);

        fondo.add(cabecera, BorderLayout.NORTH);
        fondo.add(formulario, BorderLayout.CENTER);
        setContentPane(fondo);
    }

    /** Envía los datos al controlador y muestra el mensaje si algo falla. */
    private void ingresar() {
        try {
            Usuario u = controlador.iniciarSesion(txtUsuario.getText(),
                    new String(txtClave.getPassword()));
            String motivo = controlador.motivoCambioClave(u);   // clave temporal o vencida
            if (motivo != null && !CambiarClaveDialogo.mostrar(this, motivo, true)) {
                controlador.cerrarSesion();
                lblMensaje.setText("Debe cambiar su contraseña para ingresar al sistema.");
                txtClave.setText("");
                pack();
                return;
            }
            usuarioAutenticado = u;
            dispose();
        } catch (NegocioException ex) {
            lblMensaje.setText(ex.getMessage());
            txtClave.setText("");
            txtClave.requestFocus();
            pack();
        }
    }

    /**
     * Muestra el inicio de sesión y espera. Si la base no tiene administradores,
     * primero pide crear el primero (PrimerAdminDialogo).
     * @return el usuario autenticado, o null si cerró la ventana
     */
    public static Usuario mostrarLogin() {
        LoginControlador control = new LoginControlador();
        try {
            if (!control.existeAdministrador() && !PrimerAdminDialogo.mostrar()) {
                return null;
            }
        } catch (NegocioException ex) {
            Tema.error(null, ex.getMessage());
            return null;
        } catch (ExceptionInInitializerError ex) {
            // ConexionDB no pudo leer db.properties: se muestra su mensaje explicativo
            Throwable causa = ex.getCause() != null ? ex.getCause() : ex;
            Tema.error(null, causa.getMessage());
            return null;
        }
        LoginVista vista = new LoginVista();
        vista.setVisible(true);   // modal: se queda aquí hasta que se cierre
        return vista.usuarioAutenticado;
    }
}
