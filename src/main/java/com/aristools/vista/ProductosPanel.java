package com.aristools.vista;

import com.aristools.controlador.ProductoControlador;
import com.aristools.modelo.Categoria;
import com.aristools.modelo.EstadoRegistro;
import com.aristools.modelo.Producto;
import com.aristools.util.NegocioException;
import java.math.BigDecimal;
import java.util.List;
import javax.swing.JComboBox;
import javax.swing.JTextField;

/**
 * Gestión de productos (RF-003). El stock NO se edita aquí: solo cambia con
 * movimientos autorizados. Los umbrales vacíos usan los valores generales
 * de Configuración (RF-015).
 */
public class ProductosPanel extends PanelCatalogo<Producto> {

    private static final String[] UNIDADES = {"Unidad", "Caja", "Galón", "Cuarto", "Metro", "Kilo",
        "Bulto", "Rollo", "Juego", "Paquete"};

    private final ProductoControlador controlador = new ProductoControlador();
    private final JTextField txtCodigo = new JTextField();
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtDescripcion = new JTextField();
    private final JComboBox<Categoria> cmbCategoria = new JComboBox<>();
    private final JComboBox<String> cmbUnidad = new JComboBox<>(UNIDADES);
    private final JTextField txtCosto = new JTextField();
    private final JTextField txtMinimo = new JTextField();
    private final JTextField txtMaximo = new JTextField();

    public ProductosPanel() {
        super("Productos", "Artículos del inventario con su costo y sus umbrales de alerta.");
        cmbUnidad.setEditable(true);   // permite escribir otra unidad si no está en la lista
        construir();
    }

    @Override
    protected String[] columnas() {
        return new String[]{"Código", "Nombre", "Categoría", "Unidad", "Costo ref.", "Mínimo", "Máximo", "Estado"};
    }

    @Override
    protected int[] anchosColumnas() {
        return new int[]{80, 250, 110, 80, 90, 70, 70, 80};
    }

    @Override
    protected Object[] fila(Producto p) {
        return new Object[]{p.getCodigo(), p.getNombre(), p.getCategoria(), p.getUnidadMedida(),
            p.getCostoReferencia(), p.getStockMinimo() == null ? "General" : p.getStockMinimo(),
            p.getStockMaximo() == null ? "General" : p.getStockMaximo(), p.getEstado()};
    }

    @Override
    protected List<Producto> consultar(String filtro, boolean inactivos) {
        return controlador.listarProductos(filtro, inactivos);
    }

    /** Recarga las categorías activas por si se crearon en la otra pestaña. */
    @Override
    protected void alAbrir() {
        cmbCategoria.removeAllItems();
        for (Categoria c : controlador.listarCategorias(false)) {
            cmbCategoria.addItem(c);
        }
    }

    @Override
    protected void construirFormulario() {
        agregarCampo("Código", txtCodigo);
        agregarCampo("Nombre", txtNombre);
        agregarCampo("Descripción", txtDescripcion);
        agregarCampo("Categoría", cmbCategoria);
        agregarCampo("Unidad", cmbUnidad);
        agregarCampo("Costo de referencia", txtCosto);
        agregarCampo("Stock mínimo", txtMinimo);
        agregarCampo("Stock máximo", txtMaximo);
        agregarNota("Deje el mínimo o el máximo vacío para usar los umbrales generales de Configuración. "
                + "El stock no se edita aquí: solo cambia con movimientos autorizados.");
    }

    @Override
    protected void mostrar(Producto p) {
        txtCodigo.setText(p.getCodigo());
        txtNombre.setText(p.getNombre());
        txtDescripcion.setText(p.getDescripcion());
        seleccionarCategoria(p.getCategoria());
        cmbUnidad.setSelectedItem(p.getUnidadMedida());
        txtCosto.setText(p.getCostoReferencia() == null ? "" : p.getCostoReferencia().toPlainString());
        txtMinimo.setText(p.getStockMinimo() == null ? "" : p.getStockMinimo().toString());
        txtMaximo.setText(p.getStockMaximo() == null ? "" : p.getStockMaximo().toString());
    }

    private void seleccionarCategoria(Categoria categoria) {
        for (int i = 0; i < cmbCategoria.getItemCount(); i++) {
            if (categoria != null && cmbCategoria.getItemAt(i).getIdCategoria() == categoria.getIdCategoria()) {
                cmbCategoria.setSelectedIndex(i);
                return;
            }
        }
        cmbCategoria.setSelectedIndex(-1);
    }

    @Override
    protected void limpiar() {
        for (JTextField t : new JTextField[]{txtCodigo, txtNombre, txtDescripcion, txtCosto, txtMinimo, txtMaximo}) {
            t.setText("");
        }
        cmbCategoria.setSelectedIndex(-1);
        cmbUnidad.setSelectedIndex(0);
    }

    @Override
    protected void guardar() {
        Producto p = new Producto(seleccionado == null ? 0 : seleccionado.getIdProducto(),
                txtCodigo.getText().trim(), txtNombre.getText().trim(), txtDescripcion.getText().trim(),
                (Categoria) cmbCategoria.getSelectedItem(),
                cmbUnidad.getSelectedItem() == null ? "" : cmbUnidad.getSelectedItem().toString().trim(),
                leerCosto(), enteroOpcional(txtMinimo.getText(), "stock mínimo"),
                enteroOpcional(txtMaximo.getText(), "stock máximo"),
                seleccionado == null ? EstadoRegistro.ACTIVO : seleccionado.getEstado());
        controlador.guardarProducto(p);
    }

    private BigDecimal leerCosto() {
        // Acepta 21500, 21.500 (punto de miles), 21500,50 y 21500.50
        String texto = txtCosto.getText().trim().replace("$", "").replace(" ", "");
        if (texto.matches("\\d{1,3}(\\.\\d{3})+(,\\d+)?")) {
            texto = texto.replace(".", "").replace(",", ".");
        } else {
            texto = texto.replace(",", ".");
        }
        if (texto.isEmpty()) {
            throw new NegocioException("El campo costo de referencia es obligatorio.");
        }
        try {
            return new BigDecimal(texto);
        } catch (NumberFormatException e) {
            throw new NegocioException("El costo de referencia debe ser un número (ejemplo: 21500 o 21500.50).");
        }
    }

    @Override
    protected void cambiarEstado(Producto p) {
        controlador.cambiarEstadoProducto(p.getIdProducto(), p.estaActivo() ? EstadoRegistro.INACTIVO : EstadoRegistro.ACTIVO);
    }

    @Override
    protected boolean estaActivo(Producto p) {
        return p.estaActivo();
    }
}
