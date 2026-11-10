package com.aristools.vista;

import java.awt.BorderLayout;
import javax.swing.BorderFactory;
import javax.swing.JPanel;
import javax.swing.JTabbedPane;

/**
 * Catálogos del Administrador en pestañas (JTabbedPane): Productos,
 * Categorías, Bodegas y Proveedores. Al cambiar de pestaña se recargan los
 * datos; así, una categoría creada aparece de inmediato en Productos.
 */
public class CatalogosPanel extends JPanel implements Recargable {

    private final JTabbedPane pestanas = new JTabbedPane();

    public CatalogosPanel() {
        setLayout(new BorderLayout());
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(8, 8, 0, 8));
        pestanas.setFont(Tema.NEGRITA);
        pestanas.addTab("Productos", new ProductosPanel());
        pestanas.addTab("Categorías", new CategoriasPanel());
        pestanas.addTab("Bodegas", new BodegasPanel());
        pestanas.addTab("Proveedores", new ProveedoresPanel());
        pestanas.addChangeListener(e -> recargar());
        add(pestanas, BorderLayout.CENTER);
    }

    /** Recarga solo la pestaña visible. */
    @Override
    public void recargar() {
        if (pestanas.getSelectedComponent() instanceof Recargable r) {
            r.recargar();
        }
    }
}
