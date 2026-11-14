package com.aristools.conexion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.pruebas.BaseDePruebas;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Prueba de conexión (SCE2.10): comprueba que ConexionDB entrega una
 * conexión válida a la base aristools y que el script aristools.sql cargó
 * los datos obligatorios. Se ejecuta sobre la base H2 en memoria, por eso
 * no requiere MySQL encendido ni contraseña.
 */
@ExtendWith(BaseDePruebas.class)
class ConexionDBTest {

    @Test
    void conectaConLaBaseAristools() throws Exception {
        try (Connection con = ConexionDB.obtener()) {
            assertTrue(con.isValid(2), "La conexión con la base de datos no es válida");
            assertEquals("aristools", con.getCatalog().toLowerCase(), "La conexión no apunta a la base aristools");
        }
    }

    @Test
    void existenLosCuatroRolesYLosNueveTiposDeMovimiento() throws Exception {
        try (Connection con = ConexionDB.obtener()) {
            assertEquals(4, contar(con, "SELECT COUNT(*) FROM rol"), "Deben existir 4 roles");
            assertEquals(9, contar(con, "SELECT COUNT(*) FROM tipo_movimiento"), "Deben existir 9 tipos de movimiento");
        }
    }

    // Ejecuta una consulta COUNT(*) y devuelve el número.
    private int contar(Connection con, String sql) throws Exception {
        try (PreparedStatement ps = con.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }
}
