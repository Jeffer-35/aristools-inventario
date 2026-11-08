package com.aristools.controlador;

import com.aristools.dao.ProveedorDAO;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Proveedor;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.Validador;
import java.sql.SQLException;
import java.util.List;

/** Gestión de proveedores (RF-005): Administrador y Jefe de Compras. */
public class ProveedorControlador {

    private final ProveedorDAO proveedorDAO = new ProveedorDAO();

    public List<Proveedor> listar(boolean inactivos) {
        try {
            return proveedorDAO.listar(inactivos);
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    /** Crea (idProveedor = 0) o actualiza; NIT único y correo con formato válido. */
    public void guardar(Proveedor p) {
        Sesion.exigir(Accion.GESTIONAR_PROVEEDORES);
        Validador.textoObligatorio(p.getNit(), "NIT o documento");
        Validador.textoObligatorio(p.getRazonSocial(), "razón social");
        if (p.getCorreo() != null && !p.getCorreo().isBlank() && !Validador.correoValido(p.getCorreo())) {
            throw new NegocioException("El correo no tiene un formato válido (ejemplo: ventas@empresa.com).");
        }
        try {
            if (p.getIdProveedor() == 0) {
                p.setIdProveedor(proveedorDAO.insertar(p));
            } else {
                verificarInactivacion(p.getIdProveedor(), p.getEstado());
                proveedorDAO.actualizar(p);
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_PROVEEDORES);
        try {
            verificarInactivacion(id, e);
            proveedorDAO.cambiarEstado(id, e);
        } catch (SQLException ex) {
            throw NegocioException.deBaseDeDatos(ex);
        }
    }

    // RF-005: no se inactiva un proveedor con movimientos pendientes.
    private void verificarInactivacion(int id, EstadoRegistro nuevo) throws SQLException {
        if (nuevo == EstadoRegistro.INACTIVO && proveedorDAO.tieneMovimientosPendientes(id)) {
            throw new NegocioException("No se puede inactivar: el proveedor tiene movimientos pendientes de autorización.");
        }
    }
}
