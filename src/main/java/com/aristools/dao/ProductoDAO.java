package com.aristools.dao;

import com.aristools.conexion.ConexionDB;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Producto;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;

/**
 * Acceso a la tabla producto (RF-003). El stock NO se guarda aquí:
 * se obtiene de la tabla existencia.
 */
public class ProductoDAO {

    private static final String SELECT =
            "SELECT p.id_producto, p.codigo, p.nombre, p.descripcion, p.unidad_medida, p.costo_referencia, "
          + "p.stock_minimo, p.stock_maximo, p.estado, c.id_categoria, c.nombre AS cat_nombre, "
          + "c.descripcion AS cat_desc, c.estado AS cat_estado "
          + "FROM producto p JOIN categoria c ON c.id_categoria = p.id_categoria ";

    /**
     * Lista productos filtrando por código o nombre.
     * @param filtro texto a buscar; vacío o null para todos
     * @param inactivos true para incluir los inactivos
     */
    public List<Producto> listar(String filtro, boolean inactivos) throws SQLException {
        String sql = SELECT + "WHERE (? = '' OR p.codigo LIKE ? OR p.nombre LIKE ?) "
                   + (inactivos ? "" : "AND p.estado = 'ACTIVO' ") + "ORDER BY p.codigo";
        String f = filtro == null ? "" : filtro.trim();
        List<Producto> lista = new ArrayList<>();
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            ps.setString(1, f);
            ps.setString(2, "%" + f + "%");
            ps.setString(3, "%" + f + "%");
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lista.add(mapear(rs));
                }
            }
        }
        return lista;
    }

    public Producto buscarPorId(int id) throws SQLException {
        return buscarUno("p.id_producto = ?", id);
    }

    /** Se usa para validar que el código sea único (RF-003). */
    public Producto buscarPorCodigo(String codigo) throws SQLException {
        return buscarUno("p.codigo = ?", codigo);
    }

    public int insertar(Producto p) throws SQLException {
        String sql = "INSERT INTO producto (codigo, nombre, descripcion, id_categoria, unidad_medida, "
                   + "costo_referencia, stock_minimo, stock_maximo, estado) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS)) {
            llenar(ps, p);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    public void actualizar(Producto p) throws SQLException {
        String sql = "UPDATE producto SET codigo = ?, nombre = ?, descripcion = ?, id_categoria = ?, "
                   + "unidad_medida = ?, costo_referencia = ?, stock_minimo = ?, stock_maximo = ?, estado = ? "
                   + "WHERE id_producto = ?";
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(sql)) {
            llenar(ps, p);
            ps.setInt(10, p.getIdProducto());
            ps.executeUpdate();
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("UPDATE producto SET estado = ? WHERE id_producto = ?")) {
            ps.setString(1, e.name());
            ps.setInt(2, id);
            ps.executeUpdate();
        }
    }

    public int contarActivos() throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement("SELECT COUNT(*) FROM producto WHERE estado = 'ACTIVO'");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getInt(1);
        }
    }

    // Busca un solo producto con la condición indicada.
    private Producto buscarUno(String condicion, Object valor) throws SQLException {
        try (Connection con = ConexionDB.obtener();
             PreparedStatement ps = con.prepareStatement(SELECT + "WHERE " + condicion)) {
            ps.setObject(1, valor);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapear(rs) : null;
            }
        }
    }

    // Pasa los 9 campos del producto a la sentencia (mismo orden en INSERT y UPDATE).
    private void llenar(PreparedStatement ps, Producto p) throws SQLException {
        ps.setString(1, p.getCodigo());
        ps.setString(2, p.getNombre());
        ps.setString(3, p.getDescripcion());
        ps.setInt(4, p.getCategoria().getIdCategoria());
        ps.setString(5, p.getUnidadMedida());
        ps.setBigDecimal(6, p.getCostoReferencia());
        ps.setObject(7, p.getStockMinimo(), Types.INTEGER);   // null = usa el umbral general
        ps.setObject(8, p.getStockMaximo(), Types.INTEGER);
        ps.setString(9, p.getEstado().name());
    }

    // Convierte la fila actual en un Producto con su Categoria.
    private Producto mapear(ResultSet rs) throws SQLException {
        Categoria c = new Categoria(rs.getInt("id_categoria"), rs.getString("cat_nombre"),
                rs.getString("cat_desc"), EstadoRegistro.valueOf(rs.getString("cat_estado")));
        return new Producto(rs.getInt("id_producto"), rs.getString("codigo"), rs.getString("nombre"),
                rs.getString("descripcion"), c, rs.getString("unidad_medida"), rs.getBigDecimal("costo_referencia"),
                rs.getObject("stock_minimo", Integer.class), rs.getObject("stock_maximo", Integer.class),
                EstadoRegistro.valueOf(rs.getString("estado")));
    }
}
