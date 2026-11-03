package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Ajuste;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.EstadoMovimiento;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Naturaleza;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Salida;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.Transferencia;
import com.aristools.modelo.Usuario;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla movimiento_inventario (RF-006 a RF-011).
 * Al leer, crea el objeto de la subclase que corresponde (Entrada, Salida,
 * Transferencia o Ajuste) según el tipo de movimiento: así el resto del
 * programa trabaja con polimorfismo.
 */
public class MovimientoDAO {

    /** Consulta base con todos los datos relacionados; también la usa ReporteDAO. */
    static final String SELECT_BASE =
            "SELECT m.id_movimiento, m.cantidad, m.estado, m.fecha, m.fecha_autorizacion, m.motivo, "
          + "m.motivo_rechazo, m.documento_soporte, m.observacion, "
          + "p.id_producto, p.codigo AS p_codigo, p.nombre AS p_nombre, p.estado AS p_estado, "
          + "p.stock_minimo, p.stock_maximo, "
          + "t.id_tipo_movimiento, t.nombre AS t_nombre, t.naturaleza, t.requiere_autorizacion, "
          + "ra.id_rol AS ra_id, ra.nombre_rol AS ra_nombre, "
          + "ur.id_usuario AS ur_id, ur.nombre_usuario AS ur_nombre, "
          + "ua.id_usuario AS ua_id, ua.nombre_usuario AS ua_nombre, "
          + "bo.id_bodega AS bo_id, bo.codigo AS bo_codigo, bo.nombre AS bo_nombre, bo.estado AS bo_estado, "
          + "bd.id_bodega AS bd_id, bd.codigo AS bd_codigo, bd.nombre AS bd_nombre, bd.estado AS bd_estado, "
          + "pv.id_proveedor AS pv_id, pv.nit AS pv_nit, pv.razon_social AS pv_razon, pv.estado AS pv_estado "
          + "FROM movimiento_inventario m "
          + "JOIN producto p ON p.id_producto = m.id_producto "
          + "JOIN tipo_movimiento t ON t.id_tipo_movimiento = m.id_tipo_movimiento "
          + "LEFT JOIN rol ra ON ra.id_rol = t.id_rol_autorizador "
          + "JOIN usuario ur ON ur.id_usuario = m.id_usuario "
          + "LEFT JOIN usuario ua ON ua.id_usuario = m.id_usuario_autoriza "
          + "LEFT JOIN bodega bo ON bo.id_bodega = m.id_bodega_origen "
          + "LEFT JOIN bodega bd ON bd.id_bodega = m.id_bodega_destino "
          + "LEFT JOIN proveedor pv ON pv.id_proveedor = m.id_proveedor ";

    /** Guarda un movimiento nuevo dentro de la transacción y devuelve su id. */
    public int insertar(Connection con, Movimiento m) throws SQLException {
        Integer origen = null;
        Integer destino = null;
        Integer proveedor = null;
        if (m instanceof Entrada e) {
            destino = e.getBodegaDestino().getIdBodega();
            proveedor = e.getProveedor() == null ? null : e.getProveedor().getIdProveedor();
        } else if (m instanceof Salida s) {
            origen = s.getBodegaOrigen().getIdBodega();
            proveedor = s.getProveedor() == null ? null : s.getProveedor().getIdProveedor();
        } else if (m instanceof Transferencia t) {
            origen = t.getBodegaOrigen().getIdBodega();
            destino = t.getBodegaDestino().getIdBodega();
        } else if (m instanceof Ajuste a) {
            if (a.isPositivo()) {
                destino = a.getBodega().getIdBodega();
            } else {
                origen = a.getBodega().getIdBodega();
            }
        }
        String sql = "INSERT INTO movimiento_inventario (id_producto, id_tipo_movimiento, id_bodega_origen, "
                   + "id_bodega_destino, id_proveedor, id_usuario, estado, id_usuario_autoriza, fecha_autorizacion, "
                   + "documento_soporte, motivo, cantidad, fecha, observacion) "
                   + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, m.getProducto().getIdProducto());
            ps.setInt(2, m.getTipo().getIdTipoMovimiento());
            ps.setObject(3, origen, Types.INTEGER);
            ps.setObject(4, destino, Types.INTEGER);
            ps.setObject(5, proveedor, Types.INTEGER);
            ps.setInt(6, m.getUsuarioRegistra().getIdUsuario());
            ps.setString(7, m.getEstado().name());
            ps.setObject(8, m.getUsuarioAutoriza() == null ? null : m.getUsuarioAutoriza().getIdUsuario(), Types.INTEGER);
            ps.setTimestamp(9, m.getFechaAutorizacion() == null ? null : Timestamp.valueOf(m.getFechaAutorizacion()));
            ps.setString(10, m.getDocumentoSoporte());
            ps.setString(11, m.getMotivo());
            ps.setInt(12, m.getCantidad());
            ps.setTimestamp(13, Timestamp.valueOf(m.getFecha()));
            ps.setString(14, m.getObservacion());
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                int id = rs.getInt(1);
                m.setIdMovimiento(id);
                return id;
            }
        }
    }

    /**
     * Lee un movimiento y lo bloquea hasta el COMMIT, para que dos
     * autorizadores no lo resuelvan al mismo tiempo (RF-010, RF-011).
     */
    public Movimiento buscarParaResolver(Connection con, int id) throws SQLException {
        try (PreparedStatement ps = con.prepareStatement(SELECT_BASE + "WHERE m.id_movimiento = ? FOR UPDATE OF m")) {
            ps.setInt(1, id);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    /** Guarda la decisión (autorizado o rechazado). Solo cambia movimientos Pendientes. */
    public void actualizarResolucion(Connection con, Movimiento m) throws SQLException {
        String sql = "UPDATE movimiento_inventario SET estado = ?, id_usuario_autoriza = ?, "
                   + "fecha_autorizacion = ?, motivo_rechazo = ? WHERE id_movimiento = ? AND estado = 'PENDIENTE'";
        try (PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, m.getEstado().name());
            ps.setInt(2, m.getUsuarioAutoriza().getIdUsuario());
            ps.setTimestamp(3, Timestamp.valueOf(m.getFechaAutorizacion()));
            ps.setString(4, m.getMotivoRechazo());
            ps.setInt(5, m.getIdMovimiento());
            if (ps.executeUpdate() != 1) {
                throw new SQLException("El movimiento " + m.getIdMovimiento() + " ya no está pendiente.");
            }
        }
    }

    /**
     * Bandeja de autorización: ENTRADA para el Jefe de Compras (RF-010);
     * SALIDA para el Supervisor de Bodega, que incluye los ajustes negativos (RF-011).
     */
    public List<Movimiento> listarPendientes(Naturaleza n) throws SQLException {
        List<Movimiento> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT_BASE
                     + "WHERE m.estado = 'PENDIENTE' AND t.naturaleza = ? ORDER BY m.fecha, m.id_movimiento")) {
            ps.setString(1, n.name());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    /** Número que muestra la tarjeta "Movimientos pendientes" del panel principal. */
    public int contarPendientes() throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(
                     "SELECT COUNT(*) FROM movimiento_inventario WHERE estado = 'PENDIENTE'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    /**
     * Convierte una fila de SELECT_BASE en la subclase correcta de Movimiento.
     * Es de paquete para que ReporteDAO la reutilice sin duplicar código.
     */
    static Movimiento mapear(ResultSet rs) throws SQLException {
        Naturaleza naturaleza = Naturaleza.valueOf(rs.getString("naturaleza"));
        String nombreTipo = rs.getString("t_nombre");
        Bodega origen = bodega(rs, "bo");
        Bodega destino = bodega(rs, "bd");
        Proveedor proveedor = null;
        int idProv = rs.getInt("pv_id");
        if (!rs.wasNull()) {
            proveedor = new Proveedor();
            proveedor.setIdProveedor(idProv);
            proveedor.setNit(rs.getString("pv_nit"));
            proveedor.setRazonSocial(rs.getString("pv_razon"));
            proveedor.setEstado(EstadoRegistro.valueOf(rs.getString("pv_estado")));
        }

        Movimiento m;
        if (nombreTipo.startsWith("Ajuste")) {
            boolean positivo = naturaleza == Naturaleza.ENTRADA;
            m = new Ajuste(null, null, null, 0, positivo ? destino : origen, positivo);
        } else if (naturaleza == Naturaleza.ENTRADA) {
            m = new Entrada(null, null, null, 0, destino, proveedor);
        } else if (naturaleza == Naturaleza.SALIDA) {
            m = new Salida(null, null, null, 0, origen, proveedor);
        } else {
            m = new Transferencia(null, null, null, 0, origen, destino);
        }

        Producto p = new Producto();
        p.setIdProducto(rs.getInt("id_producto"));
        p.setCodigo(rs.getString("p_codigo"));
        p.setNombre(rs.getString("p_nombre"));
        p.setEstado(EstadoRegistro.valueOf(rs.getString("p_estado")));
        p.setStockMinimo(rs.getObject("stock_minimo", Integer.class));
        p.setStockMaximo(rs.getObject("stock_maximo", Integer.class));

        int idRol = rs.getInt("ra_id");
        Rol rolAutorizador = rs.wasNull() ? null : new Rol(idRol, rs.getString("ra_nombre"), null);
        TipoMovimiento tipo = new TipoMovimiento(rs.getInt("id_tipo_movimiento"), nombreTipo, naturaleza,
                rs.getBoolean("requiere_autorizacion"), rolAutorizador);

        Usuario registra = new Usuario();
        registra.setIdUsuario(rs.getInt("ur_id"));
        registra.setNombreUsuario(rs.getString("ur_nombre"));
        Usuario autoriza = null;
        int idAut = rs.getInt("ua_id");
        if (!rs.wasNull()) {
            autoriza = new Usuario();
            autoriza.setIdUsuario(idAut);
            autoriza.setNombreUsuario(rs.getString("ua_nombre"));
        }

        m.setIdMovimiento(rs.getInt("id_movimiento"));
        m.setProducto(p);
        m.setTipo(tipo);
        m.setUsuarioRegistra(registra);
        m.setUsuarioAutoriza(autoriza);
        m.setEstado(EstadoMovimiento.valueOf(rs.getString("estado")));
        m.setCantidad(rs.getInt("cantidad"));
        m.setFecha(aFecha(rs.getTimestamp("fecha")));
        m.setFechaAutorizacion(aFecha(rs.getTimestamp("fecha_autorizacion")));
        m.setMotivo(rs.getString("motivo"));
        m.setMotivoRechazo(rs.getString("motivo_rechazo"));
        m.setDocumentoSoporte(rs.getString("documento_soporte"));
        m.setObservacion(rs.getString("observacion"));
        return m;
    }

    // Arma la bodega de origen ("bo") o destino ("bd"); null si la columna está vacía.
    private static Bodega bodega(ResultSet rs, String prefijo) throws SQLException {
        int id = rs.getInt(prefijo + "_id");
        if (rs.wasNull()) {
            return null;
        }
        return new Bodega(id, rs.getString(prefijo + "_codigo"), rs.getString(prefijo + "_nombre"), null,
                EstadoRegistro.valueOf(rs.getString(prefijo + "_estado")));
    }

    private static LocalDateTime aFecha(Timestamp t) {
        return t == null ? null : t.toLocalDateTime();
    }
}
