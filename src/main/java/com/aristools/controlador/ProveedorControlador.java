package com.aristools.controlador;

import com.aristools.util.Cambios;
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
                Auditoria.registrar("CREAR_PROVEEDOR", "Creó el proveedor " + p.getRazonSocial() + " (NIT " + p.getNit() + ")");
            } else {
                verificarInactivacion(p.getIdProveedor(), p.getEstado());
                Proveedor anterior = buscar(p.getIdProveedor());
                proveedorDAO.actualizar(p);
                if (anterior != null) {
                    Cambios c = new Cambios()
                            .campo("NIT", anterior.getNit(), p.getNit())
                            .campo("razón social", anterior.getRazonSocial(), p.getRazonSocial())
                            .campo("contacto", anterior.getContacto(), p.getContacto())
                            .campo("teléfono", anterior.getTelefono(), p.getTelefono())
                            .campo("correo", anterior.getCorreo(), p.getCorreo())
                            .campo("dirección", anterior.getDireccion(), p.getDireccion())
                            .campo("estado", anterior.getEstado(), p.getEstado());
                    if (c.hayCambios()) {
                        Auditoria.registrar("MODIFICAR_PROVEEDOR", "Proveedor " + anterior.getRazonSocial() + ": " + c);
                    }
                }
            }
        } catch (SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }

    public void cambiarEstado(int id, EstadoRegistro e) {
        Sesion.exigir(Accion.GESTIONAR_PROVEEDORES);
        try {
            verificarInactivacion(id, e);
            Proveedor anterior = buscar(id);
            proveedorDAO.cambiarEstado(id, e);
            if (anterior != null) {
                Auditoria.registrar("ESTADO_PROVEEDOR", "Proveedor " + anterior.getRazonSocial() + ": "
                        + anterior.getEstado() + " → " + e);
            }
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

    private Proveedor buscar(int id) throws SQLException {
        return proveedorDAO.listar(true).stream().filter(x -> x.getIdProveedor() == id).findFirst().orElse(null);
    }
}
