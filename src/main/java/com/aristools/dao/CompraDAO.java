package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.EstadoOrden;
import com.aristools.modelo.LineaOrdenCompra;
import com.aristools.modelo.OrdenCompra;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Datos para la sugerencia de compra, registro de las órdenes de compra y su
 * seguimiento (cuánto ha llegado de cada producto). Las órdenes se guardan
 * con sus productos en una sola transacción.
 */
public class CompraDAO {

    /** Datos de un producto activo que necesita la sugerencia de compra. */
    public static class DatosProducto {
        public int idProducto;
        public int stock;              // sin contar la mercancía en cuarentena
        public int salidas;            // unidades que salieron en el período
        public Integer idProveedor;    // proveedor de la última compra; null si nunca se ha comprado
        public BigDecimal ultimoCosto; // costo de la última compra; null si nunca se ha comprado
    }

    /**
     * Por cada producto activo: stock disponible, unidades que salieron desde
     * la fecha indicada y proveedor y costo de su última compra.
     *
     * Cuentan como salidas los movimientos AUTORIZADOS de naturaleza SALIDA
     * (consumo interno, merma, dañado o vencido y ajuste negativo), menos la
     * devolución a proveedor, que no es consumo. Las transferencias no
     * cuentan porque solo cambian la mercancía de bodega.
     */
    public List<DatosProducto> datosSugerencia(LocalDateTime desde) throws SQLException {
        String ultimaCompra = "FROM movimiento_inventario mc JOIN tipo_movimiento tc "
                + "ON tc.id_tipo_movimiento = mc.id_tipo_movimiento "
                + "WHERE mc.id_producto = p.id_producto AND tc.nombre = 'Compra' AND mc.estado = 'AUTORIZADO' "
                + "ORDER BY COALESCE(mc.fecha_autorizacion, mc.fecha) DESC, mc.id_movimiento DESC LIMIT 1";
        String sql = "SELECT p.id_producto, "
                + "COALESCE((SELECT SUM(e.cantidad) FROM existencia e JOIN bodega b ON b.id_bodega = e.id_bodega "
                + "          WHERE e.id_producto = p.id_producto AND b.es_cuarentena = FALSE), 0) AS stock, "
                + "COALESCE((SELECT SUM(m.cantidad) FROM movimiento_inventario m JOIN tipo_movimiento t "
                + "          ON t.id_tipo_movimiento = m.id_tipo_movimiento "
                + "          WHERE m.id_producto = p.id_producto AND m.estado = 'AUTORIZADO' "
                + "          AND t.naturaleza = 'SALIDA' AND t.nombre <> 'Devolución a proveedor' "
                + "          AND COALESCE(m.fecha_autorizacion, m.fecha) >= ?), 0) AS salidas, "
                + "(SELECT mc.id_proveedor " + ultimaCompra + ") AS id_proveedor, "
                + "(SELECT mc.costo_unitario " + ultimaCompra + ") AS ultimo_costo "
                + "FROM producto p WHERE p.estado = 'ACTIVO' ORDER BY p.codigo";
        List<DatosProducto> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setTimestamp(1, Timestamp.valueOf(desde));
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    DatosProducto d = new DatosProducto();
                    d.idProducto = rs.getInt("id_producto");
                    d.stock = rs.getInt("stock");
                    d.salidas = rs.getInt("salidas");
                    d.idProveedor = rs.getObject("id_proveedor", Integer.class);
                    d.ultimoCosto = rs.getBigDecimal("ultimo_costo");
                    lista.add(d);
                }
            }
        }
        return lista;
    }

    /**
     * Guarda la orden con todos sus productos en una transacción: se guarda
     * todo o nada. Devuelve el id, con el que se arma el número OC-.
     */
    public int insertar(OrdenCompra o) throws SQLException {
        try (Connection con = ConexionDB.obtener()) {
            con.setAutoCommit(false);
            try {
                int id;
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO orden_compra (id_proveedor, id_usuario, fecha, total, observacion) VALUES (?, ?, ?, ?, ?)",
                        Statement.RETURN_GENERATED_KEYS)) {
                    ps.setInt(1, o.getProveedor().getIdProveedor());
                    ps.setInt(2, o.getUsuario().getIdUsuario());
                    ps.setTimestamp(3, Timestamp.valueOf(o.getFecha()));
                    ps.setBigDecimal(4, o.total());
                    ps.setString(5, o.getObservacion());
                    ps.executeUpdate();
                    try (ResultSet rs = ps.getGeneratedKeys()) {
                        rs.next();
                        id = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = con.prepareStatement(
                        "INSERT INTO orden_compra_detalle (id_orden, id_producto, cantidad, costo_unitario) VALUES (?, ?, ?, ?)")) {
                    for (LineaOrdenCompra l : o.getLineas()) {
                        ps.setInt(1, id);
                        ps.setInt(2, l.getProducto().getIdProducto());
                        ps.setInt(3, l.getCantidad());
                        ps.setBigDecimal(4, l.getCostoUnitario());
                        ps.addBatch();
                    }
                    ps.executeBatch();
                }
                con.commit();
                o.setIdOrden(id);
                return id;
            } catch (SQLException | RuntimeException e) {
                con.rollback();
                throw e;
            }
        }
    }

    /**
     * Órdenes de compra con sus productos, de la más reciente a la más antigua.
     *
     * @param soloAbiertas true = solo las que todavía esperan mercancía (Pendiente o Recibida en parte)
     * @param idProveedor  solo las de ese proveedor (null = todos)
     */
    public List<OrdenCompra> listarOrdenes(boolean soloAbiertas, Integer idProveedor) throws SQLException {
        String sql = "SELECT o.id_orden, o.id_proveedor, o.fecha, o.observacion, o.estado, o.motivo_anulacion, "
                   + "u.id_usuario, u.nombre_usuario, u.nombre_completo, r.id_rol, r.nombre_rol "
                   + "FROM orden_compra o JOIN usuario u ON u.id_usuario = o.id_usuario "
                   + "JOIN rol r ON r.id_rol = u.id_rol WHERE 1 = 1 "
                   + (soloAbiertas ? "AND o.estado IN ('PENDIENTE', 'PARCIAL') " : "")
                   + (idProveedor != null ? "AND o.id_proveedor = ? " : "")
                   + "ORDER BY o.id_orden DESC";
        Map<Integer, Proveedor> proveedores = new HashMap<>();
        for (Proveedor p : new ProveedorDAO().listar(true)) {
            proveedores.put(p.getIdProveedor(), p);
        }
        Map<Integer, Producto> productos = new HashMap<>();
        for (Producto p : new ProductoDAO().listar("", true)) {
            productos.put(p.getIdProducto(), p);
        }
        Map<Integer, List<LineaOrdenCompra>> lineas = lineasPorOrden(productos);
        Map<Integer, OrdenCompra> ordenes = new LinkedHashMap<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            if (idProveedor != null) {
                ps.setInt(1, idProveedor);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    int id = rs.getInt("id_orden");
                    Usuario u = new Usuario();
                    u.setIdUsuario(rs.getInt("id_usuario"));
                    u.setNombreUsuario(rs.getString("nombre_usuario"));
                    u.setNombreCompleto(rs.getString("nombre_completo"));
                    u.setRol(new Rol(rs.getInt("id_rol"), rs.getString("nombre_rol"), null));
                    OrdenCompra o = new OrdenCompra(proveedores.get(rs.getInt("id_proveedor")), u,
                            lineas.getOrDefault(id, List.of()));
                    o.setIdOrden(id);
                    o.setFecha(rs.getTimestamp("fecha").toLocalDateTime());
                    o.setObservacion(rs.getString("observacion"));
                    o.setEstado(EstadoOrden.valueOf(rs.getString("estado")));
                    o.setMotivoAnulacion(rs.getString("motivo_anulacion"));
                    ordenes.put(id, o);
                }
            }
        }
        return new ArrayList<>(ordenes.values());
    }

    /** Una orden con sus productos; null si no existe. */
    public OrdenCompra buscarOrden(int idOrden) throws SQLException {
        for (OrdenCompra o : listarOrdenes(false, null)) {
            if (o.getIdOrden() == idOrden) {
                return o;
            }
        }
        return null;
    }

    // Productos de todas las órdenes, agrupados por número de orden.
    private Map<Integer, List<LineaOrdenCompra>> lineasPorOrden(Map<Integer, Producto> productos) throws SQLException {
        Map<Integer, List<LineaOrdenCompra>> mapa = new HashMap<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT d.id_orden, d.id_producto, d.cantidad, d.costo_unitario, "
                     + "d.cantidad_recibida, (SELECT COALESCE(SUM(m.cantidad), 0) FROM movimiento_inventario m "
                     + "WHERE m.id_orden = d.id_orden AND m.id_producto = d.id_producto AND m.estado = 'PENDIENTE') "
                     + "AS en_espera FROM orden_compra_detalle d ORDER BY d.id_orden, d.id_producto");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                LineaOrdenCompra l = new LineaOrdenCompra(productos.get(rs.getInt("id_producto")),
                        rs.getInt("cantidad"), rs.getBigDecimal("costo_unitario"));
                l.setCantidadRecibida(rs.getInt("cantidad_recibida"));
                l.setEnEspera(rs.getInt("en_espera"));
                mapa.computeIfAbsent(rs.getInt("id_orden"), k -> new ArrayList<>()).add(l);
            }
        }
        return mapa;
    }

    /**
     * Unidades de un producto que ya vienen en entradas PENDIENTES de esa
     * orden: todavía no llegaron al stock, pero ya no se pueden volver a recibir.
     */
    public int unidadesEnEspera(int idOrden, int idProducto) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COALESCE(SUM(cantidad), 0) FROM movimiento_inventario "
                     + "WHERE id_orden = ? AND id_producto = ? AND estado = 'PENDIENTE'")) {
            ps.setInt(1, idOrden);
            ps.setInt(2, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /** Cuántas entradas de esa orden esperan autorización. */
    public int entradasEnEspera(int idOrden) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM movimiento_inventario "
                     + "WHERE id_orden = ? AND estado = 'PENDIENTE'")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    /**
     * Suma a la orden lo que llegó con una entrada autorizada y recalcula su
     * estado (Recibida en parte o Recibida). Se llama dentro de la transacción
     * de la autorización: si algo falla, no se autoriza la entrada.
     * Bloquea la orden (FOR UPDATE) para que dos autorizaciones a la vez no
     * cuenten la misma mercancía.
     */
    public void recibir(Connection con, int idOrden, int idProducto, int cantidad) throws SQLException {
        String estado;
        try (PreparedStatement ps = con.prepareStatement(
                "SELECT estado FROM orden_compra WHERE id_orden = ? FOR UPDATE")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NegocioException("La orden de compra " + numero(idOrden) + " no existe.");
                }
                estado = rs.getString(1);
            }
        }
        if (!EstadoOrden.valueOf(estado).estaAbierta()) {
            throw new NegocioException("La orden " + numero(idOrden) + " está " + EstadoOrden.valueOf(estado).texto()
                    .toLowerCase() + ": ya no recibe mercancía. Rechace la entrada o pida que la registren sin orden.");
        }
        int pedido;
        int recibido;
        try (PreparedStatement ps = con.prepareStatement("SELECT cantidad, cantidad_recibida FROM orden_compra_detalle "
                + "WHERE id_orden = ? AND id_producto = ? FOR UPDATE")) {
            ps.setInt(1, idOrden);
            ps.setInt(2, idProducto);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new NegocioException("Ese producto no está en la orden " + numero(idOrden) + ".");
                }
                pedido = rs.getInt(1);
                recibido = rs.getInt(2);
            }
        }
        if (recibido + cantidad > pedido) {
            throw new NegocioException("La entrada trae " + cantidad + " unidades y a la orden " + numero(idOrden)
                    + " solo le faltan " + (pedido - recibido) + " de ese producto.");
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE orden_compra_detalle SET cantidad_recibida = "
                + "cantidad_recibida + ? WHERE id_orden = ? AND id_producto = ?")) {
            ps.setInt(1, cantidad);
            ps.setInt(2, idOrden);
            ps.setInt(3, idProducto);
            ps.executeUpdate();
        }
        // Nuevo estado: RECIBIDA si ya no falta nada; si no, PARCIAL
        int faltantes;
        try (PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM orden_compra_detalle "
                + "WHERE id_orden = ? AND cantidad_recibida < cantidad")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                faltantes = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = con.prepareStatement("UPDATE orden_compra SET estado = ? WHERE id_orden = ?")) {
            ps.setString(1, faltantes == 0 ? EstadoOrden.RECIBIDA.name() : EstadoOrden.PARCIAL.name());
            ps.setInt(2, idOrden);
            ps.executeUpdate();
        }
    }

    /**
     * Anula una orden abierta con su motivo. Devuelve false si la orden ya no
     * estaba abierta (otra persona la cerró al mismo tiempo).
     */
    public boolean anular(int idOrden, String motivo) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE orden_compra SET estado = 'ANULADA', motivo_anulacion = ? "
                     + "WHERE id_orden = ? AND estado IN ('PENDIENTE', 'PARCIAL')")) {
            ps.setString(1, motivo);
            ps.setInt(2, idOrden);
            return ps.executeUpdate() == 1;
        }
    }

    private static String numero(int idOrden) {
        return "OC-" + String.format("%06d", idOrden);
    }

    /** Valor total guardado de una orden (lo usan las pruebas para comprobar el registro). */
    public BigDecimal totalOrden(int idOrden) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT total FROM orden_compra WHERE id_orden = ?")) {
            ps.setInt(1, idOrden);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getBigDecimal(1) : null;
            }
        }
    }
}
