package com.aristools.conexion;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

/**
 * Única clase del sistema que sabe cómo conectarse a MySQL (SCE2.10).
 * Los datos de acceso se leen una sola vez del archivo db.properties,
 * así no quedan escritos dentro del código fuente.
 *
 * Dónde se busca db.properties, en este orden:
 *   1. En la misma carpeta del archivo Aristools.jar (programa instalado).
 *   2. En la carpeta desde donde se ejecuta el programa.
 *   3. Dentro del proyecto, en src/main/resources (al trabajar en VS Code o NetBeans).
 * Así la contraseña de MySQL nunca queda empaquetada dentro del .jar.
 *
 * Excepción para las pruebas: al ejecutar "mvn test" existe el archivo
 * db-pruebas.properties (carpeta src/test/resources) y se usa ese, que
 * apunta a una base H2 en memoria. Por eso las pruebas no necesitan MySQL
 * ni contraseña. Ese archivo no va dentro del .jar: el programa instalado
 * siempre usa MySQL.
 *
 * Todas las clases DAO piden la conexión con ConexionDB.obtener() y la
 * cierran al terminar usando try-with-resources.
 */
public final class ConexionDB {

    private static final String ARCHIVO = "/db.properties";
    // Solo existe mientras corren las pruebas (src/test/resources)
    private static final String ARCHIVO_PRUEBAS = "/db-pruebas.properties";

    private static String URL;
    private static String USUARIO;
    private static String CLAVE;

    // Se ejecuta una sola vez, la primera vez que se usa la clase.
    static {
        cargarConfiguracion();
    }

    // Constructor privado: no se crean objetos de esta clase,
    // solo se usan sus métodos estáticos.
    private ConexionDB() {
    }

    /**
     * Lee url, usuario y clave desde db.properties.
     * Si el archivo no existe o le falta un dato, detiene el programa con
     * un mensaje claro en lugar de fallar más adelante sin explicación.
     */
    private static void cargarConfiguracion() {
        Properties propiedades = new Properties();
        try (InputStream entrada = abrirArchivo()) {
            if (entrada == null) {
                throw new IllegalStateException(
                        "No se encontró el archivo db.properties. Si usa el programa instalado, póngalo en la "
                        + "misma carpeta de Aristools.jar; si trabaja en el proyecto, cópielo en src/main/resources "
                        + "desde db.properties.ejemplo. En ambos casos escriba la contraseña de MySQL.");
            }
            propiedades.load(entrada);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo db.properties.", e);
        }
        URL = obligatorio(propiedades, "url");
        USUARIO = obligatorio(propiedades, "usuario");
        CLAVE = propiedades.getProperty("clave", "");
    }

    // Si están corriendo las pruebas usa db-pruebas.properties (H2 en memoria).
    // Si no, busca db.properties al lado del .jar, luego en la carpeta actual y por último en el proyecto.
    private static InputStream abrirArchivo() throws IOException {
        InputStream pruebas = ConexionDB.class.getResourceAsStream(ARCHIVO_PRUEBAS);
        if (pruebas != null) {
            return pruebas;
        }
        File carpetaPrograma = carpetaDelPrograma();
        if (carpetaPrograma != null) {
            File junto = new File(carpetaPrograma, "db.properties");
            if (junto.isFile()) {
                return new FileInputStream(junto);
            }
        }
        File actual = new File("db.properties");
        if (actual.isFile()) {
            return new FileInputStream(actual);
        }
        return ConexionDB.class.getResourceAsStream(ARCHIVO);
    }

    // Carpeta donde está Aristools.jar; null si el programa no se ejecuta desde un .jar.
    private static File carpetaDelPrograma() {
        try {
            File ubicacion = new File(ConexionDB.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            return ubicacion.isFile() ? ubicacion.getParentFile() : null;
        } catch (Exception e) {
            return null;
        }
    }

    // Devuelve el valor de una propiedad o informa cuál falta.
    private static String obligatorio(Properties propiedades, String nombre) {
        String valor = propiedades.getProperty(nombre);
        if (valor == null || valor.isBlank()) {
            throw new IllegalStateException("Falta la propiedad '" + nombre + "' en db.properties.");
        }
        return valor.trim();
    }

    /** Indica si la conexión es la base H2 en memoria de las pruebas (y no MySQL). */
    public static boolean esBaseDePruebas() {
        return URL.startsWith("jdbc:h2:");
    }

    /**
     * Abre y devuelve una conexión nueva a la base de datos aristools.
     * Quien la recibe debe cerrarla (try-with-resources).
     *
     * @return conexión abierta a MySQL (o a H2 durante las pruebas)
     * @throws SQLException si MySQL no está encendido o los datos son incorrectos
     */
    public static Connection obtener() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }
}
