package com.aristools.vista;

import com.aristools.util.NegocioException;
import java.awt.Component;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.TableModel;

/**
 * Exporta el contenido de una tabla a un archivo CSV que se abre en Excel
 * (separador punto y coma y BOM para que se vean bien las tildes).
 */
public final class ExportadorCsv {

    private ExportadorCsv() {
    }

    /** Pide dónde guardar y escribe el archivo. */
    public static void exportar(Component padre, TableModel modelo, String nombreSugerido) {
        if (modelo.getRowCount() == 0) {
            Tema.error(padre, "No hay datos para exportar. Primero genere la consulta.");
            return;
        }
        JFileChooser selector = new JFileChooser();
        selector.setSelectedFile(new File(nombreSugerido + ".csv"));
        selector.setFileFilter(new FileNameExtensionFilter("Archivo CSV (*.csv)", "csv"));
        if (selector.showSaveDialog(padre) != JFileChooser.APPROVE_OPTION) {
            return;
        }
        File archivo = selector.getSelectedFile();
        if (!archivo.getName().toLowerCase().endsWith(".csv")) {
            archivo = new File(archivo.getParentFile(), archivo.getName() + ".csv");
        }
        List<String> lineas = new ArrayList<>();
        StringBuilder encabezado = new StringBuilder("\uFEFF");
        for (int c = 0; c < modelo.getColumnCount(); c++) {
            encabezado.append(c > 0 ? ";" : "").append(limpiar(modelo.getColumnName(c)));
        }
        lineas.add(encabezado.toString());
        for (int f = 0; f < modelo.getRowCount(); f++) {
            StringBuilder linea = new StringBuilder();
            for (int c = 0; c < modelo.getColumnCount(); c++) {
                Object valor = modelo.getValueAt(f, c);
                linea.append(c > 0 ? ";" : "").append(limpiar(valor == null ? "" : valor.toString()));
            }
            lineas.add(linea.toString());
        }
        try {
            Files.write(archivo.toPath(), lineas, StandardCharsets.UTF_8);
            Tema.info(padre, "Reporte exportado en:\n" + archivo.getAbsolutePath());
        } catch (IOException e) {
            throw new NegocioException("No se pudo guardar el archivo. Verifique que no esté abierto en Excel.");
        }
    }

    // Evita que un punto y coma dentro de un texto parta la columna
    private static String limpiar(String texto) {
        return texto.replace(";", ",").replace("\n", " ");
    }
}
