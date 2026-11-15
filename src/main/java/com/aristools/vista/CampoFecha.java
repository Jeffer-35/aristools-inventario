package com.aristools.vista;

import com.aristools.util.NegocioException;
import com.github.lgooddatepicker.components.DatePicker;
import com.github.lgooddatepicker.components.DatePickerSettings;
import java.awt.BorderLayout;
import java.awt.FlowLayout;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.swing.JButton;
import javax.swing.JPanel;

/**
 * Campo de fecha con calendario (librería LGoodDatePicker). El usuario hace
 * clic en el botón y elige el día, sin escribir números. También se puede
 * escribir la fecha como dd/mm/aaaa. Vacío significa "sin límite".
 *
 * Se usa en los filtros de Reportes (kardex, movimientos y pérdidas) y de la
 * Bitácora. El método accesosRapidos arma los botones Hoy, Este mes y Mes
 * anterior, que llenan las dos fechas de un período con un solo clic.
 */
public class CampoFecha extends JPanel {

    private static final DateTimeFormatter FORMATO = DateTimeFormatter.ofPattern("dd/MM/yyyy");

    private final DatePicker calendario;

    public CampoFecha() {
        super(new BorderLayout());
        setOpaque(false);
        DatePickerSettings ajustes = new DatePickerSettings(Locale.forLanguageTag("es-CO"));
        ajustes.setFormatForDatesCommonEra("dd/MM/yyyy");
        ajustes.setAllowEmptyDates(true);
        ajustes.setFontValidDate(Tema.NORMAL);
        ajustes.setFontInvalidDate(Tema.NORMAL);
        ajustes.setFontVetoedDate(Tema.NORMAL);
        calendario = new DatePicker(ajustes);
        calendario.getComponentDateTextField().putClientProperty("JTextField.placeholderText", "dd/mm/aaaa");
        calendario.getComponentDateTextField().setColumns(8);
        calendario.getComponentToggleCalendarButton().setToolTipText("Elegir la fecha en el calendario");
        add(calendario, BorderLayout.CENTER);
    }

    /**
     * Fecha elegida, o null si el campo está vacío.
     *
     * @param nombre nombre del campo para el mensaje de error ("Desde", "Hasta")
     * @throws NegocioException si el usuario escribió una fecha que no existe
     */
    public LocalDate getFecha(String nombre) {
        String texto = calendario.getComponentDateTextField().getText().trim();
        if (texto.isEmpty()) {
            return null;
        }
        if (!calendario.isTextFieldValid()) {
            throw new NegocioException("La fecha '" + nombre + "' no es válida. Elíjala en el calendario "
                    + "o escríbala como dd/mm/aaaa (por ejemplo, 01/11/2026).");
        }
        return calendario.getDate();
    }

    public void setFecha(LocalDate fecha) {
        calendario.setDate(fecha);
    }

    /** Fecha como texto dd/mm/aaaa, o vacío; se usa en el encabezado de los PDF. */
    public String texto() {
        LocalDate fecha = calendario.getDate();
        return fecha == null ? "" : fecha.format(FORMATO);
    }

    /**
     * Botones que llenan un período con un clic: Hoy, Este mes y Mes anterior
     * (lo que más se consulta en una bodega) y Limpiar para quitar las fechas.
     */
    public static JPanel accesosRapidos(CampoFecha desde, CampoFecha hasta) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 0));
        panel.setOpaque(false);
        panel.add(boton("Hoy", () -> poner(desde, hasta, LocalDate.now(), LocalDate.now())));
        panel.add(boton("Este mes", () -> poner(desde, hasta, LocalDate.now().withDayOfMonth(1), LocalDate.now())));
        panel.add(boton("Mes anterior", () -> {
            LocalDate inicio = LocalDate.now().minusMonths(1).withDayOfMonth(1);
            poner(desde, hasta, inicio, inicio.withDayOfMonth(inicio.lengthOfMonth()));
        }));
        panel.add(boton("Limpiar", () -> poner(desde, hasta, null, null)));
        return panel;
    }

    private static void poner(CampoFecha desde, CampoFecha hasta, LocalDate inicio, LocalDate fin) {
        desde.setFecha(inicio);
        hasta.setFecha(fin);
    }

    private static JButton boton(String texto, Runnable accion) {
        JButton b = Tema.botonSecundario(texto);
        b.addActionListener(e -> accion.run());
        return b;
    }
}
