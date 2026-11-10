package com.aristools.vista;

import com.aristools.controlador.ProductoControlador;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.EstadoRegistro;
import java.util.List;
import javax.swing.JTextField;

/** Gestión de categorías (RF-003). Una categoría con productos activos no se puede inactivar. */
public class CategoriasPanel extends PanelCatalogo<Categoria> {

    private final ProductoControlador controlador = new ProductoControlador();
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtDescripcion = new JTextField();

    public CategoriasPanel() {
        super("Categorías", "Agrupe los productos por tipo, por ejemplo tornillería o pinturas.");
        construir();
    }

    @Override
    protected String[] columnas() {
        return new String[]{"Nombre", "Descripción", "Estado"};
    }

    @Override
    protected int[] anchosColumnas() {
        return new int[]{160, 320, 80};
    }

    @Override
    protected Object[] fila(Categoria c) {
        return new Object[]{c.getNombre(), c.getDescripcion(), c.getEstado()};
    }

    @Override
    protected List<Categoria> consultar(String filtro, boolean inactivos) {
        return controlador.listarCategorias(inactivos).stream()
                .filter(c -> contiene(filtro, c.getNombre(), c.getDescripcion())).toList();
    }

    @Override
    protected void construirFormulario() {
        agregarCampo("Nombre", txtNombre);
        agregarCampo("Descripción", txtDescripcion);
    }

    @Override
    protected void mostrar(Categoria c) {
        txtNombre.setText(c.getNombre());
        txtDescripcion.setText(c.getDescripcion());
    }

    @Override
    protected void limpiar() {
        txtNombre.setText("");
        txtDescripcion.setText("");
    }

    @Override
    protected void guardar() {
        // Se crea un objeto nuevo: si el controlador rechaza los datos, la tabla no queda modificada
        Categoria c = new Categoria(seleccionado == null ? 0 : seleccionado.getIdCategoria(),
                txtNombre.getText().trim(), txtDescripcion.getText().trim(),
                seleccionado == null ? EstadoRegistro.ACTIVO : seleccionado.getEstado());
        controlador.guardarCategoria(c);
    }

    @Override
    protected void cambiarEstado(Categoria c) {
        controlador.cambiarEstadoCategoria(c.getIdCategoria(),
                c.estaActiva() ? EstadoRegistro.INACTIVO : EstadoRegistro.ACTIVO);
    }

    @Override
    protected boolean estaActivo(Categoria c) {
        return c.estaActiva();
    }
}
