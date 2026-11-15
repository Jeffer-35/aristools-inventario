package com.aristools.util;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.HeaderFooter;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.Rectangle;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;
import java.awt.Color;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Genera los PDF del sistema con la librería OpenPDF. Hay dos formatos:
 *   - Reporte (generar): encabezado con el nombre del establecimiento, título,
 *     filtros usados, tabla de datos, resumen y responsables.
 *   - Documento de soporte de un movimiento (generarDocumento): título, número
 *     del documento, sus datos en dos columnas y responsables.
 * En ambos van "Elaboró" y "Revisó", y cada página lleva su número.
 *
 * Esta clase solo arma el documento: no sabe de ventanas ni de la base de
 * datos. Recibe los textos ya listos y escribe el PDF donde le indiquen
 * (un archivo o la memoria), por eso se puede probar de forma automática.
 */
public final class ReportePdf {

    private static final Color AZUL = new Color(0x1F, 0x4E, 0x79);       // mismo azul de las tablas del programa
    private static final Color GRIS_TEXTO = new Color(0x5F, 0x6B, 0x76);
    private static final Color GRIS_FILA = new Color(0xF2, 0xF5, 0xF8);
    private static final Color BORDE = new Color(0xD9, 0xDE, 0xE3);
    private static final Charset LATINO = Charset.forName("Cp1252");     // tildes, ñ y signos del español
    private static final DateTimeFormatter FECHA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private ReportePdf() {
    }

    /**
     * Escribe el reporte completo.
     *
     * @param salida          dónde se escribe el PDF (quien llama la cierra)
     * @param establecimiento nombre de la ferretería (parámetro del sistema)
     * @param titulo          título del reporte, por ejemplo "Kardex valorizado"
     * @param detalles        líneas con los filtros usados (producto, bodega, fechas)
     * @param columnas        encabezados de la tabla
     * @param filas           datos de la tabla, una lista de textos por fila
     * @param resumen         línea de totales bajo la tabla (null si no hay)
     * @param elaboro         nombre y rol de quien genera el reporte
     * @param reviso          responsable de revisión definido en Configuración
     * @throws DocumentException si OpenPDF no puede armar el documento
     */
    public static void generar(OutputStream salida, String establecimiento, String titulo, List<String> detalles,
                               String[] columnas, List<String[]> filas, String resumen,
                               String elaboro, String reviso) throws DocumentException {
        // Carta horizontal cuando hay muchas columnas; vertical si son pocas
        Rectangle pagina = columnas.length > 6 ? PageSize.LETTER.rotate() : PageSize.LETTER;
        Document documento = new Document(pagina, 36, 36, 36, 44);
        PdfWriter.getInstance(documento, salida);

        // Pie de página: nombre del establecimiento y número de página
        HeaderFooter pie = new HeaderFooter(new Phrase(limpiar(establecimiento) + "  ·  Página ", fuente(8, Font.NORMAL, GRIS_TEXTO)), true);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setBorder(Rectangle.TOP);
        pie.setBorderColor(BORDE);
        documento.setFooter(pie);

        documento.open();
        documento.add(new Paragraph(limpiar(establecimiento), fuente(15, Font.BOLD, Color.BLACK)));
        documento.add(new Paragraph(limpiar(titulo), fuente(12, Font.BOLD, AZUL)));
        for (String detalle : detalles) {
            documento.add(new Paragraph(limpiar(detalle), fuente(9, Font.NORMAL, Color.BLACK)));
        }
        documento.add(new Paragraph("Generado el " + LocalDateTime.now().format(FECHA_HORA),
                fuente(9, Font.NORMAL, GRIS_TEXTO)));

        documento.add(tabla(columnas, filas));

        if (resumen != null && !resumen.isBlank()) {
            Paragraph p = new Paragraph(limpiar(resumen), fuente(10, Font.BOLD, Color.BLACK));
            p.setSpacingBefore(8);
            documento.add(p);
        }
        documento.add(responsables(elaboro, reviso));
        documento.close();
    }

    /**
     * Escribe el documento de soporte de un movimiento (comprobante de
     * entrada, vale de salida, acta de baja, comprobante de traslado o acta
     * de ajuste por conteo).
     *
     * @param titulo  nombre del documento, por ejemplo "Acta de baja"
     * @param numero  número del documento, por ejemplo BAJ-000057
     * @param campos  datos del movimiento: cada elemento es {etiqueta, valor}
     * @param elaboro quien registró el movimiento
     * @param reviso  quien lo autorizó
     */
    public static void generarDocumento(OutputStream salida, String establecimiento, String titulo, String numero,
                                        List<String[]> campos, String elaboro, String reviso)
            throws DocumentException {
        Document documento = new Document(PageSize.LETTER, 54, 54, 48, 48);
        PdfWriter.getInstance(documento, salida);
        HeaderFooter pie = new HeaderFooter(new Phrase(limpiar(establecimiento) + "  ·  " + limpiar(numero)
                + "  ·  Página ", fuente(8, Font.NORMAL, GRIS_TEXTO)), true);
        pie.setAlignment(Element.ALIGN_CENTER);
        pie.setBorder(Rectangle.TOP);
        pie.setBorderColor(BORDE);
        documento.setFooter(pie);

        documento.open();
        documento.add(new Paragraph(limpiar(establecimiento), fuente(15, Font.BOLD, Color.BLACK)));

        // Franja azul con el título a la izquierda y el número a la derecha
        PdfPTable franja = new PdfPTable(2);
        franja.setWidthPercentage(100);
        franja.setSpacingBefore(8);
        PdfPCell celdaTitulo = new PdfPCell(new Phrase(limpiar(titulo), fuente(13, Font.BOLD, Color.WHITE)));
        PdfPCell celdaNumero = new PdfPCell(new Phrase("N.º " + limpiar(numero), fuente(13, Font.BOLD, Color.WHITE)));
        celdaNumero.setHorizontalAlignment(Element.ALIGN_RIGHT);
        for (PdfPCell celda : new PdfPCell[]{celdaTitulo, celdaNumero}) {
            celda.setBackgroundColor(AZUL);
            celda.setBorderColor(AZUL);
            celda.setPadding(8);
            franja.addCell(celda);
        }
        documento.add(franja);

        // Datos del movimiento: etiqueta en negrita y valor
        PdfPTable datos = new PdfPTable(new float[]{28, 72});
        datos.setWidthPercentage(100);
        datos.setSpacingBefore(10);
        for (String[] campo : campos) {
            PdfPCell etiqueta = new PdfPCell(new Phrase(limpiar(campo[0]), fuente(10, Font.BOLD, Color.BLACK)));
            PdfPCell valor = new PdfPCell(new Phrase(limpiar(campo[1]), fuente(10, Font.NORMAL, Color.BLACK)));
            etiqueta.setBackgroundColor(GRIS_FILA);
            for (PdfPCell celda : new PdfPCell[]{etiqueta, valor}) {
                celda.setBorderColor(BORDE);
                celda.setPadding(6);
                datos.addCell(celda);
            }
        }
        documento.add(datos);

        Paragraph nota = new Paragraph("Documento interno  ·  Impreso el " + LocalDateTime.now().format(FECHA_HORA)
                + "  ·  Responsables registrados con su usuario en el sistema.",
                fuente(8, Font.NORMAL, GRIS_TEXTO));
        nota.setSpacingBefore(10);
        documento.add(nota);
        documento.add(responsables(elaboro, reviso));
        documento.close();
    }

    // Tabla de datos: el encabezado azul se repite en cada página.
    private static PdfPTable tabla(String[] columnas, List<String[]> filas) throws DocumentException {
        PdfPTable tabla = new PdfPTable(columnas.length);
        tabla.setWidthPercentage(100);
        tabla.setWidths(anchos(columnas, filas));
        tabla.setSpacingBefore(10);
        tabla.setHeaderRows(1);
        float letra = columnas.length > 10 ? 7f : 8f;   // con muchas columnas, letra un poco más pequeña
        for (String columna : columnas) {
            PdfPCell celda = new PdfPCell(new Phrase(limpiar(columna), fuente(letra, Font.BOLD, Color.WHITE)));
            celda.setBackgroundColor(AZUL);
            celda.setBorderColor(AZUL);
            celda.setPadding(5);
            tabla.addCell(celda);
        }
        boolean filaGris = false;
        for (String[] fila : filas) {
            for (String texto : fila) {
                String valor = limpiar(texto);
                PdfPCell celda = new PdfPCell(new Phrase(valor, fuente(letra, Font.NORMAL, Color.BLACK)));
                celda.setBorderColor(BORDE);
                celda.setPadding(4);
                celda.setHorizontalAlignment(esNumero(valor) ? Element.ALIGN_RIGHT : Element.ALIGN_LEFT);
                celda.setNoWrap(esNumero(valor));   // un valor en pesos no se parte en dos renglones
                if (filaGris) {
                    celda.setBackgroundColor(GRIS_FILA);
                }
                tabla.addCell(celda);
            }
            filaGris = !filaGris;
        }
        return tabla;
    }

    // Bloque final: quién elaboró y quién revisa (nombres, sin firmas manuscritas).
    private static PdfPTable responsables(String elaboro, String reviso) {
        PdfPTable tabla = new PdfPTable(2);
        tabla.setWidthPercentage(100);
        tabla.setSpacingBefore(22);
        tabla.setKeepTogether(true);
        tabla.addCell(responsable("Elaboró", elaboro));
        tabla.addCell(responsable("Revisó", reviso));
        return tabla;
    }

    private static PdfPCell responsable(String cargo, String nombre) {
        Phrase texto = new Phrase();
        texto.add(new Phrase(cargo + ": ", fuente(9, Font.BOLD, Color.BLACK)));
        texto.add(new Phrase(nombre == null || nombre.isBlank() ? "Sin definir" : limpiar(nombre),
                fuente(9, Font.NORMAL, Color.BLACK)));
        PdfPCell celda = new PdfPCell(texto);
        celda.setBorder(Rectangle.TOP);
        celda.setBorderColor(BORDE);
        celda.setPaddingTop(6);
        return celda;
    }

    // Reparte el ancho según el texto más largo de cada columna (entre 7 y 34 letras).
    // El encabezado cuenta un poco más porque va en negrita.
    private static float[] anchos(String[] columnas, List<String[]> filas) {
        float[] anchos = new float[columnas.length];
        for (int c = 0; c < columnas.length; c++) {
            int largo = columnas[c].length() + 3;
            for (String[] fila : filas) {
                if (c < fila.length && fila[c] != null) {
                    largo = Math.max(largo, fila[c].length());
                }
            }
            anchos[c] = Math.max(7, Math.min(largo + 1, 34));
        }
        return anchos;
    }

    // Los números y los valores en pesos se alinean a la derecha.
    private static boolean esNumero(String texto) {
        return texto.matches("-?\\d+") || texto.startsWith("$ ") || texto.equals("—");
    }

    // Deja el texto con los caracteres que la fuente del PDF sabe dibujar.
    private static String limpiar(String texto) {
        if (texto == null) {
            return "";
        }
        String t = texto.replace("→", "a");
        return new String(t.getBytes(LATINO), LATINO);
    }

    private static Font fuente(float tamano, int estilo, Color color) {
        return FontFactory.getFont(FontFactory.HELVETICA, "Cp1252", false, tamano, estilo, color);
    }
}
