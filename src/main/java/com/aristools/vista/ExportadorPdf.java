package com.aristools.vista;

import com.aristools.controlador.ParametroControlador;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import com.aristools.util.ReportePdf;
import com.lowagie.text.DocumentException;
import java.awt.Component;
import java.awt.Desktop;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;

/**
 * Botón "Imprimir PDF": toma lo que muestra una tabla de la pantalla y lo
 * guarda como reporte PDF (lo arma la clase ReportePdf). Al terminar abre el
 * archivo para verlo o enviarlo a la impresora.
 *
 * "Elaboró" es el usuario que tiene la sesión abierta; "Revisó" es el
 * responsable que el Administrador definió en Configuración.
 */
public final class ExportadorPdf {

    private ExportadorPdf() {
    }

    /**
     * Pide dónde guardar, genera el PDF y lo abre.
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
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File(nombreSugerido + ".pdf"));
        selector.setFileFilter(new FileNameExtensionFilter("Documento PDF (*.pdf)", "pdf"));
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".pdf")) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".pdf");
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
        Usuario usuario = Sesion.getUsuario();
        String elaboro = usuario.getNombreCompleto() + " (" + usuario.getRol().getNombreRol() + ")";
        try (OutputStream salida = new FileOutputStream(archivo)) {
            ReportePdf.generar(salida, parametros.valor("nombre_establecimiento", "Ferretería Aristools"), titulo,
                    detalles, columnas, filas, resumen, elaboro,
                    parametros.valor(ParametroControlador.RESPONSABLE_REVISION, ""));
        } catch (IOException | DocumentException e) {
            throw new NegocioException("No se pudo guardar el PDF. Verifique que el archivo no esté abierto "
                    + "y que tenga permiso para escribir en esa carpeta.");
        }
        abrir(padre, archivo);
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
