package com.aristools.util;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Respaldo y restauración de la base de datos (RNF-007).
 *
 * No copia los datos "a mano": llama a las herramientas oficiales de MySQL.
 *   mysqldump  escribe toda la base (tablas, datos y disparadores) en un archivo .sql
 *   mysql      vuelve a cargar ese archivo
 *
 * La contraseña no viaja en la línea de comandos (cualquiera podría verla
 * en el administrador de tareas): se escribe en un archivo temporal de
 * opciones que MySQL lee con --defaults-extra-file y que se borra al terminar.
 * Es la forma que recomienda el manual de MySQL.
 */
public final class RespaldoBD {

    public static final String PREFIJO_MANUAL = "aristools_";
    public static final String PREFIJO_AUTOMATICO = "aristools_auto_";
    public static final String PREFIJO_SEGURIDAD = "aristools_antes_de_restaurar_";
    private static final DateTimeFormatter SELLO = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final Pattern URL_MYSQL = Pattern.compile("jdbc:mysql://([^:/?]+)(?::(\\d+))?/([^?]+).*");
    private static final int MINUTOS_MAXIMO = 5;

    private RespaldoBD() {
    }

    /** Datos de conexión sacados de la URL de db.properties. */
    public record Servidor(String host, String puerto, String base) { }

    /**
     * Separa la URL JDBC en servidor, puerto y nombre de la base.
     * Ejemplo: jdbc:mysql://localhost:3306/aristools?useSSL=false
     * da localhost, 3306 y aristools.
     */
    public static Servidor leerUrl(String url) {
        Matcher m = URL_MYSQL.matcher(url == null ? "" : url);
        if (!m.matches()) {
            throw new NegocioException("El respaldo solo funciona con la base de datos MySQL del sistema.");
        }
        return new Servidor(m.group(1), m.group(2) == null ? "3306" : m.group(2), m.group(3));
    }

    /** Nombre del archivo con fecha y hora: aristools_20261117_153000.sql */
    public static String nombreArchivo(boolean automatico, LocalDateTime cuando) {
        return (automatico ? PREFIJO_AUTOMATICO : PREFIJO_MANUAL) + cuando.format(SELLO) + ".sql";
    }

    /** Nombre del respaldo de seguridad que se guarda justo antes de restaurar. */
    public static String nombreArchivoSeguridad(LocalDateTime cuando) {
        return PREFIJO_SEGURIDAD + cuando.format(SELLO) + ".sql";
    }

    /** Tipo de respaldo según el nombre del archivo: Manual, Automático o Seguridad. */
    public static String tipo(File archivo) {
        String nombre = archivo.getName();
        if (nombre.startsWith(PREFIJO_SEGURIDAD)) {
            return "Seguridad";
        }
        return nombre.startsWith(PREFIJO_AUTOMATICO) ? "Automático" : "Manual";
    }

    /** Comando de mysqldump. --single-transaction saca una copia coherente sin bloquear a los usuarios. */
    public static List<String> comandoRespaldo(File mysqldump, Servidor s, String usuario, File destino) {
        return List.of(mysqldump.getPath(), "--host=" + s.host(), "--port=" + s.puerto(), "--user=" + usuario,
                "--single-transaction", "--routines", "--triggers", "--set-gtid-purged=OFF", "--default-character-set=utf8mb4",
                "--databases", s.base(), "--result-file=" + destino.getPath());
    }

    /** Comando de mysql para restaurar; el archivo .sql se le entrega por la entrada estándar. */
    public static List<String> comandoRestaurar(File mysql, Servidor s, String usuario) {
        return List.of(mysql.getPath(), "--host=" + s.host(), "--port=" + s.puerto(), "--user=" + usuario,
                "--default-character-set=utf8mb4");
    }

    /**
     * ¿Ya toca hacer el respaldo automático?
     *
     * @param ultimo fecha del respaldo más reciente; null si nunca se ha hecho
     * @param cadaDias cada cuántos días se respalda; 0 = desactivado
     */
    public static boolean tocaRespaldo(LocalDateTime ultimo, int cadaDias, LocalDateTime ahora) {
        if (cadaDias <= 0) {
            return false;
        }
        return ultimo == null || !ultimo.plusDays(cadaDias).isAfter(ahora);
    }

    /**
     * Respaldos automáticos que sobran: se conservan los más recientes y se
     * devuelven los demás para borrarlos. Los respaldos manuales nunca se borran.
     */
    public static List<File> automaticosSobrantes(List<File> respaldos, int conservar) {
        return sobrantes(respaldos, PREFIJO_AUTOMATICO, conservar);
    }

    /** Igual que automaticosSobrantes, pero para los respaldos de seguridad (los previos a restaurar). */
    public static List<File> seguridadSobrantes(List<File> respaldos, int conservar) {
        return sobrantes(respaldos, PREFIJO_SEGURIDAD, conservar);
    }

    private static List<File> sobrantes(List<File> respaldos, String prefijo, int conservar) {
        List<File> automaticos = new ArrayList<>();
        for (File f : respaldos) {
            if (f.getName().startsWith(prefijo)) {
                automaticos.add(f);
            }
        }
        automaticos.sort(Comparator.comparing(File::getName).reversed());   // el nombre lleva la fecha
        return automaticos.size() <= conservar ? List.of() : new ArrayList<>(automaticos.subList(conservar, automaticos.size()));
    }

    /** Archivos .sql de la carpeta de respaldos, del más reciente al más antiguo. */
    public static List<File> listar(File carpeta) {
        File[] archivos = carpeta.listFiles((d, n) -> n.startsWith(PREFIJO_MANUAL) && n.endsWith(".sql"));
        List<File> lista = archivos == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(archivos));
        lista.sort(Comparator.comparingLong(File::lastModified).reversed());
        return lista;
    }

    /**
     * Busca mysqldump.exe o mysql.exe: primero en la carpeta escrita en
     * db.properties (mysql_bin), luego en C:\Program Files\MySQL\MySQL Server *
     * y por último confía en que esté en el PATH del sistema.
     *
     * @param nombre "mysqldump" o "mysql"
     */
    public static File buscarHerramienta(String nombre, String carpetaConfigurada) {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        String ejecutable = windows ? nombre + ".exe" : nombre;
        if (carpetaConfigurada != null && !carpetaConfigurada.isBlank()) {
            File f = new File(carpetaConfigurada, ejecutable);
            if (f.isFile()) {
                return f;
            }
            throw new NegocioException("No se encontró " + ejecutable + " en la carpeta " + carpetaConfigurada
                    + " (propiedad mysql_bin de db.properties).");
        }
        for (String base : new String[]{System.getenv("ProgramFiles"), System.getenv("ProgramFiles(x86)")}) {
            File[] versiones = base == null ? null : new File(base, "MySQL").listFiles(
                    (d, n) -> n.startsWith("MySQL Server"));
            if (versiones != null) {
                Arrays.sort(versiones, Comparator.comparing(File::getName).reversed());   // la versión más nueva primero
                for (File v : versiones) {
                    File f = new File(new File(v, "bin"), ejecutable);
                    if (f.isFile()) {
                        return f;
                    }
                }
            }
        }
        String path = System.getenv("PATH");
        for (String carpeta : path == null ? new String[0] : path.split(File.pathSeparator)) {
            File f = new File(carpeta, ejecutable);
            if (f.isFile()) {
                return f;
            }
        }
        throw new NegocioException("No se encontró " + ejecutable + " de MySQL Server en este computador. "
                + "Escriba la carpeta bin de MySQL en db.properties, por ejemplo:\n"
                + "mysql_bin=C:/Program Files/MySQL/MySQL Server 9.1/bin");
    }

    /** Genera el respaldo en el archivo indicado. Si falla, no deja un archivo a medias. */
    public static void respaldar(File mysqldump, Servidor s, String usuario, String clave, File destino) {
        File carpeta = destino.getAbsoluteFile().getParentFile();
        if (!carpeta.isDirectory() && !carpeta.mkdirs()) {
            throw new NegocioException("No se pudo crear la carpeta de respaldos: " + carpeta);
        }
        String salida = ejecutar(comandoRespaldo(mysqldump, s, usuario, destino), clave, null, "el respaldo");
        if (!destino.isFile() || destino.length() == 0) {
            destino.delete();
            throw new NegocioException("El respaldo no se generó. " + salida);
        }
    }

    /** Carga un archivo de respaldo: reemplaza la base actual por la del archivo. */
    public static void restaurar(File mysql, Servidor s, String usuario, String clave, File archivo) {
        if (!archivo.isFile() || archivo.length() == 0) {
            throw new NegocioException("El archivo de respaldo no existe o está vacío.");
        }
        // Los respaldos viejos pueden traer la línea SET @@GLOBAL.GTID_PURGED, que MySQL rechaza al restaurar
        // en el mismo servidor; se carga una copia temporal sin esa línea y el archivo original no se toca.
        File limpio = null;
        try {
            limpio = File.createTempFile("aristools_restaurar", ".sql");
            List<String> lineas = new ArrayList<>();
            for (String linea : Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8)) {
                if (!linea.contains("@@GLOBAL.GTID_PURGED")) {
                    lineas.add(linea);
                }
            }
            Files.write(limpio.toPath(), lineas, StandardCharsets.UTF_8);
            ejecutar(comandoRestaurar(mysql, s, usuario), clave, limpio, "la restauración");
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el archivo de respaldo: " + e.getMessage());
        } finally {
            if (limpio != null) {
                limpio.delete();
            }
        }
    }

    // Lanza el programa, espera a que termine y devuelve lo que escribió. Lanza error si termina mal.
    private static String ejecutar(List<String> comando, String clave, File entrada, String que) {
        File registro = null;
        File opciones = null;
        try {
            registro = File.createTempFile("aristools_respaldo", ".log");
            // Archivo de opciones con la clave; debe ser el primer argumento del comando
            opciones = File.createTempFile("aristools_respaldo", ".cnf");
            String segura = (clave == null ? "" : clave).replace("\\", "\\\\").replace("\"", "\\\"");
            Files.write(opciones.toPath(), ("[client]\npassword=\"" + segura + "\"\n").getBytes(StandardCharsets.UTF_8));
            List<String> completo = new ArrayList<>(comando);
            completo.add(1, "--defaults-extra-file=" + opciones.getPath());
            ProcessBuilder pb = new ProcessBuilder(completo);
            pb.redirectErrorStream(true);
            pb.redirectOutput(registro);
            if (entrada != null) {
                pb.redirectInput(entrada);
            }
            Process proceso = pb.start();
            if (!proceso.waitFor(MINUTOS_MAXIMO, TimeUnit.MINUTES)) {
                proceso.destroyForcibly();
                throw new NegocioException("Se canceló " + que + " porque tardó más de " + MINUTOS_MAXIMO + " minutos.");
            }
            String salida = new String(Files.readAllBytes(registro.toPath()), StandardCharsets.UTF_8).trim();
            if (proceso.exitValue() != 0) {
                throw new NegocioException("No se pudo completar " + que + ". MySQL respondió:\n"
                        + (salida.length() > 400 ? salida.substring(0, 400) + "..." : salida));
            }
            return salida;
        } catch (IOException e) {
            throw new NegocioException("No se pudo ejecutar " + new File(comando.get(0)).getName() + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new NegocioException("Se interrumpió " + que + ".");
        } finally {
            if (registro != null) {
                registro.delete();
            }
            if (opciones != null) {
                opciones.delete();   // no queda ninguna copia de la clave
            }
        }
    }
}
