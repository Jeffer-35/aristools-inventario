package com.aristools.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import com.aristools.conexion.ConexionDB;
import com.aristools.pruebas.BaseDePruebas;
import com.aristools.util.NegocioException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Pruebas unitarias y de integración de los procedimientos almacenados
 * (taller de Bases de Datos 2, semanas 13 y 14).
 *
 * Los procedimientos están escritos en el lenguaje de MySQL y H2 no los
 * ejecuta; por eso estas pruebas solo corren con MySQL real:
 *
 *     mvn test "-Dpruebas=mysql"
 *
 * Con H2 (mvn clean package) salen como omitidas (Skipped). Cada prueba crea
 * su propio producto (SPT-n) para no depender de los datos de las demás.
 */
@ExtendWith(BaseDePruebas.class)
class ProcedimientosTest {

    private static int contador = 0;
    private final ProcedimientosDAO procedimientos = new ProcedimientosDAO();
    private int almacen, compras, supervisor, bodega1, bodega2;

    @BeforeEach
    void preparar() throws Exception {
        assumeFalse(ConexionDB.esH2(), "Los procedimientos almacenados solo se ejecutan en MySQL");
        almacen = numero("SELECT id_usuario FROM usuario WHERE nombre_usuario = 'almacen'");
        compras = numero("SELECT id_usuario FROM usuario WHERE nombre_usuario = 'compras'");
        supervisor = numero("SELECT id_usuario FROM usuario WHERE nombre_usuario = 'supervisor'");
        bodega1 = numero("SELECT id_bodega FROM bodega WHERE codigo = 'BOD-01'");
        bodega2 = numero("SELECT id_bodega FROM bodega WHERE codigo = 'BOD-02'");
    }

    // =========================================================== sp_evaluar_alertas_producto (unitarias)

    @Test
    void alertasBajoStockSeCreaUnaSolaVez() throws Exception {
        int p = producto(10, 50, 5);
        procedimientos.evaluarAlertas(p);
        procedimientos.evaluarAlertas(p);      // otra vez: no debe repetirla
        assertEquals(1, alertasActivas(p, "BAJO_STOCK"), "Una sola alerta de bajo stock");
        assertEquals(5, numero("SELECT stock_detectado FROM alerta WHERE id_producto = " + p + " AND estado = 'ACTIVA'"));
        assertEquals(10, numero("SELECT umbral FROM alerta WHERE id_producto = " + p + " AND estado = 'ACTIVA'"));
    }

    @Test
    void alertasSeResuelveElBajoStockYApareceElSobrestock() throws Exception {
        int p = producto(10, 50, 5);
        procedimientos.evaluarAlertas(p);
        ejecutar("UPDATE existencia SET cantidad = 60 WHERE id_producto = " + p);
        procedimientos.evaluarAlertas(p);
        assertEquals(0, alertasActivas(p, "BAJO_STOCK"), "El bajo stock quedó resuelto");
        assertEquals(1, alertasActivas(p, "SOBRESTOCK"));
        ejecutar("UPDATE existencia SET cantidad = 30 WHERE id_producto = " + p);
        procedimientos.evaluarAlertas(p);
        assertEquals(0, alertasActivas(p, "SOBRESTOCK") + alertasActivas(p, "BAJO_STOCK"), "Stock normal: sin alertas");
    }

    @Test
    void alertasUsaLosUmbralesGeneralesYNoEvaluaProductosInactivos() throws Exception {
        int p = producto(null, null, 3);      // sin umbrales propios: usa stock_minimo_defecto (10)
        procedimientos.evaluarAlertas(p);
        assertEquals(10, numero("SELECT umbral FROM alerta WHERE id_producto = " + p + " AND estado = 'ACTIVA'"));
        int inactivo = producto(10, 50, 1);
        ejecutar("UPDATE producto SET estado = 'INACTIVO' WHERE id_producto = " + inactivo);
        procedimientos.evaluarAlertas(inactivo);
        assertEquals(0, alertasActivas(inactivo, "BAJO_STOCK"), "Un producto inactivo no genera alertas");
    }

    // =========================================================== sp_autorizar_movimiento (unitarias)

    @Test
    void autorizarEntradaSumaElStockYMarcaElMovimiento() throws Exception {
        int p = producto(0, 100, 20);
        int m = movimiento(p, "Compra", null, bodega1, almacen, 8);
        assertEquals("Movimiento autorizado", procedimientos.autorizarMovimiento(m, compras));
        assertEquals(28, existencia(p, bodega1));
        assertEquals("AUTORIZADO", texto("SELECT estado FROM movimiento_inventario WHERE id_movimiento = " + m));
        assertEquals(compras, numero("SELECT id_usuario_autoriza FROM movimiento_inventario WHERE id_movimiento = " + m));
    }

    @Test
    void autorizarSalidaRestaElStock() throws Exception {
        int p = producto(0, 100, 20);
        int m = movimiento(p, "Merma", bodega1, null, almacen, 6);
        procedimientos.autorizarMovimiento(m, supervisor);
        assertEquals(14, existencia(p, bodega1));
    }

    @Test
    void autorizarSalidaSinStockSeRechazaYNoCambiaNada() throws Exception {
        int p = producto(0, 100, 5);
        int m = movimiento(p, "Merma", bodega1, null, almacen, 9);
        NegocioException e = assertThrows(NegocioException.class, () -> procedimientos.autorizarMovimiento(m, supervisor));
        assertEquals("Stock insuficiente en la bodega de origen", e.getMessage());
        assertEquals(5, existencia(p, bodega1), "ROLLBACK: el stock no cambió");
        assertEquals("PENDIENTE", texto("SELECT estado FROM movimiento_inventario WHERE id_movimiento = " + m));
    }

    @Test
    void autorizarRespetaElRolYLaSegregacionDeFunciones() throws Exception {
        int p = producto(0, 100, 20);
        int merma = movimiento(p, "Merma", bodega1, null, almacen, 2);
        NegocioException rol = assertThrows(NegocioException.class, () -> procedimientos.autorizarMovimiento(merma, compras));
        assertTrue(rol.getMessage().contains("no tiene el rol"), rol.getMessage());
        int propia = movimiento(p, "Merma", bodega1, null, supervisor, 2);
        NegocioException mismo = assertThrows(NegocioException.class, () -> procedimientos.autorizarMovimiento(propia, supervisor));
        assertEquals("Quien registra un movimiento no puede autorizarlo", mismo.getMessage());
    }

    @Test
    void autorizarDosVecesElMismoMovimientoNoSePuede() throws Exception {
        int p = producto(0, 100, 20);
        int m = movimiento(p, "Compra", null, bodega1, almacen, 1);
        procedimientos.autorizarMovimiento(m, compras);
        NegocioException e = assertThrows(NegocioException.class, () -> procedimientos.autorizarMovimiento(m, compras));
        assertEquals("El movimiento no existe o ya fue resuelto", e.getMessage());
        assertEquals(21, existencia(p, bodega1), "No sumó dos veces");
    }

    // =========================================================== sp_registrar_transferencia (unitarias)

    @Test
    void transferenciaMueveElStockYDevuelveElNumero() throws Exception {
        int p = producto(0, 100, 20);
        int m = procedimientos.registrarTransferencia(p, bodega1, bodega2, 7, almacen, "Prueba del taller");
        assertTrue(m > 0);
        assertEquals(13, existencia(p, bodega1));
        assertEquals(7, existencia(p, bodega2), "Creó la existencia en la bodega destino");
        assertEquals("AUTORIZADO", texto("SELECT estado FROM movimiento_inventario WHERE id_movimiento = " + m));
    }

    @Test
    void transferenciaValidaLosDatosYElStock() throws Exception {
        int p = producto(0, 100, 4);
        int antes = numero("SELECT COUNT(*) FROM movimiento_inventario");
        assertThrows(NegocioException.class, () -> procedimientos.registrarTransferencia(p, bodega1, bodega1, 1, almacen, null));
        assertThrows(NegocioException.class, () -> procedimientos.registrarTransferencia(p, bodega1, bodega2, 0, almacen, null));
        NegocioException stock = assertThrows(NegocioException.class,
                () -> procedimientos.registrarTransferencia(p, bodega1, bodega2, 10, almacen, null));
        assertEquals("Stock insuficiente en la bodega de origen", stock.getMessage());
        NegocioException permiso = assertThrows(NegocioException.class,
                () -> procedimientos.registrarTransferencia(p, bodega1, bodega2, 1, compras, null));
        assertEquals("El usuario no tiene permiso para registrar movimientos", permiso.getMessage());
        assertEquals(antes, numero("SELECT COUNT(*) FROM movimiento_inventario"), "No se guardó ningún movimiento");
        assertEquals(4, existencia(p, bodega1));
    }

    // =========================================================== sp_consultar_existencias (unitaria)

    @Test
    void consultarDevuelveLasFilasYElTotal() throws Exception {
        int p = producto(0, 100, 12);
        ejecutar("INSERT INTO existencia (id_producto, id_bodega, cantidad) VALUES (" + p + ", " + bodega2 + ", 3)");
        ProcedimientosDAO.ConsultaExistencias c = procedimientos.consultarExistencias(p);
        assertEquals(2, c.filas().size(), "Una fila por bodega");
        assertEquals(15, c.total(), "El parámetro OUT trae el total");
        assertEquals("BOD-01", c.filas().get(0).codigoBodega());
        assertTrue(procedimientos.consultarExistencias(null).filas().size() >= 2, "NULL = todos los productos");
    }

    // =========================================================== integración

    @Test
    void integracionAutorizarLlamaAlProcedimientoDeAlertas() throws Exception {
        int p = producto(10, 50, 45);
        int m = movimiento(p, "Compra", null, bodega1, almacen, 10);   // 45 + 10 = 55 > 50
        procedimientos.autorizarMovimiento(m, compras);
        assertEquals(1, alertasActivas(p, "SOBRESTOCK"), "sp_autorizar_movimiento llamó a sp_evaluar_alertas_producto");
        int salida = movimiento(p, "Merma", bodega1, null, almacen, 50);   // 55 - 50 = 5 <= 10
        procedimientos.autorizarMovimiento(salida, supervisor);
        assertEquals(0, alertasActivas(p, "SOBRESTOCK"));
        assertEquals(1, alertasActivas(p, "BAJO_STOCK"));
    }

    @Test
    void integracionFlujoCompletoYRollbackSinEfectosParciales() throws Exception {
        int p = producto(0, 1000, 0);
        // 1) Llega una compra y se autoriza
        procedimientos.autorizarMovimiento(movimiento(p, "Compra", null, bodega1, almacen, 30), compras);
        // 2) Se pasa una parte a la bodega de mostrador
        procedimientos.registrarTransferencia(p, bodega1, bodega2, 12, almacen, "Para el mostrador");
        // 3) Una merma más grande que lo que hay en el mostrador falla y no deja nada a medias
        int merma = movimiento(p, "Merma", bodega2, null, almacen, 20);
        assertThrows(NegocioException.class, () -> procedimientos.autorizarMovimiento(merma, supervisor));
        // 4) La consulta cuadra con todo lo anterior: 18 en principal + 12 en mostrador
        ProcedimientosDAO.ConsultaExistencias c = procedimientos.consultarExistencias(p);
        assertEquals(30, c.total());
        assertEquals(18, existencia(p, bodega1));
        assertEquals(12, existencia(p, bodega2));
        // 5) El kardex de la aplicación Java ve los movimientos que hicieron los procedimientos
        assertEquals(2, new ReporteDAO().kardex(p, null, null, null).size(), "Compra y transferencia en el kardex");
    }

    // =========================================================== ayudas

    // Crea un producto de prueba con su existencia en la bodega principal.
    private int producto(Integer minimo, Integer maximo, int stockInicial) throws Exception {
        contador++;
        String codigo = "SPT-" + System.nanoTime() % 100000 + "-" + contador;
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO producto (codigo, nombre, id_categoria, unidad_medida, "
                     + "costo_referencia, stock_minimo, stock_maximo) VALUES (?, ?, 1, 'Unidad', 1000, ?, ?)",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setString(1, codigo);
            ps.setString(2, "Producto de prueba de procedimientos " + codigo);
            ps.setObject(3, minimo);
            ps.setObject(4, maximo);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                int id = rs.getInt(1);
                if (stockInicial > 0) {
                    ejecutar("INSERT INTO existencia (id_producto, id_bodega, cantidad) VALUES (" + id + ", " + bodega1 + ", " + stockInicial + ")");
                }
                return id;
            }
        }
    }

    // Registra un movimiento PENDIENTE como lo dejaría la aplicación.
    private int movimiento(int producto, String tipo, Integer origen, Integer destino, int usuario, int cantidad) throws Exception {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("INSERT INTO movimiento_inventario (id_producto, id_tipo_movimiento, "
                     + "id_bodega_origen, id_bodega_destino, id_usuario, estado, cantidad, documento_soporte, motivo) "
                     + "VALUES (?, (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre = ?), ?, ?, ?, 'PENDIENTE', ?, 'FAC-SP-1', 'Prueba')",
                     Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, producto);
            ps.setString(2, tipo);
            ps.setObject(3, origen);
            ps.setObject(4, destino);
            ps.setInt(5, usuario);
            ps.setInt(6, cantidad);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private int existencia(int producto, int bodega) throws Exception {
        return numero("SELECT COALESCE(SUM(cantidad), 0) FROM existencia WHERE id_producto = " + producto + " AND id_bodega = " + bodega);
    }

    private int alertasActivas(int producto, String tipo) throws Exception {
        return numero("SELECT COUNT(*) FROM alerta WHERE id_producto = " + producto + " AND tipo = '" + tipo + "' AND estado = 'ACTIVA'");
    }

    private static int numero(String sql) throws Exception {
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            return rs.getInt(1);
        }
    }

    private static String texto(String sql) throws Exception {
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), sql);
            assertNotNull(rs.getString(1));
            return rs.getString(1);
        }
    }

    private static void ejecutar(String sql) throws Exception {
        try (Connection con = ConexionDB.obtener(); Statement st = con.createStatement()) {
            st.executeUpdate(sql);
        }
    }
}
