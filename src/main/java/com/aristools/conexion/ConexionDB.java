package com.aristools.conexion;

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
 * Todas las clases DAO piden la conexión con ConexionDB.obtener() y la
 * cierran al terminar usando try-with-resources.
 */
public final class ConexionDB {

    private static final String ARCHIVO = "/db.properties";

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
        try (InputStream entrada = ConexionDB.class.getResourceAsStream(ARCHIVO)) {
            if (entrada == null) {
                throw new IllegalStateException(
                        "No se encontró el archivo db.properties en src/main/resources. "
                        + "Cópielo desde db.properties.ejemplo y escriba la contraseña de MySQL.");
            }
            propiedades.load(entrada);
        } catch (IOException e) {
            throw new IllegalStateException("No se pudo leer el archivo db.properties.", e);
        }
        URL = obligatorio(propiedades, "url");
        USUARIO = obligatorio(propiedades, "usuario");
        CLAVE = propiedades.getProperty("clave", "");
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
     * Abre y devuelve una conexión nueva a la base de datos aristools.
     * Quien la recibe debe cerrarla (try-with-resources).
     *
     * @return conexión abierta a MySQL
     * @throws SQLException si MySQL no está encendido o los datos son incorrectos
     */
    public static Connection obtener() throws SQLException {
        return DriverManager.getConnection(URL, USUARIO, CLAVE);
    }
}
