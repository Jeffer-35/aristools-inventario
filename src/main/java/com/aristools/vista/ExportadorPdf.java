package com.aristools.vista;

import com.aristools.controlador.ParametroControlador;
import com.aristools.modelo.CambioStock;
import com.aristools.modelo.Entrada;
import com.aristools.modelo.Movimiento;
import com.aristools.modelo.Proveedor;
import com.aristools.modelo.Salida;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.Sesion;
import com.aristools.util.Formato;
import com.aristools.util.NegocioException;
import com.aristools.util.ReportePdf;
import com.lowagie.text.DocumentException;
import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;

/**
 * Botones "Imprimir PDF" y "Documento PDF": piden dónde guardar, generan el
 * archivo (lo arma la clase ReportePdf) y lo abren para verlo o enviarlo a
 * la impresora.
 *
 * En los reportes, "Elaboró" es el usuario con la sesión abierta y "Revisó"
 * es el responsable definido en Configuración. En el documento de soporte
 * de un movimiento, "Elaboró" es quien lo registró y "Revisó" quien lo autorizó.
 */
public final class ExportadorPdf {

    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ExportadorPdf() {
    }

    /**
     * Reporte con lo que muestra una tabla de la pantalla.
     *
     * @param titulo         título del reporte
     * @param detalles       filtros usados en la consulta (se imprimen bajo el título)
     * @param resumen        línea de totales (null si no hay)
     * @param nombreSugerido nombre del archivo, sin extensión
     */
    public static void exportar(Component padre, TableModel modelo, String titulo, List<String> detalles,
                                String resumen, String nombreSugerido) {
        if (modelo.getRowCount() == 0) {
            Tema.error(padre, "No hay datos para imprimir. Primero genere la consulta.");
            return;
        }
        File archivo = elegirArchivo(padre, nombreSugerido);
        if (archivo == null) {
            return;
        }
        // Pasa la tabla de la pantalla a textos
        String[] columnas = new String[modelo.getColumnCount()];
        for (int c = 0; c < columnas.length; c++) {
            columnas[c] = modelo.getColumnName(c);
        }
        List<String[]> filas = new ArrayList<>();
        for (int f = 0; f < modelo.getRowCount(); f++) {
            String[] fila = new String[columnas.length];
            for (int c = 0; c < columnas.length; c++) {
                Object valor = modelo.getValueAt(f, c);
                fila[c] = valor == null ? "" : valor.toString();
            }
            filas.add(fila);
        }
        ParametroControlador parametros = new ParametroControlador();
        try (OutputStream salida = new FileOutputStream(archivo)) {
            ReportePdf.generar(salida, parametros.valor("nombre_establecimiento", "Ferretería Aristools"), titulo,
                    detalles, columnas, filas, resumen, nombreYRol(Sesion.getUsuario()),
                    parametros.valor(ParametroControlador.RESPONSABLE_REVISION, ""));
        } catch (IOException | DocumentException e) {
            throw new NegocioException(errorAlGuardar());
        }
        abrir(padre, archivo);
    }

    /**
     * Documento de soporte de un movimiento autorizado: comprobante de
     * entrada, vale de salida, acta de baja, comprobante de traslado o acta
     * de ajuste. El título y el número los da el propio movimiento
     * (polimorfismo), así este método sirve para todos.
     */
    public static void documento(Component padre, Movimiento m) {
        File archivo = elegirArchivo(padre, m.numeroDocumento());
        if (archivo == null) {
            return;
        }
        String establecimiento = new ParametroControlador().valor("nombre_establecimiento", "Ferretería Aristools");
        String reviso = m.getUsuarioAutoriza() == null
                ? "No requiere autorización (se aplica al registrar)"
                : nombreYRol(m.getUsuarioAutoriza()) + " · " + fecha(m.getFechaAutorizacion());
        try (OutputStream salida = new FileOutputStream(archivo)) {
            ReportePdf.generarDocumento(salida, establecimiento, m.tituloDocumento(), m.numeroDocumento(),
                    campos(m), nombreYRol(m.getUsuarioRegistra()) + " · " + fecha(m.getFecha()), reviso);
        } catch (IOException | DocumentException e) {
            throw new NegocioException(errorAlGuardar());
        }
        abrir(padre, archivo);
    }

    // Datos del movimiento que se imprimen en el documento; los vacíos no se muestran.
    private static List<String[]> campos(Movimiento m) {
        List<String[]> campos = new ArrayList<>();
        campos.add(new String[]{"Tipo de movimiento", m.getTipo().getNombre()});
        campos.add(new String[]{"Estado", String.valueOf(m.getEstado())});
        campos.add(new String[]{"Fecha de registro", fecha(m.getFecha())});
        campos.add(new String[]{"Producto", m.getProducto().getCodigo() + " - " + m.getProducto().getNombre()});
        String unidad = m.getProducto().getUnidadMedida() == null ? "" : " " + m.getProducto().getUnidadMedida().toLowerCase() + "(s)";
        campos.add(new String[]{"Cantidad", m.getCantidad() + unidad});
        for (CambioStock c : m.calcularCambiosStock()) {     // polimorfismo: cada movimiento dice qué bodegas afecta
            campos.add(new String[]{c.getDelta() < 0 ? "Bodega de origen" : "Bodega de destino", c.getBodega().getNombre()});
        }
        Proveedor proveedor = m instanceof Entrada e ? e.getProveedor() : m instanceof Salida s ? s.getProveedor() : null;
        if (proveedor != null) {
            campos.add(new String[]{"Proveedor", proveedor.getNit() + " - " + proveedor.getRazonSocial()});
        }
        agregarSiHay(campos, "Factura o remisión", m.getDocumentoSoporte());
        if (m.getCausaBaja() != null) {
            campos.add(new String[]{"Causa de la baja", m.getCausaBaja().getDescripcion()});
        }
        agregarSiHay(campos, "Motivo", m.getMotivo());
        agregarSiHay(campos, "Observación", m.getObservacion());
        if (m.getCostoUnitario() != null) {
            campos.add(new String[]{"Costo unitario", Formato.pesos(m.getCostoUnitario())});
            campos.add(new String[]{m.esPerdida() ? "Valor de la pérdida" : "Valor total", Formato.pesos(m.valorTotal())});
        }
        return campos;
    }

    private static void agregarSiHay(List<String[]> campos, String etiqueta, String valor) {
        if (valor != null && !valor.isBlank()) {
            campos.add(new String[]{etiqueta, valor});
        }
    }

    private static String nombreYRol(Usuario u) {
        if (u == null) {
            return "";
        }
        String nombre = u.getNombreCompleto() == null ? u.getNombreUsuario() : u.getNombreCompleto();
        // Si el nombre es igual al rol (usuarios de prueba) no se repite
        if (u.getRol() == null || nombre.equalsIgnoreCase(u.getRol().getNombreRol())) {
            return nombre;
        }
        return nombre + " (" + u.getRol().getNombreRol() + ")";
    }

    private static String fecha(LocalDateTime f) {
        return f == null ? "" : f.format(FECHA_HORA);
    }

    // Pregunta dónde guardar; devuelve null si el usuario cancela.
    private static File elegirArchivo(Component padre, String nombreSugerido) {
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File(nombreSugerido + ".pdf"));
        selector.setFileFilter(new FileNameExtensionFilter("Documento PDF (*.pdf)", "pdf"));
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return null;
        }
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".pdf")) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".pdf");
        }
        return archivo;
    }

    private static String errorAlGuardar() {
        return "No se pudo guardar el PDF. Verifique que el archivo no esté abierto y que tenga permiso "
                + "para escribir en esa carpeta.";
    }

    // Abre el PDF con el programa del computador; si no se puede, informa dónde quedó.
    private static void abrir(Component padre, File archivo) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.OPEN)) {
                Desktop.getDesktop().open(archivo);
                return;
            }
        } catch (IOException | RuntimeException e) {
            // no hay un programa para abrir PDF: se informa la ruta
        }
        Tema.info(padre, "PDF generado en:\n" + archivo.getAbsolutePath());
    }
}
