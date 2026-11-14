package com.aristools.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Ajuste;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.Usuario;
import com.aristools.pruebas.BaseDePruebas;
import java.sql.Connection;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pruebas de integración de la capa DAO (SCE2.10).
 * Usan la base H2 en memoria que prepara BaseDePruebas con los scripts
 * aristools.sql y datos_iniciales.sql; no necesitan MySQL.
 */
@ExtendWith(BaseDePruebas.class)
class DaoLecturaTest {

    @Test
    void losCuatroRolesYElUsuarioAdmin() throws Exception {
        assertEquals(4, new RolDAO().listar().size());
        Usuario admin = new UsuarioDAO().buscarPorNombreUsuario("admin");
        assertNotNull(admin, "Debe existir el usuario admin (datos_iniciales.sql)");
        assertTrue(admin.getRol().esAdministrador());
        assertTrue(admin.getContrasenaHash().startsWith("$2a$"), "La contraseña debe estar como hash BCrypt");
        assertTrue(new UsuarioDAO().contarAdministradoresActivos() >= 1);
    }

    @Test
    void catalogosYTiposDeMovimiento() throws Exception {
        Producto martillo = new ProductoDAO().buscarPorCodigo("HER-001");
        assertNotNull(martillo);
        assertNotNull(martillo.getCategoria().getNombre(), "El producto debe traer su categoría");
        assertFalse(new BodegaDAO().listar(false).isEmpty());
        TipoMovimiento ajuste = new TipoMovimientoDAO().buscarPorNombre("Ajuste positivo");
        assertEquals(Naturaleza.ENTRADA, ajuste.getNaturaleza());
        assertEquals(3, new TipoMovimientoDAO().listarPorNaturaleza(Naturaleza.ENTRADA).size(),
                "Compra, Devolución y Ajuste positivo");
    }

    @Test
    void parametrosNumericos() throws Exception {
        try (Connection con = ConexionDB.obtener()) {
            ParametroDAO dao = new ParametroDAO();
            int min = dao.obtenerEntero(con, "stock_minimo_defecto");
            int max = dao.obtenerEntero(con, "stock_maximo_defecto");
            assertTrue(min >= 0 && min <= max);
        }
    }

    @Test
    void elDaoCreaLaSubclaseCorrectaDeMovimiento() throws Exception {
        boolean hayAjuste = new ReporteDAO().movimientos(new FiltroMovimiento())
                .stream().anyMatch(m -> m instanceof Ajuste);
        assertTrue(hayAjuste, "El inventario inicial se registró como Ajuste positivo");
    }

    @Test
    void elSaldoFinalDelKardexCoincideConLasExistencias() throws Exception {
        Producto martillo = new ProductoDAO().buscarPorCodigo("HER-001");
        List<LineaKardex> kardex = new ReporteDAO().kardex(martillo.getIdProducto(), null, null, null);
        assertFalse(kardex.isEmpty());
        int saldoFinal = kardex.get(kardex.size() - 1).getSaldo();
        try (Connection con = ConexionDB.obtener()) {
            assertEquals(new ExistenciaDAO().stockTotal(con, martillo.getIdProducto()), saldoFinal,
                    "El stock solo cambia por movimientos: el kardex debe cuadrar con las existencias");
        }
    }
}
