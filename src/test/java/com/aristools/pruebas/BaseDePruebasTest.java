package com.aristools.pruebas;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.aristools.conexion.ConexionDB;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Comprueba que las pruebas no dependen del MySQL local: usan una base H2
 * en memoria creada con los mismos scripts de la carpeta database.
 */
@ExtendWith(BaseDePruebas.class)
class BaseDePruebasTest {

    @Test
    void lasPruebasNuncaUsanLaBaseDeTrabajo() throws Exception {
        assertTrue(ConexionDB.esBaseDePruebas());
        try (Connection con = ConexionDB.obtener()) {
            if (ConexionDB.esH2()) {
                assertEquals("H2", con.getMetaData().getDatabaseProductName(), "Lo normal: H2 en memoria");
            } else {
                // mvn test -Dpruebas=mysql: MySQL real, pero en la base aparte aristools_pruebas
                assertEquals("MySQL", con.getMetaData().getDatabaseProductName());
                assertTrue(con.getCatalog().endsWith("_pruebas"), "Base de pruebas: " + con.getCatalog());
            }
        }
    }

    /** Solo en MySQL real: los disparadores no dejan cambiar ni borrar la bitácora. */
    @Test
    void enMySqlLaBitacoraNoSePuedeModificarNiBorrar() throws Exception {
        assumeFalse(ConexionDB.esH2(), "H2 no ejecuta los disparadores de MySQL");
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement()) {
            st.execute("INSERT INTO bitacora (accion, detalle) VALUES ('PRUEBA', 'Registro de prueba del disparador')");
            assertThrows(SQLException.class, () -> st.execute("UPDATE bitacora SET detalle = 'cambiado'"));
            assertThrows(SQLException.class, () -> st.execute("DELETE FROM bitacora"));
        }
    }

    @Test
    void elScriptRealCreaLasDoceTablas() throws Exception {
        String[] tablas = {"rol", "usuario", "categoria", "producto", "bodega", "proveedor", "existencia",
            "tipo_movimiento", "movimiento_inventario", "alerta", "parametro", "bitacora"};
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement()) {
            for (String tabla : tablas) {
                try (ResultSet rs = st.executeQuery("SELECT COUNT(*) FROM " + tabla)) {
                    assertTrue(rs.next(), "Debe existir la tabla " + tabla);
                }
            }
        }
    }

    @Test
    void elLectorSeparaBienLasSentenciasDeUnScript() {
        String script = "DROP DATABASE IF EXISTS x;\n"
                + "USE x;\n"
                + "-- comentario con ; y con 'comillas'\n"
                + "CREATE TABLE a (id INT) ENGINE = InnoDB;\n"
                + "INSERT INTO a VALUES ('uno; dos'); -- otro comentario\n"
                + "DELIMITER $$\n"
                + "CREATE TRIGGER t BEFORE UPDATE ON a FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000'; END$$\n"
                + "DELIMITER ;\n"
                + "INSERT INTO a VALUES ('tres');\n";
        List<String> sentencias = LectorScripts.sentencias(script);
        assertEquals(List.of("CREATE TABLE a (id INT)", "INSERT INTO a VALUES ('uno; dos')",
                "INSERT INTO a VALUES ('tres')"), sentencias);
        assertEquals(List.of("CREATE TRIGGER t BEFORE UPDATE ON a FOR EACH ROW BEGIN SIGNAL SQLSTATE '45000'; END"),
                LectorScripts.disparadores(script), "El disparador se lee completo, con su ; interno");
    }
}
