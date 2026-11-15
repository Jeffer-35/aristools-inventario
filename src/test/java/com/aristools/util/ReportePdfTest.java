package com.aristools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.lowagie.text.pdf.PdfReader;
import com.lowagie.text.pdf.parser.PdfTextExtractor;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Pruebas del generador de reportes PDF. El PDF se escribe en memoria y
 * luego se vuelve a leer para comprobar su contenido; no usa la base de
 * datos ni abre ventanas.
 */
class ReportePdfTest {

    private static final String[] COLUMNAS = {"Fecha", "Tipo", "Documento", "Bodega", "Entrada", "Salida", "Saldo",
        "Costo unit.", "Valor saldo"};

    private byte[] generar(List<String[]> filas) throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        ReportePdf.generar(memoria, "Ferretería Aristools", "Kardex valorizado",
                List.of("Producto: ELE-001 - Cable eléctrico No. 12", "Método de valoración: costo promedio ponderado"),
                COLUMNAS, filas, "Saldo final: 15 rollo(s) · Valor del saldo: $ 2.865.000",
                "Administrador General (Administrador del Sistema)", "Marta Gómez (Supervisor de Bodega)");
        return memoria.toByteArray();
    }

    @Test
    void elReporteLlevaEncabezadoDatosResumenYResponsables() throws Exception {
        List<String[]> filas = new ArrayList<>();
        filas.add(new String[]{"14/11/2026 10:00", "Compra", "FAC-001", "Bodega Principal", "8", "0", "20",
            "$ 200.000", "$ 3.820.000"});
        filas.add(new String[]{"14/11/2026 11:00", "Transferencia", "—", "Principal → Exhibición", "5", "5", "20",
            "$ 191.000", "$ 3.820.000"});
        byte[] pdf = generar(filas);
        assertEquals("%PDF", new String(pdf, 0, 4), "El archivo debe ser un PDF");

        PdfReader lector = new PdfReader(pdf);
        assertEquals(1, lector.getNumberOfPages());
        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
        lector.close();
        for (String esperado : new String[]{"Ferretería Aristools", "Kardex valorizado", "costo promedio ponderado",
            "Costo unit.", "FAC-001", "$ 3.820.000", "Saldo final: 15 rollo(s)", "Elaboró", "Administrador General",
            "Revisó", "Marta Gómez", "Página 1"}) {
            assertTrue(texto.contains(esperado), "El PDF debe contener: " + esperado);
        }
    }

    @Test
    void elDocumentoDeSoporteLlevaNumeroDatosYResponsables() throws Exception {
        ByteArrayOutputStream memoria = new ByteArrayOutputStream();
        List<String[]> campos = new ArrayList<>();
        campos.add(new String[]{"Producto", "PIN-001 - Pintura vinilo blanco 1 gal"});
        campos.add(new String[]{"Cantidad", "2 galón(s)"});
        campos.add(new String[]{"Causa de la baja", "Humedad"});
        campos.add(new String[]{"Valor de la pérdida", "$ 96.000"});
        ReportePdf.generarDocumento(memoria, "Ferretería Aristools", "Acta de baja", "BAJ-000057", campos,
                "Carlos Pérez (Almacenista) · 14/11/2026 10:00", "Marta Gómez (Supervisor de Bodega) · 14/11/2026 11:30");
        PdfReader lector = new PdfReader(memoria.toByteArray());
        String texto = new PdfTextExtractor(lector).getTextFromPage(1);
        lector.close();
        for (String esperado : new String[]{"Ferretería Aristools", "Acta de baja", "BAJ-000057", "Humedad",
            "$ 96.000", "Elaboró", "Carlos Pérez", "Revisó", "Marta Gómez"}) {
            assertTrue(texto.contains(esperado), "El documento debe contener: " + esperado);
        }
    }

    @Test
    void unReporteLargoOcupaVariasPaginas() throws Exception {
        List<String[]> filas = new ArrayList<>();
        for (int i = 1; i <= 150; i++) {
            filas.add(new String[]{"14/11/2026 10:00", "Consumo interno", "—", "Bodega Principal", "0", "1",
                String.valueOf(500 - i), "$ 191.000", "$ 1.000.000"});
        }
        PdfReader lector = new PdfReader(generar(filas));
        int paginas = lector.getNumberOfPages();
        String ultima = new PdfTextExtractor(lector).getTextFromPage(paginas);
        lector.close();
        assertTrue(paginas > 1, "150 filas no caben en una sola página");
        assertTrue(ultima.contains("Costo unit."), "El encabezado de la tabla se repite en cada página");
        assertTrue(ultima.contains("Revisó"), "Los responsables van al final del reporte");
    }
}
