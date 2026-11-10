package com.aristools.vista;

import com.aristools.controlador.ProveedorControlador;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Proveedor;
import java.util.List;
import javax.swing.JTextField;

/**
 * Gestión de proveedores (RF-005). La usan el Jefe de Compras (opción propia
 * del menú) y el Administrador (pestaña de Catálogos).
 */
public class ProveedoresPanel extends PanelCatalogo<Proveedor> {

    private final ProveedorControlador controlador = new ProveedorControlador();
    private final JTextField txtNit = new JTextField();
    private final JTextField txtRazonSocial = new JTextField();
    private final JTextField txtContacto = new JTextField();
    private final JTextField txtTelefono = new JTextField();
    private final JTextField txtCorreo = new JTextField();
    private final JTextField txtDireccion = new JTextField();

    public ProveedoresPanel() {
        super("Proveedores", "Empresas que entregan mercancía o reciben devoluciones.");
        construir();
    }

    @Override
    protected String[] columnas() {
        return new String[]{"NIT", "Razón social", "Contacto", "Teléfono", "Correo", "Estado"};
    }

    @Override
    protected int[] anchosColumnas() {
        return new int[]{100, 220, 130, 100, 180, 80};
    }

    @Override
    protected Object[] fila(Proveedor p) {
        return new Object[]{p.getNit(), p.getRazonSocial(), p.getContacto(), p.getTelefono(),
            p.getCorreo(), p.getEstado()};
    }

    @Override
    protected List<Proveedor> consultar(String filtro, boolean inactivos) {
        return controlador.listar(inactivos).stream()
                .filter(p -> contiene(filtro, p.getNit(), p.getRazonSocial(), p.getContacto())).toList();
    }

    @Override
    protected void construirFormulario() {
        agregarCampo("NIT", txtNit);
        agregarCampo("Razón social", txtRazonSocial);
        agregarCampo("Contacto", txtContacto);
        agregarCampo("Teléfono", txtTelefono);
        agregarCampo("Correo", txtCorreo);
        agregarCampo("Dirección", txtDireccion);
        agregarNota("Los datos de contacto se tratan conforme a la Ley 1581 de 2012 de protección de "
                + "datos personales. Un proveedor con movimientos pendientes no se puede inactivar.");
    }

    @Override
    protected void mostrar(Proveedor p) {
        txtNit.setText(p.getNit());
        txtRazonSocial.setText(p.getRazonSocial());
        txtContacto.setText(p.getContacto());
        txtTelefono.setText(p.getTelefono());
        txtCorreo.setText(p.getCorreo());
        txtDireccion.setText(p.getDireccion());
    }

    @Override
    protected void limpiar() {
        for (JTextField t : new JTextField[]{txtNit, txtRazonSocial, txtContacto, txtTelefono, txtCorreo, txtDireccion}) {
            t.setText("");
        }
    }

    @Override
    protected void guardar() {
        Proveedor p = new Proveedor(seleccionado == null ? 0 : seleccionado.getIdProveedor(),
                txtNit.getText().trim(), txtRazonSocial.getText().trim(), txtContacto.getText().trim(),
                txtTelefono.getText().trim(), txtCorreo.getText().trim(), txtDireccion.getText().trim(),
                seleccionado == null ? EstadoRegistro.ACTIVO : seleccionado.getEstado());
        controlador.guardar(p);
    }

    @Override
    protected void cambiarEstado(Proveedor p) {
        controlador.cambiarEstado(p.getIdProveedor(), p.estaActivo() ? EstadoRegistro.INACTIVO : EstadoRegistro.ACTIVO);
    }

    @Override
    protected boolean estaActivo(Proveedor p) {
        return p.estaActivo();
    }
}
