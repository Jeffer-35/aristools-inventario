package com.aristools.pruebas;

import com.aristools.conexion.ConexionDB;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Prepara la base de datos de las pruebas (H2 en memoria).
 *
 * Las clases de prueba que usan la base llevan la anotación
 * {@code @ExtendWith(BaseDePruebas.class)}. Antes de la primera prueba se
 * ejecutan los scripts reales del proyecto, en el mismo orden que en MySQL:
 *   1. database/aristools.sql        (tablas y datos obligatorios)
 *   2. database/datos_iniciales.sql  (usuarios y catálogos de prueba)
 *
 * La base se crea una sola vez por ejecución de "mvn test" y desaparece al
 * terminar; así las pruebas siempre parten de los mismos datos y pasan en
 * cualquier computador, sin MySQL y sin contraseñas.
 *
 * Nota: los dos disparadores que protegen la bitácora están escritos en el
 * lenguaje de MySQL y H2 no los ejecuta.
 *
 * Con "mvn test -Dpruebas=mysql" las mismas pruebas corren en MySQL real: se
 * borra y se vuelve a crear la base aristools_pruebas (nunca la base de
 * trabajo aristools), se ejecutan los mismos scripts y también se crean los
 * disparadores. Así se comprueba que el SQL funciona igual en MySQL.
 */
public class BaseDePruebas implements BeforeAllCallback {

    private static final String[] SCRIPTS = {"aristools.sql", "datos_iniciales.sql"};
    private static boolean lista = false;

    @Override
    public void beforeAll(ExtensionContext contexto) throws Exception {
        preparar();
    }

    /** Crea las tablas y carga los datos, solo la primera vez que se llama. */
    public static synchronized void preparar() throws Exception {
        if (lista) {
            return;
        }
        if (!ConexionDB.esBaseDePruebas()) {
            throw new IllegalStateException("Las pruebas deben usar la base H2 en memoria (db-pruebas.properties), "
                    + "no la base MySQL real.");
        }
        boolean mysql = !ConexionDB.esH2();
        if (mysql) {
            crearBaseMySql();
        }
        File carpeta = carpetaDatabase();
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement()) {
            for (String nombre : SCRIPTS) {
                String texto = Files.readString(new File(carpeta, nombre).toPath(), StandardCharsets.UTF_8);
                List<String> sentencias = new ArrayList<>(LectorScripts.sentencias(texto));
                if (mysql) {
                    sentencias.addAll(LectorScripts.disparadores(texto));   // MySQL sí entiende los disparadores
                }
                for (String sentencia : sentencias) {
                    try {
                        st.execute(sentencia);
                    } catch (SQLException e) {
                        throw new IllegalStateException("Error en " + nombre + " al ejecutar:\n" + sentencia, e);
                    }
                }
            }
        }
        lista = true;
    }

    /**
     * Borra y vuelve a crear la base MySQL de pruebas. Por seguridad solo lo
     * hace si su nombre termina en "_pruebas": así nunca toca la base de trabajo.
     */
    private static void crearBaseMySql() throws SQLException {
        String url = ConexionDB.getUrl();
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("(jdbc:mysql://[^/]+/)([^?]+)(\\?.*)?").matcher(url);
        if (!m.matches()) {
            throw new IllegalStateException("La url de " + ConexionDB.ARCHIVO_PRUEBAS_MYSQL + " debe ser de MySQL, "
                    + "por ejemplo jdbc:mysql://localhost:3306/aristools_pruebas");
        }
        String base = m.group(2);
        if (!base.endsWith("_pruebas")) {
            throw new IllegalStateException("Por seguridad, la base de las pruebas debe terminar en _pruebas "
                    + "(por ejemplo aristools_pruebas). Se escribió: " + base + ". La base de trabajo nunca se usa.");
        }
        String servidor = m.group(1) + (m.group(3) == null ? "" : m.group(3));
        try (Connection con = DriverManager.getConnection(servidor, ConexionDB.getUsuario(), ConexionDB.getClave());
             Statement st = con.createStatement()) {
            st.execute("DROP DATABASE IF EXISTS " + base);
            st.execute("CREATE DATABASE " + base + " CHARACTER SET utf8mb4 COLLATE utf8mb4_spanish_ci");
        }
    }

    // Busca la carpeta database del proyecto subiendo desde la carpeta donde se ejecutan las pruebas.
    private static File carpetaDatabase() {
        File carpeta = new File("").getAbsoluteFile();
        while (carpeta != null) {
            File database = new File(carpeta, "database");
            if (new File(database, SCRIPTS[0]).isFile()) {
                return database;
            }
            carpeta = carpeta.getParentFile();
        }
        throw new IllegalStateException("No se encontró la carpeta database con el archivo " + SCRIPTS[0] + ".");
    }
}
