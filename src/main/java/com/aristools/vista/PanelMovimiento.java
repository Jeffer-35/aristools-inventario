package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.controlador.MovimientoControlador;
import com.aristools.controlador.ProductoControlador;
import com.aristools.controlador.ProveedorControlador;
import com.aristools.controlador.ReporteControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Existencia;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JComboBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;

/**
 * Base común de las pestañas de Entrada, Salida y Transferencia (RF-006 a
 * RF-008). Reúne lo que comparten: cargar productos, bodegas y proveedores
 * activos, mostrar la existencia disponible y armar el formulario.
 * Cada subclase define sus campos y cómo se registra su movimiento.
 */
public abstract class PanelMovimiento extends JPanel implements Recargable {

    protected final MovimientoControlador movimientos = new MovimientoControlador();
    private final ProductoControlador productos = new ProductoControlador();
    private final BodegaControlador bodegas = new BodegaControlador();
    private final ProveedorControlador proveedores = new ProveedorControlador();
    private final ReporteControlador reportes = new ReporteControlador();

    protected final JPanel formulario = new JPanel(new GridBagLayout());
    private int fila = 0;

    protected PanelMovimiento() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(16, 20, 16, 20));
        formulario.setBackground(Tema.BLANCO);
        formulario.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                BorderFactory.createEmptyBorder(18, 22, 18, 22)));
    }

    /** Agrega una fila "etiqueta: campo" al formulario. */
    protected void agregarCampo(String etiqueta, JComponent campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila++;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 14);
        JLabel l = new JLabel(etiqueta);
        l.setFont(Tema.NEGRITA);
        formulario.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(6, 0, 6, 0);
        campo.setFont(Tema.NORMAL);
        campo.setPreferredSize(new Dimension(340, 32));
        formulario.add(campo, c);
    }

    /** Agrega un componente que ocupa las dos columnas (tabla, botones, notas). */
    protected void agregarFilaCompleta(JComponent componente) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.BOTH;
        c.insets = new Insets(8, 0, 4, 0);
        formulario.add(componente, c);
    }

    /** Texto de ayuda en gris. */
    protected static JLabel nota(String texto) {
        JLabel l = new JLabel("<html><body style='width:520px'>" + texto + "</body></html>");
        l.setFont(Tema.NORMAL.deriveFont(12f));
        l.setForeground(Tema.TEXTO_SUAVE);
        return l;
    }

    // ---------- Carga de combos (solo registros activos) ----------

    protected void cargarProductos(JComboBox<Producto> combo) {
        Object antes = combo.getSelectedItem();
        combo.removeAllItems();
        for (Producto p : productos.listarProductos("", false)) {
            combo.addItem(p);
        }
        reseleccionar(combo, antes);
    }

    protected void cargarBodegas(JComboBox<Bodega> combo) {
        Object antes = combo.getSelectedItem();
        combo.removeAllItems();
        for (Bodega b : bodegas.listar(false)) {
            combo.addItem(b);
        }
        reseleccionar(combo, antes);
    }

    protected void cargarProveedores(JComboBox<Proveedor> combo) {
        combo.removeAllItems();
        for (Proveedor p : proveedores.listar(false)) {
            combo.addItem(p);
        }
        combo.setSelectedIndex(-1);
    }

    // Conserva la selección al recargar (compara por texto porque son objetos nuevos)
    private static <T> void reseleccionar(JComboBox<T> combo, Object antes) {
        if (antes == null) {
            combo.setSelectedIndex(-1);
            return;
        }
        for (int i = 0; i < combo.getItemCount(); i++) {
            if (combo.getItemAt(i).toString().equals(antes.toString())) {
                combo.setSelectedIndex(i);
                return;
            }
        }
        combo.setSelectedIndex(-1);
    }

    /** Muestra cuántas unidades hay del producto en la bodega elegida. */
    protected void mostrarExistencia(JComboBox<Producto> producto, JComboBox<Bodega> bodega, JLabel etiqueta) {
        Producto p = (Producto) producto.getSelectedItem();
        Bodega b = (Bodega) bodega.getSelectedItem();
        if (p == null || b == null) {
            etiqueta.setText("Seleccione el producto y la bodega");
            etiqueta.setForeground(Tema.TEXTO_SUAVE);
            return;
        }
        try {
            int cantidad = 0;
            List<Existencia> lista = reportes.consultarExistencias(p.getCodigo(), null, b.getIdBodega(), false);
            for (Existencia e : lista) {
                if (e.getProducto().getIdProducto() == p.getIdProducto()) {
                    cantidad = e.getCantidad();
                }
            }
            etiqueta.setText(cantidad + " " + p.getUnidadMedida().toLowerCase() + "(s) disponibles en " + b.getNombre());
            etiqueta.setForeground(cantidad > 0 ? Tema.VERDE : Tema.ROJO);
        } catch (NegocioException ex) {
            etiqueta.setText("No se pudo consultar la existencia: " + ex.getMessage());
            etiqueta.setForeground(Tema.ROJO);
        }
    }

    /** Convierte la cantidad escrita en un entero mayor que cero. */
    protected static int leerCantidad(String texto) {
        try {
            int cantidad = Integer.parseInt(texto.trim());
            if (cantidad <= 0) {
                throw new NumberFormatException();
            }
            return cantidad;
        } catch (NumberFormatException e) {
            throw new NegocioException("La cantidad debe ser un número entero mayor que cero.");
        }
    }

    /** Ejecuta una acción y muestra el mensaje si el controlador la rechaza. */
    protected void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    /** Texto vacío se guarda como null (columna opcional en la base). */
    protected static String textoOpcional(String texto) {
        return texto == null || texto.isBlank() ? null : texto.trim();
    }
}
