package com.aristools.vista;

import com.aristools.controlador.UsuarioControlador;
import com.aristools.modelo.EstadoUsuario;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import java.util.List;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JOptionPane;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

/**
 * Gestión de usuarios y roles (RF-002). Solo el Administrador la ve.
 * Reglas: usuario, documento y correo únicos; la contraseña se guarda con
 * BCrypt; no se eliminan cuentas (se desactivan) y siempre debe quedar un
 * Administrador activo.
 */
public class UsuariosPanel extends PanelCatalogo<Usuario> {

    private final UsuarioControlador controlador = new UsuarioControlador();
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtDocumento = new JTextField();
    private final JTextField txtCorreo = new JTextField();
    private final JTextField txtUsuario = new JTextField();
    private final JComboBox<Rol> cmbRol = new JComboBox<>();
    private final JPasswordField txtClave = new JPasswordField();
    private final JButton btnRestablecer = Tema.botonSecundario("Restablecer contraseña");

    public UsuariosPanel() {
        super("Usuarios y roles", "Cuentas de acceso al sistema y el rol de cada persona.");
        btnRestablecer.addActionListener(e -> restablecerContrasena());
        panelBotonesExtra.setLayout(new java.awt.GridLayout(1, 1));
        panelBotonesExtra.add(btnRestablecer);
        construir();
    }

    @Override
    protected String[] columnas() {
        return new String[]{"Usuario", "Nombre completo", "Documento", "Correo", "Rol", "Estado"};
    }

    @Override
    protected int[] anchosColumnas() {
        return new int[]{100, 180, 100, 200, 160, 90};
    }

    @Override
    protected Object[] fila(Usuario u) {
        return new Object[]{u.getNombreUsuario(), u.getNombreCompleto(), u.getDocumento(), u.getCorreo(),
            u.getRol(), u.getEstado()};
    }

    /** Los inactivos solo se muestran si se marca "Ver inactivos"; los bloqueados siempre se ven. */
    @Override
    protected List<Usuario> consultar(String filtro, boolean inactivos) {
        return controlador.listar().stream()
                .filter(u -> inactivos || u.getEstado() != EstadoUsuario.INACTIVO)
                .filter(u -> contiene(filtro, u.getNombreUsuario(), u.getNombreCompleto(), u.getDocumento()))
                .toList();
    }

    @Override
    protected void alAbrir() {
        if (cmbRol.getItemCount() == 0) {
            for (Rol r : controlador.listarRoles()) {
                cmbRol.addItem(r);
            }
        }
    }

    @Override
    protected void construirFormulario() {
        agregarCampo("Nombre completo", txtNombre);
        agregarCampo("Documento", txtDocumento);
        agregarCampo("Correo", txtCorreo);
        agregarCampo("Usuario", txtUsuario);
        agregarCampo("Rol", cmbRol);
        javax.swing.JPanel claveConCasilla = new javax.swing.JPanel(new java.awt.BorderLayout(8, 0));
        claveConCasilla.setOpaque(false);
        claveConCasilla.add(txtClave, java.awt.BorderLayout.CENTER);
        claveConCasilla.add(MostrarClave.casilla("Ver", txtClave), java.awt.BorderLayout.EAST);
        agregarCampo("Contraseña temporal", claveConCasilla);
        agregarNota("La contraseña temporal solo se pide al crear la cuenta: mínimo 8 caracteres, con mayúscula, "
                + "minúscula, número y símbolo. El usuario deberá cambiarla en su primer ingreso. "
                + "Aviso de privacidad: los datos personales se tratan conforme a la Ley 1581 de 2012 "
                + "y solo se usan para el control de acceso al sistema.");
    }

    @Override
    protected void mostrar(Usuario u) {
        txtNombre.setText(u.getNombreCompleto());
        txtDocumento.setText(u.getDocumento());
        txtCorreo.setText(u.getCorreo());
        txtUsuario.setText(u.getNombreUsuario());
        for (int i = 0; i < cmbRol.getItemCount(); i++) {
            if (cmbRol.getItemAt(i).getIdRol() == u.getRol().getIdRol()) {
                cmbRol.setSelectedIndex(i);
            }
        }
        txtClave.setText("");
        txtClave.setEnabled(false);   // al editar, la clave se cambia con "Restablecer contraseña"
        btnRestablecer.setEnabled(true);
    }

    @Override
    protected void limpiar() {
        for (JTextField t : new JTextField[]{txtNombre, txtDocumento, txtCorreo, txtUsuario}) {
            t.setText("");
        }
        txtClave.setText("");
        txtClave.setEnabled(true);
        cmbRol.setSelectedIndex(-1);
        btnRestablecer.setEnabled(false);
    }

    @Override
    protected void guardar() {
        Usuario u = new Usuario(seleccionado == null ? 0 : seleccionado.getIdUsuario(),
                txtNombre.getText().trim(), txtDocumento.getText().trim(), txtCorreo.getText().trim(),
                txtUsuario.getText().trim(), null, (Rol) cmbRol.getSelectedItem(),
                seleccionado == null ? EstadoUsuario.ACTIVO : seleccionado.getEstado(),
                seleccionado == null ? 0 : seleccionado.getIntentosFallidos(), null);
        controlador.guardar(u, seleccionado == null ? new String(txtClave.getPassword()) : null);
    }

    /** Activo -> Desactivar; Inactivo -> Activar; Bloqueado -> Reactivar (reinicia los intentos). */
    @Override
    protected void cambiarEstado(Usuario u) {
        EstadoUsuario nuevo = u.getEstado() == EstadoUsuario.ACTIVO ? EstadoUsuario.INACTIVO : EstadoUsuario.ACTIVO;
        controlador.cambiarEstado(u.getIdUsuario(), nuevo);
    }

    @Override
    protected boolean estaActivo(Usuario u) {
        return u.getEstado() == EstadoUsuario.ACTIVO;
    }

    @Override
    protected String textoBotonEstado(Usuario u) {
        return switch (u.getEstado()) {
            case ACTIVO -> "Desactivar";
            case INACTIVO -> "Activar";
            case BLOQUEADO -> "Reactivar (desbloquear)";
        };
    }

    private void restablecerContrasena() {
        if (seleccionado == null) {
            return;
        }
        JPasswordField campo = new JPasswordField();
        int opcion = JOptionPane.showConfirmDialog(this, new Object[]{
            "<html>Nueva contraseña temporal para <b>" + seleccionado.getNombreUsuario() + "</b>.<br>"
                + "Mínimo 8 caracteres, con mayúscula, minúscula, número y símbolo.<br>"
                + "El usuario deberá cambiarla en su próximo ingreso.</html>", campo,
            MostrarClave.casilla("Mostrar contraseña", campo)},
            "Restablecer contraseña", JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
        if (opcion == JOptionPane.OK_OPTION) {
            ejecutar(() -> {
                controlador.restablecerContrasena(seleccionado.getIdUsuario(), new String(campo.getPassword()));
                Tema.info(this, "Contraseña restablecida. Entréguela al usuario para que inicie sesión.");
            });
        }
    }
}
