package com.aristools.controlador;

import com.aristools.conexion.ConexionDB;
import com.aristools.dao.ParametroDAO;
import com.aristools.dao.RolDAO;
import com.aristools.dao.UsuarioDAO;
import com.aristools.modelo.EstadoUsuario;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.PasswordUtil;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDateTime;

/**
 * Inicio y cierre de sesión (RF-001) y creación del primer administrador.
 * Mensajes según la ficha RF-001: ante credenciales inválidas no se indica
 * cuál dato falló, y al tercer intento fallido la cuenta se bloquea.
 */
public class LoginControlador {

    private static final String SIN_CONEXION =
            "Sin conexión a la base de datos. Verifique que MySQL esté encendido e intente de nuevo.";
    private final UsuarioDAO usuarioDAO = new UsuarioDAO();
    private final ParametroDAO parametroDAO = new ParametroDAO();
    private final RolDAO rolDAO = new RolDAO();

    /**
     * Valida las credenciales y, si son correctas, inicia la sesión.
     * @return el usuario autenticado (con su rol, para armar el menú)
     */
    public Usuario iniciarSesion(String usuario, String clave) {
        if (usuario == null || usuario.isBlank() || clave == null || clave.isEmpty()) {
            throw new NegocioException("Ingrese su usuario y su contraseña.");
        }
        try {
            Usuario u = usuarioDAO.buscarPorNombreUsuario(usuario.trim());
            if (u == null) {
                throw new NegocioException("Usuario o contraseña incorrectos.");
            }
            if (u.estaBloqueado()) {
                throw new NegocioException("La cuenta está bloqueada. Solicite al Administrador que la reactive.");
            }
            if (!u.estaActivo()) {
                throw new NegocioException("El usuario está inactivo y no tiene acceso al sistema.");
            }
            if (!PasswordUtil.verificar(clave, u.getContrasenaHash())) {
                u.registrarIntentoFallido();
                usuarioDAO.actualizarIntentos(u);
                if (u.estaBloqueado()) {
                    throw new NegocioException("Tercer intento fallido: la cuenta quedó bloqueada. "
                            + "Solicite al Administrador que la reactive.");
                }
                throw new NegocioException("Usuario o contraseña incorrectos.");
            }
            usuarioDAO.registrarAcceso(u.getIdUsuario());
            u.reiniciarIntentos();
            Sesion.iniciar(u);
            return u;
        } catch (SQLException e) {
            throw new NegocioException(SIN_CONEXION);
        }
    }

    /** Cierra la sesión y descarta los datos del usuario. */
    public void cerrarSesion() {
        Sesion.cerrar();
    }

    /** @return true si ya hay al menos un Administrador del Sistema activo */
    public boolean existeAdministrador() {
        try {
            return usuarioDAO.contarAdministradoresActivos() > 0;
        } catch (SQLException e) {
            throw new NegocioException(SIN_CONEXION);
        }
    }

    /**
     * Crea el primer Administrador del Sistema cuando la base no tiene ninguno.
     * Así no hay contraseñas de administrador escritas en el código fuente.
     */
    public void crearPrimerAdministrador(Usuario u, String clave) {
        if (existeAdministrador()) {
            throw new NegocioException("Ya existe un Administrador activo; inicie sesión con esa cuenta.");
        }
        Validador.textoObligatorio(u.getNombreCompleto(), "nombre completo");
        Validador.textoObligatorio(u.getDocumento(), "documento");
        Validador.textoObligatorio(u.getNombreUsuario(), "usuario");
        if (!Validador.correoValido(u.getCorreo())) {
            throw new NegocioException("El correo no tiene un formato válido (ejemplo: nombre@dominio.com).");
        }
        Validador.claveSegura(clave);
        try {
            Rol admin = rolDAO.listar().stream().filter(Rol::esAdministrador).findFirst()
                    .orElseThrow(() -> new NegocioException("No existe el rol Administrador del Sistema en la base."));
            u.setRol(admin);
            u.setEstado(EstadoUsuario.ACTIVO);
            u.setContrasenaHash(PasswordUtil.encriptar(clave));
            u.setDebeCambiarClave(false);   // la eligió el mismo administrador
            u.setIdUsuario(usuarioDAO.insertar(u));
        } catch (SQLException e) {
            throw new NegocioException("No se pudo crear el administrador: el usuario, el documento o el correo ya existen.");
        }
    }

    /**
     * Indica si el usuario debe cambiar su contraseña antes de entrar:
     * cuando es temporal (asignada por el Administrador) o cuando venció
     * según el parámetro dias_vigencia_clave (0 = no vence).
     * @return el motivo para mostrarlo, o null si no debe cambiarla
     */
    public String motivoCambioClave(Usuario u) {
        if (u.isDebeCambiarClave()) {
            return "Su contraseña es temporal. Cree una contraseña propia para continuar.";
        }
        int dias = leerParametro("dias_vigencia_clave", 0);
        if (u.claveVencida(dias, LocalDateTime.now())) {
            return "Su contraseña venció (vigencia de " + dias + " días). Cree una nueva para continuar.";
        }
        return null;
    }

    /** @return minutos sin actividad para cerrar la sesión (0 = desactivado) */
    public int minutosInactividad() {
        return leerParametro("minutos_inactividad", 0);
    }

    /**
     * El usuario conectado cambia su propia contraseña: debe conocer la actual,
     * la nueva debe ser segura, distinta de la actual y escrita dos veces igual.
     */
    public void cambiarMiClave(String actual, String nueva, String confirmacion) {
        Usuario u = Sesion.getUsuario();
        if (u == null) {
            throw new NegocioException("No hay una sesión iniciada.");
        }
        if (actual == null || !PasswordUtil.verificar(actual, u.getContrasenaHash())) {
            throw new NegocioException("La contraseña actual no es correcta.");
        }
        if (nueva == null || !nueva.equals(confirmacion)) {
            throw new NegocioException("La nueva contraseña y su confirmación no coinciden.");
        }
        if (PasswordUtil.verificar(nueva, u.getContrasenaHash())) {
            throw new NegocioException("La nueva contraseña debe ser distinta de la actual.");
        }
        Validador.claveSegura(nueva);
        try {
            String hash = PasswordUtil.encriptar(nueva);
            usuarioDAO.actualizarClave(u.getIdUsuario(), hash, false);
            u.setContrasenaHash(hash);
            u.setDebeCambiarClave(false);
            u.setFechaCambioClave(LocalDateTime.now());
        } catch (SQLException e) {
            throw new NegocioException(SIN_CONEXION);
        }
    }

    // Lee un parámetro numérico; si no existe (base sin actualizar) usa el valor por defecto.
    private int leerParametro(String clave, int porDefecto) {
        try (Connection con = ConexionDB.obtener()) {
            return parametroDAO.obtenerEntero(con, clave);
        } catch (SQLException | NegocioException e) {
            return porDefecto;
        }
    }
}
