package com.aristools.vista;

import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.table.DefaultTableModel;

/**
 * Plantilla común de las pantallas de catálogos (usuarios, productos,
 * categorías, bodegas y proveedores). Es una clase abstracta: define la
 * estructura que comparten todas (buscador, tabla a la izquierda, formulario
 * a la derecha y botones Nuevo, Guardar y Activar/Inactivar) y cada subclase
 * solo indica sus columnas, sus campos y qué controlador usar.
 *
 * Así se aplica herencia y se evita repetir el mismo código cinco veces.
 *
 * @param <T> tipo de objeto del catálogo (Producto, Bodega, Proveedor...)
 */
public abstract class PanelCatalogo<T> extends JPanel implements Recargable {

    protected final JTextField txtBuscar = new JTextField(18);
    protected final JCheckBox chkInactivos = new JCheckBox("Ver inactivos");
    protected final JButton btnNuevo = Tema.botonSecundario("Nuevo");
    protected final JButton btnGuardar = Tema.botonPrincipal("Guardar");
    protected final JButton btnEstado = Tema.botonSecundario("Inactivar");
    protected final JPanel panelBotonesExtra = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));

    private final String titulo;
    private final String subtitulo;
    private DefaultTableModel modelo;
    private JTable tabla;
    private final JPanel formulario = new JPanel(new GridBagLayout());
    private final JLabel lblModo = new JLabel();
    private int filaFormulario = 0;
    private List<T> datos = new ArrayList<>();
    protected T seleccionado;   // null = se está creando uno nuevo

    protected PanelCatalogo(String titulo, String subtitulo) {
        this.titulo = titulo;
        this.subtitulo = subtitulo;
    }

    /**
     * Arma la pantalla. Cada subclase lo llama al FINAL de su constructor,
     * cuando sus campos ya existen (si se llamara desde aquí, los campos de
     * la subclase todavía serían null).
     */
    protected final void construir() {
        setLayout(new BorderLayout(0, 14));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

        modelo = new DefaultTableModel(columnas(), 0) {
            @Override
            public boolean isCellEditable(int fila, int columna) {
                return false;
            }
        };
        tabla = new JTable(modelo);
        Tema.estilizarTabla(tabla);
        tabla.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        int[] anchos = anchosColumnas();
        for (int i = 0; anchos != null && i < anchos.length; i++) {
            tabla.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        tabla.getSelectionModel().addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting() && tabla.getSelectedRow() >= 0) {
                seleccionar(datos.get(tabla.getSelectedRow()));
            }
        });

        add(crearEncabezado(), BorderLayout.NORTH);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        add(scroll, BorderLayout.CENTER);
        add(crearPanelFormulario(), BorderLayout.EAST);

        btnNuevo.addActionListener(e -> nuevo());
        btnGuardar.addActionListener(e -> ejecutar(() -> {
            guardar();
            Tema.info(this, "Datos guardados correctamente.");
            recargar();
        }));
        btnEstado.addActionListener(e -> {
            if (seleccionado == null) {
                Tema.error(this, "Seleccione un registro de la tabla.");
                return;
            }
            ejecutar(() -> {
                cambiarEstado(seleccionado);
                recargar();
            });
        });
        txtBuscar.addActionListener(e -> recargar());
        chkInactivos.addActionListener(e -> recargar());
    }

    // ---------- Lo que define cada subclase ----------

    /** Encabezados de la tabla. */
    protected abstract String[] columnas();

    /** Valores de una fila de la tabla para el objeto recibido. */
    protected abstract Object[] fila(T objeto);

    /** Consulta los datos al controlador. */
    protected abstract List<T> consultar(String filtro, boolean inactivos);

    /** Agrega los campos del formulario con agregarCampo(...). */
    protected abstract void construirFormulario();

    /** Copia los datos del objeto seleccionado al formulario. */
    protected abstract void mostrar(T objeto);

    /** Deja el formulario vacío para crear un registro nuevo. */
    protected abstract void limpiar();

    /** Lee el formulario y lo envía al controlador (crear o actualizar). */
    protected abstract void guardar();

    /** Activa o inactiva el registro (eliminación lógica). */
    protected abstract void cambiarEstado(T objeto);

    /** @return true si el registro está activo (para el texto del botón). */
    protected abstract boolean estaActivo(T objeto);

    /** Anchos sugeridos de las columnas (null = repartir por igual). */
    protected int[] anchosColumnas() {
        return null;
    }

    /** Se llama cada vez que se abre la pantalla (por ejemplo, para recargar combos). */
    protected void alAbrir() {
    }

    // ---------- Comportamiento común ----------

    /** Vuelve a consultar los datos y llena la tabla. */
    @Override
    public final void recargar() {
        ejecutar(() -> {
            alAbrir();
            datos = consultar(txtBuscar.getText().trim(), chkInactivos.isSelected());
            modelo.setRowCount(0);
            for (T d : datos) {
                modelo.addRow(fila(d));
            }
            nuevo();
        });
    }

    private void nuevo() {
        seleccionado = null;
        tabla.clearSelection();
        limpiar();
        lblModo.setText("Nuevo registro");
        btnEstado.setEnabled(false);
        btnEstado.setText("Inactivar");
    }

    private void seleccionar(T objeto) {
        seleccionado = objeto;
        mostrar(objeto);
        lblModo.setText("Editando el registro seleccionado");
        btnEstado.setEnabled(true);
        btnEstado.setText(textoBotonEstado(objeto));
    }

    /** Texto del botón de estado; UsuariosPanel lo cambia para "Reactivar". */
    protected String textoBotonEstado(T objeto) {
        return estaActivo(objeto) ? "Inactivar" : "Activar";
    }

    /** Ejecuta una acción y muestra el mensaje si el controlador la rechaza. */
    protected void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    /** Agrega una fila "etiqueta: campo" al formulario. */
    protected void agregarCampo(String etiqueta, JComponent campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = filaFormulario++;
        c.insets = new Insets(5, 0, 5, 10);
        c.anchor = GridBagConstraints.WEST;
        JLabel l = new JLabel(etiqueta);
        l.setFont(Tema.NEGRITA);
        c.gridx = 0;
        formulario.add(l, c);
        c.gridx = 1;
        c.weightx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 0, 5, 0);
        campo.setFont(Tema.NORMAL);
        campo.setPreferredSize(new Dimension(220, 32));
        formulario.add(campo, c);
    }

    /** Agrega una nota de ayuda debajo de los campos. */
    protected void agregarNota(String texto) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = filaFormulario++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(8, 0, 4, 0);
        JLabel nota = new JLabel("<html><body style='width:280px'>" + texto + "</body></html>");
        nota.setFont(Tema.NORMAL.deriveFont(12f));
        nota.setForeground(Tema.TEXTO_SUAVE);
        formulario.add(nota, c);
    }

    private JPanel crearEncabezado() {
        JPanel encabezado = new JPanel(new BorderLayout());
        encabezado.setOpaque(false);
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo(titulo));
        textos.add(Tema.subtitulo(subtitulo));

        JPanel busqueda = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 8));
        busqueda.setOpaque(false);
        JButton btnBuscar = Tema.botonSecundario("Buscar");
        btnBuscar.addActionListener(e -> recargar());
        txtBuscar.setFont(Tema.NORMAL);
        txtBuscar.putClientProperty("JTextField.placeholderText", "Buscar...");
        chkInactivos.setOpaque(false);
        chkInactivos.setFont(Tema.NORMAL);
        busqueda.add(txtBuscar);
        busqueda.add(btnBuscar);
        busqueda.add(chkInactivos);

        encabezado.add(textos, BorderLayout.WEST);
        encabezado.add(busqueda, BorderLayout.EAST);
        return encabezado;
    }

    private JPanel crearPanelFormulario() {
        construirFormulario();
        JPanel tarjeta = new JPanel(new BorderLayout(0, 12));
        tarjeta.setBackground(Tema.BLANCO);
        tarjeta.setPreferredSize(new Dimension(400, 0));
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                BorderFactory.createEmptyBorder(16, 18, 16, 18)));
        lblModo.setFont(Tema.NEGRITA.deriveFont(15f));
        formulario.setOpaque(false);

        JPanel botones = new JPanel(new GridLayout(0, 1, 0, 8));
        botones.setOpaque(false);
        JPanel fila1 = new JPanel(new GridLayout(1, 2, 8, 0));
        fila1.setOpaque(false);
        fila1.add(btnNuevo);
        fila1.add(btnGuardar);
        botones.add(fila1);
        botones.add(btnEstado);
        panelBotonesExtra.setOpaque(false);
        if (panelBotonesExtra.getComponentCount() > 0) {
            botones.add(panelBotonesExtra);
        }

        JPanel arriba = new JPanel(new BorderLayout(0, 10));
        arriba.setOpaque(false);
        arriba.add(lblModo, BorderLayout.NORTH);
        arriba.add(formulario, BorderLayout.CENTER);
        tarjeta.add(arriba, BorderLayout.NORTH);
        tarjeta.add(botones, BorderLayout.SOUTH);

        JPanel contenedor = new JPanel(new BorderLayout());
        contenedor.setOpaque(false);
        contenedor.setBorder(BorderFactory.createEmptyBorder(0, 16, 0, 0));
        contenedor.add(tarjeta, BorderLayout.CENTER);
        return contenedor;
    }

    /** Convierte un texto en entero; vacío devuelve null (para umbrales opcionales). */
    protected static Integer enteroOpcional(String texto, String campo) {
        if (texto == null || texto.isBlank()) {
            return null;
        }
        try {
            return Integer.valueOf(texto.trim());
        } catch (NumberFormatException e) {
            throw new NegocioException("El campo " + campo + " debe ser un número entero.");
        }
    }

    /** Filtro sencillo en memoria para catálogos pequeños. */
    protected static boolean contiene(String filtro, String... valores) {
        if (filtro == null || filtro.isBlank()) {
            return true;
        }
        String f = filtro.toLowerCase();
        for (String v : valores) {
            if (v != null && v.toLowerCase().contains(f)) {
                return true;
            }
        }
        return false;
    }
}
