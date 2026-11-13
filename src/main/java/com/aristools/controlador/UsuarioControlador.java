package com.aristools.controlador;

import com.aristools.dao.RolDAO;
import com.aristools.dao.UsuarioDAO;
import com.aristools.modelo.EstadoUsuario;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.PasswordUtil;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.sql.SQLException;
import java.util.List;

/**
 * Gestión de usuarios y roles (RF-002), exclusiva del Administrador.
 * Regla clave: siempre debe quedar al menos un Administrador activo.
 */
public class UsuarioControlador {

    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final RolDAO rolDAO = new RolDAO();

    public List<Usuario> listar() {
        Sesion.exigir(Accion.GESTIONAR_USUARIOS);
        try {
            return usuarioDAO.listar();
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public List<Rol> listarRoles() {
        Sesion.exigir(Accion.GESTIONAR_USUARIOS);
        try {
            return rolDAO.listar();
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Crea (idUsuario = 0) o actualiza un usuario. Al crear, la contraseña es
     * obligatoria y se guarda como hash BCrypt; al actualizar se ignora.
     */
    public void guardar(Usuario u, String clave) {
        Sesion.exigir(Accion.GESTIONAR_USUARIOS);
        Validador.textoObligatorio(u.getNombreCompleto(), "nombre completo");
        Validador.textoObligatorio(u.getDocumento(), "documento");
        Validador.textoObligatorio(u.getNombreUsuario(), "usuario");
        if (!Validador.correoValido(u.getCorreo())) {
            throw new NegocioException("El correo no tiene un formato válido (ejemplo: nombre@dominio.com).");
        }
        if (u.getRol() == null) {
            throw new NegocioException("Seleccione el rol del usuario.");
        }
        try {
            if (u.getIdUsuario() == 0) {
                Validador.claveSegura(clave);
                u.setContrasenaHash(PasswordUtil.encriptar(clave));
                u.setDebeCambiarClave(true);   // clave temporal: el usuario la cambia al ingresar
                u.setIdUsuario(usuarioDAO.insertar(u));
            } else {
                boolean sigueSiendoAdminActivo = u.getRol().esAdministrador() && u.getEstado() == EstadoUsuario.ACTIVO;
                verificarUltimoAdministrador(u.getIdUsuario(), sigueSiendoAdminActivo);
                usuarioDAO.actualizar(u);
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Activar, desactivar o reactivar (desbloquear) una cuenta. */
    public void cambiarEstado(int id, EstadoUsuario e) {
        Sesion.exigir(Accion.GESTIONAR_USUARIOS);
        try {
            verificarUltimoAdministrador(id, e == EstadoUsuario.ACTIVO);
            usuarioDAO.cambiarEstado(id, e);
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
    }

    /** Asigna una contraseña temporal; se guarda solo su hash. */
    public void restablecerContrasena(int id, String clave) {
        Sesion.exigir(Accion.GESTIONAR_USUARIOS);
        Validador.claveSegura(clave);
        try {
            // clave temporal: el usuario deberá cambiarla en su próximo ingreso
            usuarioDAO.actualizarClave(id, PasswordUtil.encriptar(clave), true);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    // Si el usuario es hoy un administrador activo y dejará de serlo, debe existir otro.
    private void verificarUltimoAdministrador(int id, boolean seguiraSiendoAdminActivo) throws SQLException {
        if (seguiraSiendoAdminActivo) {
            return;
        }
        Usuario actual = usuarioDAO.listar().stream().filter(x -> x.getIdUsuario() == id).findFirst().orElse(null);
        boolean esAdminActivo = actual != null && actual.getRol().esAdministrador() && actual.estaActivo();
        if (esAdminActivo && usuarioDAO.contarAdministradoresActivos() <= 1) {
            throw new NegocioException("No se puede desactivar ni cambiar el rol del último Administrador activo.");
        }
    }
}
