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
 * Pruebas en MySQL real: "mvn test -Dpruebas=mysql" corre las mismas pruebas
 * contra MySQL, en una base aparte (aristools_pruebas) que se crea y se borra
 * sola. Los datos de conexión se leen de db-pruebas-mysql.properties, en la
 * carpeta del proyecto. Sirve para comprobar que el SQL funciona igual en
 * MySQL que en H2. La base de trabajo (aristools) no se toca.
 *
 * Todas las clases DAO piden la conexión con ConexionDB.obtener() y la
 * cierran al terminar usando try-with-resources.
 */
public final class ConexionDB {

    private static final String ARCHIVO = "/db.properties";
    // Solo existe mientras corren las pruebas (src/test/resources)
    private static final String ARCHIVO_PRUEBAS = "/db-pruebas.properties";
    // Pruebas en MySQL real (mvn test -Dpruebas=mysql): archivo en la carpeta del proyecto, fuera de GitHub
    public static final String ARCHIVO_PRUEBAS_MYSQL = "db-pruebas-mysql.properties";

    private static String URL;
    private static String USUARIO;
    private static String CLAVE;
    private static String MYSQL_BIN;   // opcional: carpeta bin de MySQL para los respaldos
    private static boolean MODO_PRUEBAS;   // true mientras corre "mvn test"

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
        MYSQL_BIN = propiedades.getProperty("mysql_bin", "").trim();
    }

    // Si están corriendo las pruebas usa db-pruebas.properties (H2 en memoria).
    // Si no, busca db.properties al lado del .jar, luego en la carpeta actual y por último en el proyecto.
    private static InputStream abrirArchivo() throws IOException {
        InputStream pruebas = ConexionDB.class.getResourceAsStream(ARCHIVO_PRUEBAS);
        if (pruebas != null) {
            MODO_PRUEBAS = true;
            if ("mysql".equalsIgnoreCase(System.getProperty("pruebas", "").trim())) {
                pruebas.close();
                File mysql = new File(ARCHIVO_PRUEBAS_MYSQL);
                if (!mysql.isFile()) {
                    throw new IllegalStateException("Para correr las pruebas en MySQL copie "
                            + ARCHIVO_PRUEBAS_MYSQL + ".ejemplo con el nombre " + ARCHIVO_PRUEBAS_MYSQL
                            + " (en la carpeta del proyecto) y escriba la contraseña de MySQL.");
                }
                return new FileInputStream(mysql);
            }
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

    /**
     * Indica si se están corriendo las pruebas (mvn test): con la base H2 en
     * memoria o con la base MySQL de pruebas, nunca con la base de trabajo.
     */
    public static boolean esBaseDePruebas() {
        return MODO_PRUEBAS || URL.startsWith("jdbc:h2:");
    }

    /** true si la conexión es la base H2 en memoria (la forma normal de las pruebas). */
    public static boolean esH2() {
        return URL.startsWith("jdbc:h2:");
    }

    /**
     * Abre y devuelve una conexión nueva a la base de datos aristools.
     * Quien la recibe debe cerrarla (try-with-resources).
     *
     * @return conexión abierta a MySQL (o a H2 durante las pruebas)
     * @throws SQLException si MySQL no está encendido o los datos son incorrectos
     */
    // Datos que necesita el respaldo de la base (util.RespaldoBD) para llamar a mysqldump.
    public static String getUrl() { return URL; }
    public static String getUsuario() { return USUARIO; }
    public static String getClave() { return CLAVE; }
    /** Carpeta bin de MySQL escrita en db.properties (propiedad mysql_bin); vacío si no se escribió. */
    public static String getMysqlBin() { return MYSQL_BIN; }

    public static Connection obtener() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }
}
