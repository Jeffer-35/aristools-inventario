package com.aristools.pruebas;

import com.aristools.conexion.ConexionDB;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
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
 * lenguaje de MySQL y H2 no los ejecuta; se comprueban en MySQL Workbench.
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
        File carpeta = carpetaDatabase();
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement()) {
            for (String nombre : SCRIPTS) {
                String texto = Files.readString(new File(carpeta, nombre).toPath(), StandardCharsets.UTF_8);
                for (String sentencia : LectorScripts.sentencias(texto)) {
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
