package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.controlador.ProductoControlador;
import com.aristools.controlador.ProveedorControlador;
import com.aristools.controlador.ReporteControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.CambioStock;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.GridLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Kardex y reportes de movimientos (RF-014). El kardex cuenta solo
 * movimientos autorizados y calcula el saldo (saldo anterior + entradas −
 * salidas). Cada rol ve solo los movimientos de su alcance: el controlador
 * recorta los datos según el usuario conectado.
 */
public class ReportesPanel extends JPanel implements Recargable {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private final ReporteControlador reportes = new ReporteControlador();
    private final ProductoControlador productos = new ProductoControlador();
    private final BodegaControlador bodegas = new BodegaControlador();
    private final ProveedorControlador proveedores = new ProveedorControlador();

    // Pestaña Kardex
    private final JComboBox<Producto> kProducto = new JComboBox<>();
    private final JComboBox<Object> kBodega = new JComboBox<>();
    private final JTextField kDesde = campoFecha();
    private final JTextField kHasta = campoFecha();
    private final JLabel kResumen = new JLabel(" ");
    private final DefaultTableModel kModelo = modelo("Fecha", "Tipo", "Documento", "Bodega", "Entrada",
            "Salida", "Saldo", "Registró", "Autorizó");

    // Pestaña Movimientos
    private final JComboBox<Object> mProducto = new JComboBox<>();
    private final JComboBox<Object> mBodega = new JComboBox<>();
    private final JComboBox<Object> mProveedor = new JComboBox<>();
    private final JComboBox<Object> mTipo = new JComboBox<>();
    private final JTextField mDesde = campoFecha();
    private final JTextField mHasta = campoFecha();
    private final JLabel mResumen = new JLabel(" ");
    private final DefaultTableModel mModelo = modelo("Fecha", "Tipo", "Producto", "Bodega", "Cantidad",
            "Estado", "Documento", "Registró", "Autorizó");

    public ReportesPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Reportes"));
        textos.add(Tema.subtitulo("Kardex de un producto y movimientos con filtros. Las fechas son opcionales (AAAA-MM-DD)."));
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setFont(Tema.NEGRITA);
        pestanas.addTab("Kardex", crearKardex());
        pestanas.addTab("Movimientos", crearMovimientos());
        add(textos, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
    }

    // ---------- Construcción de las pestañas ----------

    private JPanel crearKardex() {
        JButton generar = Tema.botonPrincipal("Generar kardex");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        generar.addActionListener(e -> generarKardex());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, kModelo, "kardex")));
        BarraFiltros filtros = new BarraFiltros(2);
        filtros.agregar("Producto:", kProducto);
        filtros.agregar("Bodega:", kBodega);
        filtros.agregar("Desde:", kDesde);
        filtros.agregar("Hasta:", kHasta);
        filtros.agregarBotones(generar, exportar);
        return pestana(filtros, tabla(kModelo, new int[]{120, 150, 140, 140, 70, 70, 70, 90, 90}), kResumen);
    }

    private JPanel crearMovimientos() {
        JButton consultar = Tema.botonPrincipal("Consultar");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        consultar.addActionListener(e -> consultarMovimientos());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, mModelo, "movimientos")));
        BarraFiltros filtros = new BarraFiltros(3);
        filtros.agregar("Producto:", mProducto);
        filtros.agregar("Bodega:", mBodega);
        filtros.agregar("Proveedor:", mProveedor);
        filtros.agregar("Tipo:", mTipo);
        filtros.agregar("Desde:", mDesde);
        filtros.agregar("Hasta:", mHasta);
        filtros.agregarBotones(consultar, exportar);
        return pestana(filtros, tabla(mModelo, new int[]{120, 150, 230, 180, 70, 100, 130, 90, 90}), mResumen);
    }

    /** Carga los combos de filtros con los datos actuales. */
    @Override
    public void recargar() {
        ejecutar(() -> {
            Object productoKardex = kProducto.getSelectedItem();
            kProducto.removeAllItems();
            mProducto.removeAllItems();
            mProducto.addItem("Todos");
            for (Producto p : productos.listarProductos("", true)) {
                kProducto.addItem(p);
                mProducto.addItem(p);
                if (productoKardex != null && p.toString().equals(productoKardex.toString())) {
                    kProducto.setSelectedItem(p);
                }
            }
            for (JComboBox<Object> combo : List.of(kBodega, mBodega)) {
                combo.removeAllItems();
                combo.addItem("Todas");
                for (Bodega b : bodegas.listar(true)) {
                    combo.addItem(b);
                }
            }
            mProveedor.removeAllItems();
            mProveedor.addItem("Todos");
            for (Proveedor p : proveedores.listar(true)) {
                mProveedor.addItem(p);
            }
            mTipo.removeAllItems();
            mTipo.addItem("Todos");
            for (TipoMovimiento t : reportes.listarTiposReporte()) {   // solo los tipos del alcance del rol
                mTipo.addItem(t);
            }
        });
    }

    // ---------- Consultas ----------

    private void generarKardex() {
        ejecutar(() -> {
            Producto p = (Producto) kProducto.getSelectedItem();
            if (p == null) {
                throw new NegocioException("Seleccione el producto del kardex.");
            }
            Integer idBodega = kBodega.getSelectedItem() instanceof Bodega b ? b.getIdBodega() : null;
            List<LineaKardex> lineas = reportes.generarKardex(p.getIdProducto(), idBodega,
                    fecha(kDesde, "Desde"), fecha(kHasta, "Hasta"));
            kModelo.setRowCount(0);
            for (LineaKardex l : lineas) {
                kModelo.addRow(new Object[]{l.getFecha() == null ? "" : l.getFecha().format(FECHA_HORA),
                    l.getTipo(), vacio(l.getDocumento()), vacio(l.getBodega()), l.getEntrada(), l.getSalida(),
                    l.getSaldo(), vacio(l.getRegistra()), vacio(l.getAutoriza())});
            }
            int saldoFinal = lineas.isEmpty() ? 0 : lineas.get(lineas.size() - 1).getSaldo();
            kResumen.setText(lineas.size() + " línea(s) · Saldo final: " + saldoFinal + " "
                    + p.getUnidadMedida().toLowerCase() + "(s)");
        });
    }

    private void consultarMovimientos() {
        ejecutar(() -> {
            FiltroMovimiento f = new FiltroMovimiento(
                    mProducto.getSelectedItem() instanceof Producto p ? p.getIdProducto() : null,
                    mBodega.getSelectedItem() instanceof Bodega b ? b.getIdBodega() : null,
                    mProveedor.getSelectedItem() instanceof Proveedor pr ? pr.getIdProveedor() : null,
                    mTipo.getSelectedItem() instanceof TipoMovimiento t ? t.getIdTipoMovimiento() : null,
                    fecha(mDesde, "Desde"), fecha(mHasta, "Hasta"));
            List<Movimiento> lista = reportes.reporteMovimientos(f);
            mModelo.setRowCount(0);
            for (Movimiento m : lista) {
                mModelo.addRow(new Object[]{m.getFecha() == null ? "" : m.getFecha().format(FECHA_HORA),
                    m.getTipo().getNombre(), m.getProducto().getNombre(), bodegas(m), m.getCantidad(),
                    m.getEstado(), vacio(m.getDocumentoSoporte()),
                    m.getUsuarioRegistra() == null ? "" : m.getUsuarioRegistra().getNombreUsuario(),
                    m.getUsuarioAutoriza() == null ? "—" : m.getUsuarioAutoriza().getNombreUsuario()});
            }
            mResumen.setText(lista.size() + " movimiento(s) encontrados");
        });
    }

    /** Bodegas afectadas, con polimorfismo: una transferencia muestra "origen → destino". */
    private static String bodegas(Movimiento m) {
        List<CambioStock> cambios = m.calcularCambiosStock();
        if (cambios.size() == 2) {
            return cambios.get(0).getBodega().getNombre() + " → " + cambios.get(1).getBodega().getNombre();
        }
        return cambios.isEmpty() || cambios.get(0).getBodega() == null ? "" : cambios.get(0).getBodega().getNombre();
    }

    // ---------- Utilidades ----------

    /** Lee una fecha opcional: acepta 2026-10-01 o 01/10/2026; vacía = sin límite. */
    private static LocalDate fecha(JTextField campo, String nombre) {
        String texto = campo.getText().trim();
        if (texto.isEmpty()) {
            return null;
        }
        try {
            return texto.contains("/")
                    ? LocalDate.parse(texto, DateTimeFormatter.ofPattern("d/M/yyyy"))
                    : LocalDate.parse(texto);
        } catch (DateTimeParseException e) {
            throw new NegocioException("La fecha '" + nombre + "' no es válida. Escríbala como AAAA-MM-DD "
                    + "(por ejemplo, 2026-10-01) o DD/MM/AAAA.");
        }
    }

    private static String vacio(String texto) {
        return texto == null || texto.isBlank() ? "—" : texto;
    }

    private void ejecutar(Runnable accion) {
        try {
            accion.run();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private static JTextField campoFecha() {
        JTextField t = new JTextField(9);
        t.setFont(Tema.NORMAL);
        t.putClientProperty("JTextField.placeholderText", "AAAA-MM-DD");
        return t;
    }

    private static DefaultTableModel modelo(String... columnas) {
        return new DefaultTableModel(columnas, 0) {
            @Override
            public boolean isCellEditable(int f, int c) {
                return false;
            }
        };
    }

    private static JScrollPane tabla(DefaultTableModel modelo, int[] anchos) {
        JTable t = new JTable(modelo);
        Tema.estilizarTabla(t);
        for (int i = 0; i < anchos.length; i++) {
            t.getColumnModel().getColumn(i).setPreferredWidth(anchos[i]);
        }
        JScrollPane s = new JScrollPane(t);
        s.setBorder(BorderFactory.createLineBorder(Tema.BORDE));
        s.setPreferredSize(new Dimension(900, 320));
        return s;
    }

    private static JPanel pestana(JPanel filtros, JScrollPane tabla, JLabel resumen) {
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(12, 4, 4, 4));
        resumen.setFont(Tema.NEGRITA);
        p.add(filtros, BorderLayout.NORTH);
        p.add(tabla, BorderLayout.CENTER);
        p.add(resumen, BorderLayout.SOUTH);
        return p;
    }
}
