package com.aristools.controlador;

import com.aristools.util.Cambios;
import com.aristools.dao.CategoriaDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Producto;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Productos y categorías (RF-003). Consultar está permitido a todos los roles
 * (los necesitan los combos de movimientos); crear, editar e inactivar solo
 * al Administrador.
 */
public class ProductoControlador {

    private final ProductoDAO productoDAO = new ProductoDAO();
    private final CategoriaDAO categoriaDAO = new CategoriaDAO();

    public List<Producto> listarProductos(String filtro, boolean inactivos) {
        try {
            return productoDAO.listar(filtro, inactivos);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Crea (idProducto = 0) o actualiza. Código único y mínimo ≤ máximo. */
    public void guardarProducto(Producto p) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        Validador.textoObligatorio(p.getCodigo(), "código");
        Validador.textoObligatorio(p.getNombre(), "nombre");
        Validador.textoObligatorio(p.getUnidadMedida(), "unidad de medida");
        if (p.getCategoria() == null) {
            throw new NegocioException("Seleccione la categoría del producto.");
        }
        if (p.getCostoReferencia() == null || p.getCostoReferencia().compareTo(BigDecimal.ZERO) < 0) {
            throw new NegocioException("El costo de referencia debe ser un valor mayor o igual a cero.");
        }
        if (!p.umbralesValidos()) {
            throw new NegocioException("Los umbrales no pueden ser negativos y el stock mínimo no puede ser mayor que el máximo.");
        }
        try {
            Producto existente = productoDAO.buscarPorCodigo(p.getCodigo().trim());
            if (existente != null && existente.getIdProducto() != p.getIdProducto()) {
                throw new NegocioException("Ya existe un producto con el código " + p.getCodigo().trim() + ".");
            }
            p.setCodigo(p.getCodigo().trim());
            if (p.getIdProducto() == 0) {
                p.setIdProducto(productoDAO.insertar(p));
                Auditoria.registrar("CREAR_PRODUCTO", "Creó " + p.getCodigo() + " " + p.getNombre()
                        + " (costo " + p.getCostoReferencia().stripTrailingZeros().toPlainString() + ")");
            } else {
                Producto anterior = productoDAO.buscarPorId(p.getIdProducto());
                productoDAO.actualizar(p);
                if (anterior != null) {
                    Cambios c = new Cambios()
                            .campo("código", anterior.getCodigo(), p.getCodigo())
                            .campo("nombre", anterior.getNombre(), p.getNombre())
                            .campo("descripción", anterior.getDescripcion(), p.getDescripcion())
                            .campo("categoría", nombreCategoria(anterior.getCategoria()), nombreCategoria(p.getCategoria()))
                            .campo("unidad", anterior.getUnidadMedida(), p.getUnidadMedida())
                            .campo("costo", anterior.getCostoReferencia(), p.getCostoReferencia())
                            .campo("stock mínimo", anterior.getStockMinimo(), p.getStockMinimo())
                            .campo("stock máximo", anterior.getStockMaximo(), p.getStockMaximo());
                    if (c.hayCambios()) {
                        Auditoria.registrar("MODIFICAR_PRODUCTO", p.getCodigo() + " " + p.getNombre() + ": " + c);
                    }
                }
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Inactivar conserva el historial; el producto deja de aparecer en movimientos nuevos. */
    public void cambiarEstadoProducto(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        try {
            Producto anterior = productoDAO.buscarPorId(id);
            productoDAO.cambiarEstado(id, e);
            if (anterior != null) {
                Auditoria.registrar("ESTADO_PRODUCTO", anterior.getCodigo() + " " + anterior.getNombre() + ": "
                        + anterior.getEstado() + " → " + e);
            }
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
    }

    public List<Categoria> listarCategorias(boolean inactivas) {
        try {
            return categoriaDAO.listar(inactivas);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void guardarCategoria(Categoria c) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        Validador.textoObligatorio(c.getNombre(), "nombre");
        try {
            if (c.getIdCategoria() == 0) {
                c.setIdCategoria(categoriaDAO.insertar(c));
                Auditoria.registrar("CREAR_CATEGORIA", "Creó la categoría " + c.getNombre());
            } else {
                verificarInactivacion(c.getIdCategoria(), c.getEstado());
                Categoria anterior = buscarCategoria(c.getIdCategoria());
                categoriaDAO.actualizar(c);
                if (anterior != null) {
                    Cambios cambios = new Cambios()
                            .campo("nombre", anterior.getNombre(), c.getNombre())
                            .campo("descripción", anterior.getDescripcion(), c.getDescripcion())
                            .campo("estado", anterior.getEstado(), c.getEstado());
                    if (cambios.hayCambios()) {
                        Auditoria.registrar("MODIFICAR_CATEGORIA", "Categoría " + anterior.getNombre() + ": " + cambios);
                    }
                }
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void cambiarEstadoCategoria(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        try {
            verificarInactivacion(id, e);
            Categoria anterior = buscarCategoria(id);
            categoriaDAO.cambiarEstado(id, e);
            if (anterior != null) {
                Auditoria.registrar("ESTADO_CATEGORIA", "Categoría " + anterior.getNombre() + ": "
                        + anterior.getEstado() + " → " + e);
            }
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
    }

    // RF-003: una categoría con productos activos no se puede inactivar.
    private void verificarInactivacion(int idCategoria, EstadoRegistro nuevo) throws SQLException {
        if (nuevo == EstadoRegistro.INACTIVO && categoriaDAO.tieneProductosActivos(idCategoria)) {
            throw new NegocioException("No se puede inactivar: la categoría tiene productos activos.");
        }
    }

    private Categoria buscarCategoria(int id) throws SQLException {
        return categoriaDAO.listar(true).stream().filter(x -> x.getIdCategoria() == id).findFirst().orElse(null);
    }

    private static String nombreCategoria(Categoria c) {
        return c == null ? null : c.getNombre();
    }
}
