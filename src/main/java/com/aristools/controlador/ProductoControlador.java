package com.aristools.controlador;

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
            } else {
                productoDAO.actualizar(p);
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Inactivar conserva el historial; el producto deja de aparecer en movimientos nuevos. */
    public void cambiarEstadoProducto(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        try {
            productoDAO.cambiarEstado(id, e);
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
            } else {
                verificarInactivacion(c.getIdCategoria(), c.getEstado());
                categoriaDAO.actualizar(c);
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void cambiarEstadoCategoria(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_CATALOGOS);
        try {
            verificarInactivacion(id, e);
            categoriaDAO.cambiarEstado(id, e);
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
}
