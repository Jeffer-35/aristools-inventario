package com.aristools.controlador;

import com.aristools.conexion.ConexionDB;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.RespaldoBD;
import java.io.File;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

/**
 * Respaldos de la base de datos (RNF-007).
 *
 * Manual: el Administrador crea un respaldo o restaura uno anterior.
 * Automático: al iniciar sesión, si ya pasaron los días configurados desde el
 * último respaldo, el sistema hace uno en segundo plano y conserva los 10
 * automáticos más recientes. Los manuales nunca se borran. Antes de restaurar
 * se guarda un respaldo de seguridad (se conservan los 5 más recientes).
 */
public class RespaldoControlador {

    public static final String PARAMETRO_DIAS = "dias_respaldo_automatico";
    public static final int AUTOMATICOS_A_CONSERVAR = 10;
    public static final int SEGURIDAD_A_CONSERVAR = 5;

    private final ParametroControlador parametros = new ParametroControlador();

    /** Carpeta "respaldos" junto al programa (o junto al proyecto, si se ejecuta desde el editor). */
    public File carpeta() {
        return new File("respaldos").getAbsoluteFile();
    }

    /** Respaldos guardados, del más reciente al más antiguo. */
    public List<File> listar() {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        return RespaldoBD.listar(carpeta());
    }

    /** Crea un respaldo ahora. @return el archivo generado */
    public File respaldarAhora() {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        File archivo = respaldar(RespaldoBD.nombreArchivo(false, LocalDateTime.now()));
        Auditoria.registrar("RESPALDO", "Respaldo manual: " + archivo.getName() + " (" + archivo.length() / 1024 + " KB)");
        return archivo;
    }

    /**
     * Restaura un respaldo: la base queda como estaba cuando se hizo ese
     * archivo. Antes de reemplazarla se guarda un respaldo de seguridad, por
     * si se eligió el archivo equivocado.
     */
    public void restaurar(File archivo) {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        if (archivo == null) {
            throw new NegocioException("Seleccione el respaldo que quiere restaurar.");
        }
        File seguridad = respaldar(RespaldoBD.nombreArchivoSeguridad(LocalDateTime.now()));
        RespaldoBD.Servidor servidor = RespaldoBD.leerUrl(ConexionDB.getUrl());
        RespaldoBD.restaurar(RespaldoBD.buscarHerramienta("mysql", ConexionDB.getMysqlBin()), servidor,
                ConexionDB.getUsuario(), ConexionDB.getClave(), archivo);
        Auditoria.registrar("RESTAURACION", "Restauró " + archivo.getName()
                + ". Respaldo de seguridad previo: " + seguridad.getName());
        for (File viejo : RespaldoBD.seguridadSobrantes(RespaldoBD.listar(carpeta()), SEGURIDAD_A_CONSERVAR)) {
            viejo.delete();
        }
    }

    /** Cada cuántos días se hace el respaldo automático (7 si no está configurado; 0 = desactivado). */
    public int diasRespaldoAutomatico() {
        try {
            return Integer.parseInt(parametros.valor(PARAMETRO_DIAS, "7").trim());
        } catch (NumberFormatException e) {
            return 7;
        }
    }

    public void guardarDiasRespaldoAutomatico(int dias) {
        Sesion.exigir(Accion.CONFIGURAR_PARAMETROS);
        if (dias < 0 || dias > 90) {
            throw new NegocioException("Los días entre respaldos automáticos deben estar entre 0 y 90 (0 = desactivado).");
        }
        int antes = diasRespaldoAutomatico();
        parametros.guardarParametro(PARAMETRO_DIAS, String.valueOf(dias),
                "Cada cuántos días se hace el respaldo automático de la base (0 = desactivado)");
        if (antes != dias) {
            Auditoria.registrar("CAMBIO_PARAMETROS", "días entre respaldos automáticos: " + antes + " → " + dias);
        }
    }

    /** Fecha del respaldo más reciente (manual o automático); null si no hay ninguno. */
    public LocalDateTime ultimoRespaldo() {
        List<File> respaldos = RespaldoBD.listar(carpeta());
        return respaldos.isEmpty() ? null
                : LocalDateTime.ofInstant(Instant.ofEpochMilli(respaldos.get(0).lastModified()), ZoneId.systemDefault());
    }

    /**
     * Respaldo automático. Se llama al abrir la ventana principal, con
     * cualquier rol, y trabaja en un hilo aparte para no frenar al usuario.
     * Si falla no interrumpe el trabajo: el error queda en la consola, igual
     * que hace la bitácora.
     */
    public void respaldoAutomaticoSiToca() {
        if (ConexionDB.esBaseDePruebas()) {
            return;
        }
        Thread hilo = new Thread(() -> {
            try {
                if (!RespaldoBD.tocaRespaldo(ultimoRespaldo(), diasRespaldoAutomatico(), LocalDateTime.now())) {
                    return;
                }
                File archivo = respaldar(RespaldoBD.nombreArchivo(true, LocalDateTime.now()));
                for (File viejo : RespaldoBD.automaticosSobrantes(RespaldoBD.listar(carpeta()), AUTOMATICOS_A_CONSERVAR)) {
                    viejo.delete();
                }
                Auditoria.registrar("RESPALDO", "Respaldo automático: " + archivo.getName());
            } catch (RuntimeException e) {
                System.err.println("No se pudo hacer el respaldo automático: " + e.getMessage());
            }
        }, "respaldo-automatico");
        hilo.setDaemon(true);
        hilo.start();
    }

    // Llama a mysqldump con los datos de db.properties.
    private File respaldar(String nombre) {
        RespaldoBD.Servidor servidor = RespaldoBD.leerUrl(ConexionDB.getUrl());
        File archivo = new File(carpeta(), nombre);
        RespaldoBD.respaldar(RespaldoBD.buscarHerramienta("mysqldump", ConexionDB.getMysqlBin()), servidor,
                ConexionDB.getUsuario(), ConexionDB.getClave(), archivo);
        return archivo;
    }
}
