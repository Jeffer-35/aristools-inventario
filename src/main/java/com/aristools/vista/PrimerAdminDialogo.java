package com.aristools.vista;

import com.aristools.controlador.LoginControlador;
import com.aristools.modelo.EstadoUsuario;
import com.aristools.modelo.Usuario;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.util.Arrays;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Se muestra solo la primera vez, cuando la base no tiene ningún
 * Administrador del Sistema activo. Así no hay contraseñas de administrador
 * escritas en el código fuente.
 */
public class PrimerAdminDialogo extends JDialog {

    private final JTextField txtNombre = new JTextField(22);
    private final JTextField txtDocumento = new JTextField(22);
    private final JTextField txtCorreo = new JTextField(22);
    private final JTextField txtUsuario = new JTextField(22);
    private final JPasswordField txtClave = new JPasswordField(22);
    private final JPasswordField txtConfirmar = new JPasswordField(22);
    private boolean creado;

    private PrimerAdminDialogo() {
        super((java.awt.Frame) null, "Aristools - Crear el primer administrador", true);
        setDefaultCloseOperation(DISPOSE_ON_CLOSE);
        JPanel fondo = new JPanel(new BorderLayout(0, 14));
        fondo.setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        fondo.add(Tema.subtitulo("No hay administradores registrados. Cree la primera cuenta de Administrador del Sistema."),
                BorderLayout.NORTH);

        JPanel campos = new JPanel(new GridLayout(0, 2, 10, 8));
        agregar(campos, "Nombre completo", txtNombre);
        agregar(campos, "Documento", txtDocumento);
        agregar(campos, "Correo", txtCorreo);
        agregar(campos, "Usuario", txtUsuario);
        agregar(campos, "Contraseña (mínimo 8)", txtClave);
        agregar(campos, "Confirmar contraseña", txtConfirmar);
        fondo.add(campos, BorderLayout.CENTER);

        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
        JButton btnCancelar = Tema.botonSecundario("Cancelar");
        JButton btnCrear = Tema.botonPrincipal("Crear administrador");
        btnCancelar.addActionListener(e -> dispose());
        btnCrear.addActionListener(e -> crear());
        botones.add(btnCancelar);
        botones.add(btnCrear);
        fondo.add(botones, BorderLayout.SOUTH);

        setContentPane(fondo);
        pack();
        setLocationRelativeTo(null);
    }

    private void agregar(JPanel panel, String etiqueta, JTextField campo) {
        JLabel l = new JLabel(etiqueta);
        l.setFont(Tema.NEGRITA);
        campo.setFont(Tema.NORMAL);
        panel.add(l);
        panel.add(campo);
    }

    private void crear() {
        char[] clave = txtClave.getPassword();
        char[] confirmar = txtConfirmar.getPassword();
        if (!Arrays.equals(clave, confirmar)) {
            Tema.error(this, "Las contraseñas no coinciden.");
            return;
        }
        Usuario u = new Usuario(0, txtNombre.getText().trim(), txtDocumento.getText().trim(),
                txtCorreo.getText().trim(), txtUsuario.getText().trim(), null, null,
                EstadoUsuario.ACTIVO, 0, null);
        try {
            new LoginControlador().crearPrimerAdministrador(u, new String(clave));
            Tema.info(this, "Administrador creado. Ahora inicie sesión con esa cuenta.");
            creado = true;
            dispose();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    /** @return true si se creó el administrador */
    public static boolean mostrar() {
        PrimerAdminDialogo d = new PrimerAdminDialogo();
        d.setVisible(true);
        return d.creado;
    }
}
