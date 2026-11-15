package com.aristools.controlador;

import com.aristools.conexion.ConexionDB;
import com.aristools.dao.ParametroDAO;
import com.aristools.modelo.Parametro;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.Cambios;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Parámetros globales y umbrales por defecto (RF-015). Cambiarlos no altera
 * los umbrales propios de los productos existentes.
 */
public class ParametroControlador {

    /** Clave del parámetro con el nombre y cargo de quien revisa los reportes impresos ("Revisó"). */
    public static final String RESPONSABLE_REVISION = "responsable_revision";

    private final ParametroDAO parametroDAO = new ParametroDAO();

    public List<Parametro> listar() {
        try {
            return parametroDAO.listar();
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Valor actual de un parámetro.
     * @param porDefecto lo que se devuelve si la base todavía no tiene ese parámetro
     */
    public String valor(String clave, String porDefecto) {
        for (Parametro p : listar()) {
            if (p.getClave().equals(clave)) {
                return p.getValor();
            }
        }
        return porDefecto;
    }

    /** Guarda los tres parámetros juntos en una transacción. */
    public void guardar(String nombre, int minimo, int maximo) {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        Validador.textoObligatorio(nombre, "nombre del establecimiento");
        if (minimo < 0 || maximo < 0) {
            throw new NegocioException("Los umbrales no pueden ser negativos.");
        }
        if (minimo > maximo) {
            throw new NegocioException("El umbral mínimo no puede ser mayor que el máximo.");
        }
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                parametroDAO.actualizar(con, "nombre_establecimiento", nombre.trim());
                parametroDAO.actualizar(con, "stock_minimo_defecto", String.valueOf(minimo));
                parametroDAO.actualizar(con, "stock_maximo_defecto", String.valueOf(maximo));
                con.commit();
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /**
     * Guarda los parámetros generales, los de seguridad y el responsable de
     * revisión de los reportes en una transacción.
     * @param diasVigencia días de vigencia de la contraseña (0 = no vence)
     * @param minutosInactividad minutos sin actividad para cerrar la sesión (0 = desactivado)
     * @param responsableRevision nombre y cargo que sale como "Revisó" en los reportes PDF
     * @param diasEntregaDefecto días de entrega de los proveedores que no tienen los suyos
     */
    public void guardar(String nombre, int minimo, int maximo, int diasVigencia, int minutosInactividad,
                        String responsableRevision, int diasEntregaDefecto) {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        Validador.textoObligatorio(nombre, "nombre del establecimiento");
        if (minimo < 0 || maximo < 0) {
            throw new NegocioException("Los umbrales no pueden ser negativos.");
        }
        if (minimo > maximo) {
            throw new NegocioException("El umbral mínimo no puede ser mayor que el máximo.");
        }
        if (diasVigencia < 0 || diasVigencia > 365) {
            throw new NegocioException("La vigencia de la contraseña debe estar entre 0 y 365 días (0 = no vence).");
        }
        if (minutosInactividad < 0 || minutosInactividad > 240) {
            throw new NegocioException("El cierre por inactividad debe estar entre 0 y 240 minutos (0 = desactivado).");
        }
        Validador.textoObligatorio(responsableRevision, "responsable de revisión");
        if (diasEntregaDefecto < 0 || diasEntregaDefecto > 180) {
            throw new NegocioException("Los días de entrega por defecto deben estar entre 0 y 180.");
        }
        if (responsableRevision.trim().length() > 100) {
            throw new NegocioException("El responsable de revisión no puede tener más de 100 caracteres.");
        }
        Map<String, String> antes = listar().stream()
                .collect(Collectors.toMap(Parametro::getClave, Parametro::getValor, (a, b) -> a));
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                parametroDAO.actualizar(con, "nombre_establecimiento", nombre.trim());
                parametroDAO.actualizar(con, "stock_minimo_defecto", String.valueOf(minimo));
                parametroDAO.actualizar(con, "stock_maximo_defecto", String.valueOf(maximo));
                parametroDAO.guardar(con, "dias_vigencia_clave", String.valueOf(diasVigencia),
                        "Días de vigencia de la contraseña (0 = sin vencimiento, recomendación NIST SP 800-63B)");
                parametroDAO.guardar(con, "minutos_inactividad", String.valueOf(minutosInactividad),
                        "Minutos sin actividad para cerrar la sesión (0 = desactivado)");
                parametroDAO.guardar(con, RESPONSABLE_REVISION, responsableRevision.trim(),
                        "Nombre y cargo de quien revisa los reportes impresos (sale como Revisó en los PDF)");
                parametroDAO.guardar(con, "dias_entrega_defecto", String.valueOf(diasEntregaDefecto),
                        "Días de entrega para los proveedores que no tienen los suyos (sugerencia de compra)");
                con.commit();
                Cambios c = new Cambios()
                        .campo("nombre del establecimiento", antes.get("nombre_establecimiento"), nombre.trim())
                        .campo("stock mínimo por defecto", antes.get("stock_minimo_defecto"), minimo)
                        .campo("stock máximo por defecto", antes.get("stock_maximo_defecto"), maximo)
                        .campo("vigencia de la contraseña (días)", antes.get("dias_vigencia_clave"), diasVigencia)
                        .campo("cierre por inactividad (minutos)", antes.get("minutos_inactividad"), minutosInactividad)
                        .campo("responsable de revisión", antes.get(RESPONSABLE_REVISION), responsableRevision.trim())
                        .campo("días de entrega por defecto", antes.get("dias_entrega_defecto"), diasEntregaDefecto);
                if (c.hayCambios()) {
                    Auditoria.registrar("CAMBIO_PARAMETROS", c.toString());
                }
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }
}
