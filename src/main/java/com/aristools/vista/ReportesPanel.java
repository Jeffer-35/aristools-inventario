package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.controlador.ProductoControlador;
import com.aristools.controlador.ProveedorControlador;
import com.aristools.controlador.ReporteControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.CambioStock;
import com.aristools.modelo.EstadoMovimiento;
import com.aristools.modelo.FiltroMovimiento;
import com.aristools.modelo.LineaKardex;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Producto;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.modelo.TotalPerdida;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Permisos;
import com.aristools.seguridad.Sesion;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridLayout;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTabbedPane;
import javax.swing.JTable;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.DefaultTableModel;

/**
 * Kardex y reportes de movimientos (RF-014). El kardex cuenta solo
 * movimientos autorizados y calcula el saldo (saldo anterior + entradas −
 * salidas). Cada rol ve solo los movimientos de su alcance: el controlador
 * recorta los datos según el usuario conectado.
 *
 * El kardex es valorizado: muestra el costo unitario de cada movimiento, el
 * valor de lo que entra y sale y el valor del saldo (costo promedio ponderado).
 *
 * Desde la pestaña Movimientos se imprime el documento de soporte de un
 * movimiento autorizado. La pestaña Pérdidas (Supervisor y Administrador)
 * muestra cuánto se perdió en el período, con totales por causa y por bodega.
 */
public class ReportesPanel extends JPanel implements Recargable {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");
    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final ReporteControlador reportes = new ReporteControlador();
    private final ProductoControlador productos = new ProductoControlador();
    private final BodegaControlador bodegas = new BodegaControlador();
    private final ProveedorControlador proveedores = new ProveedorControlador();

    // Pestaña Kardex
    private final JComboBox<Producto> kProducto = new JComboBox<>();
    private final JComboBox<Object> kBodega = new JComboBox<>();
    private final CampoFecha kDesde = new CampoFecha();
    private final CampoFecha kHasta = new CampoFecha();
    private final JLabel kResumen = new JLabel(" ");
    private List<String> kDetalles = new ArrayList<>();   // filtros del último kardex generado (para el PDF)
    private final DefaultTableModel kModelo = modelo("Fecha", "Tipo", "Documento", "Bodega", "Entrada",
            "Salida", "Saldo", "Costo unit.", "Valor entrada", "Valor salida", "Valor saldo", "Registró", "Autorizó");

    // Pestaña Movimientos
    private final JComboBox<Object> mProducto = new JComboBox<>();
    private final JComboBox<Object> mBodega = new JComboBox<>();
    private final JComboBox<Object> mProveedor = new JComboBox<>();
    private final JComboBox<Object> mTipo = new JComboBox<>();
    private final CampoFecha mDesde = new CampoFecha();
    private final CampoFecha mHasta = new CampoFecha();
    private final JLabel mResumen = new JLabel(" ");
    private List<String> mDetalles = new ArrayList<>();   // filtros de la última consulta (para el PDF)
    private List<Movimiento> mLista = new ArrayList<>();  // movimientos de la última consulta (misma posición que la tabla)
    private JTable mTabla;
    private final DefaultTableModel mModelo = modelo("N.º documento", "Fecha", "Tipo", "Producto", "Bodega", "Cantidad",
            "Estado", "Factura o remisión", "Registró", "Autorizó");

    // Pestaña Pérdidas
    private final JComboBox<Object> pBodega = new JComboBox<>();
    private final CampoFecha pDesde = new CampoFecha();
    private final CampoFecha pHasta = new CampoFecha();
    private final JLabel pResumen = new JLabel(" ");
    private List<String> pDetalles = new ArrayList<>();
    private final DefaultTableModel pModelo = modelo("Fecha", "N.º documento", "Tipo", "Producto", "Bodega", "Causa",
            "Cantidad", "Costo unit.", "Valor perdido", "Registró", "Autorizó");
    private final DefaultTableModel pTotales = modelo("Totales", "Nombre", "Bajas", "Unidades", "Valor perdido");

    public ReportesPanel() {
        setLayout(new BorderLayout(0, 10));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 12, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Reportes"));
        textos.add(Tema.subtitulo("Kardex valorizado, movimientos y pérdidas. Las fechas son opcionales: elíjalas en el calendario."));
        JTabbedPane pestanas = new JTabbedPane();
        pestanas.setFont(Tema.NEGRITA);
        pestanas.addTab("Kardex", crearKardex());
        pestanas.addTab("Movimientos", crearMovimientos());
        if (Permisos.puede(Sesion.getUsuario().getRol(), Accion.VER_PERDIDAS)) {
            pestanas.addTab("Pérdidas", crearPerdidas());   // solo Supervisor y Administrador
        }
        add(textos, BorderLayout.NORTH);
        add(pestanas, BorderLayout.CENTER);
    }

    // ---------- Construcción de las pestañas ----------

    private JPanel crearKardex() {
        JButton generar = Tema.botonPrincipal("Generar kardex");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        generar.addActionListener(e -> generarKardex());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, kModelo, "kardex")));
        JButton imprimir = Tema.botonSecundario("Imprimir PDF");
        imprimir.addActionListener(e -> ejecutar(() -> ExportadorPdf.exportar(this, kModelo, "Kardex valorizado",
                kDetalles, kResumen.getText(), "kardex")));
        BarraFiltros filtros = new BarraFiltros(2);
        filtros.agregar("Producto:", kProducto);
        filtros.agregar("Bodega:", kBodega);
        filtros.agregar("Desde:", kDesde);
        filtros.agregar("Hasta:", kHasta);
        filtros.agregar("Período:", CampoFecha.accesosRapidos(kDesde, kHasta));
        filtros.agregarBotones(generar, exportar, imprimir);
        JScrollPane scroll = tabla(kModelo, new int[]{120, 140, 170, 130, 65, 65, 65, 100, 115, 115, 125, 85, 85});
        resaltarSaldoInicial((JTable) scroll.getViewport().getView());
        return pestana(filtros, scroll, kResumen);
    }

    private JPanel crearMovimientos() {
        JButton consultar = Tema.botonPrincipal("Consultar");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        consultar.addActionListener(e -> consultarMovimientos());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, mModelo, "movimientos")));
        JButton imprimir = Tema.botonSecundario("Imprimir PDF");
        imprimir.addActionListener(e -> ejecutar(() -> ExportadorPdf.exportar(this, mModelo, "Reporte de movimientos",
                mDetalles, mResumen.getText(), "movimientos")));
        BarraFiltros filtros = new BarraFiltros(3);
        filtros.agregar("Producto:", mProducto);
        filtros.agregar("Bodega:", mBodega);
        filtros.agregar("Proveedor:", mProveedor);
        filtros.agregar("Tipo:", mTipo);
        filtros.agregar("Desde:", mDesde);
        filtros.agregar("Hasta:", mHasta);
        filtros.agregar("Período:", CampoFecha.accesosRapidos(mDesde, mHasta));
        JButton documento = Tema.botonSecundario("Documento PDF");
        documento.setToolTipText("Documento de soporte del movimiento seleccionado en la tabla");
        documento.addActionListener(e -> imprimirDocumento());
        filtros.agregarBotones(consultar, exportar, imprimir, documento);
        JScrollPane scroll = tabla(mModelo, new int[]{105, 120, 150, 220, 170, 70, 100, 130, 90, 90});
        mTabla = (JTable) scroll.getViewport().getView();
        return pestana(filtros, scroll, mResumen);
    }

    private JPanel crearPerdidas() {
        JButton consultar = Tema.botonPrincipal("Consultar");
        JButton exportar = Tema.botonSecundario("Exportar a Excel (CSV)");
        JButton imprimir = Tema.botonSecundario("Imprimir PDF");
        consultar.addActionListener(e -> consultarPerdidas());
        exportar.addActionListener(e -> ejecutar(() -> ExportadorCsv.exportar(this, pModelo, "perdidas")));
        imprimir.addActionListener(e -> ejecutar(() -> ExportadorPdf.exportar(this, pModelo, "Reporte de pérdidas",
                pDetalles, pResumen.getText(), "perdidas")));
        BarraFiltros filtros = new BarraFiltros(3);
        filtros.agregar("Bodega:", pBodega);
        filtros.agregar("Desde:", pDesde);
        filtros.agregar("Hasta:", pHasta);
        filtros.agregar("Período:", CampoFecha.accesosRapidos(pDesde, pHasta));
        filtros.agregarBotones(consultar, exportar, imprimir);
        // Arriba el detalle de cada baja; abajo los totales por causa y por bodega
        JScrollPane detalle = tabla(pModelo, new int[]{120, 105, 150, 220, 140, 170, 70, 100, 110, 90, 90});
        JScrollPane totales = tabla(pTotales, new int[]{110, 260, 80, 90, 130});
        totales.setPreferredSize(new Dimension(900, 150));
        JSplitPane division = new JSplitPane(JSplitPane.VERTICAL_SPLIT, detalle, totales);
        division.setResizeWeight(0.65);
        division.setBorder(null);
        JPanel p = new JPanel(new BorderLayout(0, 10));
        p.setBackground(Tema.FONDO);
        p.setBorder(BorderFactory.createEmptyBorder(12, 4, 4, 4));
        pResumen.setFont(Tema.NEGRITA);
        p.add(filtros, BorderLayout.NORTH);
        p.add(division, BorderLayout.CENTER);
        p.add(pResumen, BorderLayout.SOUTH);
        return p;
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
            for (JComboBox<Object> combo : List.of(kBodega, mBodega, pBodega)) {
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
                if ("Saldo inicial".equals(l.getTipo())) {
                    // Resume todo lo anterior a la fecha "Desde": solo importan el saldo y su valor
                    kModelo.addRow(new Object[]{l.getFecha().format(FECHA_HORA), "Saldo inicial",
                        "Acumulado antes del " + l.getFecha().format(FECHA), "—", "—", "—", l.getSaldo(), "—", "—", "—",
                        Formato.pesos(l.getValorSaldo()), "—", "—"});
                    continue;
                }
                kModelo.addRow(new Object[]{l.getFecha() == null ? "" : l.getFecha().format(FECHA_HORA),
                    l.getTipo(), vacio(l.getDocumento()), vacio(l.getBodega()), l.getEntrada(), l.getSalida(),
                    l.getSaldo(), Formato.pesos(l.getCostoUnitario()), pesosSiHay(l.getEntrada(), l.getValorEntrada()),
                    pesosSiHay(l.getSalida(), l.getValorSalida()), Formato.pesos(l.getValorSaldo()),
                    vacio(l.getRegistra()), vacio(l.getAutoriza())});
            }
            int saldoFinal = lineas.isEmpty() ? 0 : lineas.get(lineas.size() - 1).getSaldo();
            BigDecimal valorFinal = lineas.isEmpty() ? BigDecimal.ZERO : lineas.get(lineas.size() - 1).getValorSaldo();
            // Costo promedio = valor del saldo / unidades del saldo
            String promedio = saldoFinal > 0
                    ? Formato.pesos(valorFinal.divide(BigDecimal.valueOf(saldoFinal), 2, RoundingMode.HALF_UP)) : "—";
            kDetalles = List.of("Producto: " + p.getCodigo() + " - " + p.getNombre(),
                    "Bodega: " + kBodega.getSelectedItem() + "   ·   " + periodo(kDesde, kHasta),
                    "Método de valoración: costo promedio ponderado");
            kResumen.setText(lineas.size() + " línea(s) · Saldo final: " + saldoFinal + " "
                    + p.getUnidadMedida().toLowerCase() + "(s) · Valor del saldo: " + Formato.pesos(valorFinal)
                    + " · Costo promedio: " + promedio);
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
            mLista = lista;
            mModelo.setRowCount(0);
            for (Movimiento m : lista) {
                mModelo.addRow(new Object[]{numeroDocumento(m), m.getFecha() == null ? "" : m.getFecha().format(FECHA_HORA),
                    m.getTipo().getNombre(), m.getProducto().getNombre(), bodegas(m), m.getCantidad(),
                    m.getEstado(), vacio(m.getDocumentoSoporte()),
                    m.getUsuarioRegistra() == null ? "" : m.getUsuarioRegistra().getNombreUsuario(),
                    m.getUsuarioAutoriza() == null ? "—" : m.getUsuarioAutoriza().getNombreUsuario()});
            }
            mDetalles = List.of("Producto: " + mProducto.getSelectedItem() + "   ·   Bodega: " + mBodega.getSelectedItem(),
                    "Proveedor: " + mProveedor.getSelectedItem() + "   ·   Tipo: " + mTipo.getSelectedItem()
                    + "   ·   " + periodo(mDesde, mHasta));
            mResumen.setText(lista.size() + " movimiento(s) encontrados");
        });
    }

    /** Genera el documento de soporte (PDF) del movimiento seleccionado en la tabla. */
    private void imprimirDocumento() {
        ejecutar(() -> {
            int fila = mTabla.getSelectedRow();
            if (fila < 0 || fila >= mLista.size()) {
                throw new NegocioException("Seleccione un movimiento de la tabla.");
            }
            // Se vuelve a leer de la base para imprimir el estado actual del movimiento
            ExportadorPdf.documento(this, reportes.movimientoParaDocumento(mLista.get(fila).getIdMovimiento()));
        });
    }

    private void consultarPerdidas() {
        ejecutar(() -> {
            Integer idBodega = pBodega.getSelectedItem() instanceof Bodega b ? b.getIdBodega() : null;
            List<Movimiento> lista = reportes.reportePerdidas(fecha(pDesde, "Desde"), fecha(pHasta, "Hasta"), idBodega);
            pModelo.setRowCount(0);
            int unidades = 0;
            BigDecimal total = BigDecimal.ZERO;
            for (Movimiento m : lista) {
                LocalDateTime cuando = m.getFechaAutorizacion() != null ? m.getFechaAutorizacion() : m.getFecha();
                pModelo.addRow(new Object[]{cuando.format(FECHA_HORA), m.numeroDocumento(), m.getTipo().getNombre(),
                    m.getProducto().getNombre(), bodegas(m), causa(m), m.getCantidad(),
                    Formato.pesos(m.getCostoUnitario()), Formato.pesos(m.valorTotal()),
                    m.getUsuarioRegistra().getNombreUsuario(),
                    m.getUsuarioAutoriza() == null ? "—" : m.getUsuarioAutoriza().getNombreUsuario()});
                unidades += m.getCantidad();
                total = total.add(m.valorTotal());
            }
            // Totales por causa y por bodega (de mayor a menor valor)
            pTotales.setRowCount(0);
            List<String> porCausa = new ArrayList<>();
            for (TotalPerdida t : TotalPerdida.agrupar(lista, ReportesPanel::causa)) {
                pTotales.addRow(new Object[]{"Por causa", t.getNombre(), t.getBajas(), t.getUnidades(), Formato.pesos(t.getValor())});
                porCausa.add(t.getNombre() + " " + Formato.pesos(t.getValor()));
            }
            List<String> porBodega = new ArrayList<>();
            for (TotalPerdida t : TotalPerdida.agrupar(lista, ReportesPanel::bodegas)) {
                pTotales.addRow(new Object[]{"Por bodega", t.getNombre(), t.getBajas(), t.getUnidades(), Formato.pesos(t.getValor())});
                porBodega.add(t.getNombre() + " " + Formato.pesos(t.getValor()));
            }
            pDetalles = List.of("Bodega: " + pBodega.getSelectedItem() + "   ·   " + periodo(pDesde, pHasta),
                    "Incluye mermas, productos dañados o vencidos y faltantes del conteo ya autorizados.",
                    "Por causa: " + (porCausa.isEmpty() ? "—" : String.join("  |  ", porCausa)),
                    "Por bodega: " + (porBodega.isEmpty() ? "—" : String.join("  |  ", porBodega)));
            pResumen.setText(lista.size() + " baja(s) · " + unidades + " unidad(es) · Valor perdido: " + Formato.pesos(total));
        });
    }

    // Causa estándar de una pérdida; las registradas antes de existir la lista no tienen.
    private static String causa(Movimiento m) {
        return m.getCausaBaja() == null ? "Sin clasificar" : m.getCausaBaja().getDescripcion();
    }

    // El documento de soporte existe cuando el movimiento ya está autorizado.
    private static String numeroDocumento(Movimiento m) {
        return m.getEstado() == EstadoMovimiento.AUTORIZADO ? m.numeroDocumento() : "—";
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

    /** Fecha opcional elegida en el calendario; vacía = sin límite. */
    private static LocalDate fecha(CampoFecha campo, String nombre) {
        return campo.getFecha(nombre);
    }

    // Texto del período consultado, para el encabezado del PDF.
    private static String periodo(CampoFecha desde, CampoFecha hasta) {
        String d = desde.texto();
        String h = hasta.texto();
        return "Período: " + (d.isEmpty() ? "desde el inicio" : "desde " + d) + (h.isEmpty() ? " hasta hoy" : " hasta " + h);
    }

    /** La fila de "Saldo inicial" va en cursiva y gris, para que se vea que es un resumen y no un movimiento. */
    private static void resaltarSaldoInicial(JTable tabla) {
        DefaultTableCellRenderer render = new DefaultTableCellRenderer() {
            @Override
            public Component getTableCellRendererComponent(JTable t, Object valor, boolean seleccionada,
                                                           boolean foco, int fila, int columna) {
                Component c = super.getTableCellRendererComponent(t, valor, seleccionada, foco, fila, columna);
                boolean saldoInicial = "Saldo inicial".equals(t.getModel().getValueAt(fila, 1));
                c.setFont(saldoInicial ? Tema.NORMAL.deriveFont(Font.ITALIC) : Tema.NORMAL);
                if (!seleccionada) {
                    c.setForeground(saldoInicial ? Tema.TEXTO_SUAVE : Tema.TEXTO);
                }
                return c;
            }
        };
        for (int i = 0; i < tabla.getColumnCount(); i++) {
            tabla.getColumnModel().getColumn(i).setCellRenderer(render);
        }
    }

    // En el kardex solo se muestra el valor cuando hubo unidades en esa columna.
    private static String pesosSiHay(int unidades, BigDecimal valor) {
        return unidades == 0 ? "—" : Formato.pesos(valor);
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
        if (anchos.length > 10) {
            t.setAutoResizeMode(JTable.AUTO_RESIZE_OFF);   // muchas columnas: barra de desplazamiento horizontal
        }
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
