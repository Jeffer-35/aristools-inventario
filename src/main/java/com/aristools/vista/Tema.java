package com.aristools.vista;

import com.formdev.flatlaf.FlatLightLaf;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Font;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JTable;
import javax.swing.UIManager;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;

/**
 * Colores, fuentes y estilos comunes de todas las ventanas (igual al mockup):
 * menú lateral azul oscuro, opción activa naranja y encabezados de tabla azules.
 * Centralizarlos aquí evita repetir el mismo código en cada pantalla.
 */
public final class Tema {

    public static final Color AZUL_OSCURO = new Color(0x2B3A4A);
    public static final Color AZUL_MENU_HOVER = new Color(0x3A4D61);
    public static final Color NARANJA = new Color(0xE8891E);
    public static final Color AZUL_TABLA = new Color(0x1F4E79);
    public static final Color FONDO = new Color(0xF4F6F8);
    public static final Color BLANCO = Color.WHITE;
    public static final Color TEXTO = new Color(0x1E2A35);
    public static final Color TEXTO_SUAVE = new Color(0x5F6B76);
    public static final Color ROJO = new Color(0xC0392B);
    public static final Color VERDE = new Color(0x2E7D32);
    public static final Color BORDE = new Color(0xD9DEE3);

    public static final Font TITULO = new Font("Segoe UI", Font.BOLD, 22);
    public static final Font SUBTITULO = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font NORMAL = new Font("Segoe UI", Font.PLAIN, 14);
    public static final Font NEGRITA = new Font("Segoe UI", Font.BOLD, 14);
    public static final Font NUMERO_TARJETA = new Font("Segoe UI", Font.BOLD, 32);

    private Tema() {
    }

    /** Instala la apariencia FlatLaf. Si falla, Swing usa la apariencia por defecto. */
    public static void instalar() {
        try {
            FlatLightLaf.setup();
            UIManager.put("Button.arc", 10);
            UIManager.put("Component.arc", 8);
            UIManager.put("TextComponent.arc", 8);
            UIManager.put("Table.rowHeight", 28);
        } catch (Exception | LinkageError e) {
            System.err.println("No se pudo cargar FlatLaf: " + e.getMessage());
        }
    }

    /** Botón naranja para la acción principal de cada pantalla (Guardar, Ingresar...). */
    public static JButton botonPrincipal(String texto) {
        JButton b = new JButton(texto);
        b.setBackground(NARANJA);
        b.setForeground(BLANCO);
        b.setFont(NEGRITA);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Botón gris para acciones secundarias (Cancelar, Actualizar...). */
    public static JButton botonSecundario(String texto) {
        JButton b = new JButton(texto);
        b.setFont(NORMAL);
        b.setFocusPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        return b;
    }

    /** Título grande de cada pantalla. */
    public static JLabel titulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(TITULO);
        l.setForeground(TEXTO);
        return l;
    }

    /** Texto de apoyo debajo del título. */
    public static JLabel subtitulo(String texto) {
        JLabel l = new JLabel(texto);
        l.setFont(SUBTITULO);
        l.setForeground(TEXTO_SUAVE);
        return l;
    }

    /** Aplica a una tabla el estilo del mockup: encabezado azul y filas cómodas de leer. */
    public static void estilizarTabla(JTable tabla) {
        tabla.setFont(NORMAL);
        tabla.setRowHeight(28);
        tabla.setShowVerticalLines(false);
        tabla.setGridColor(BORDE);
        tabla.setSelectionBackground(new Color(0xFCE3C4));
        tabla.setSelectionForeground(TEXTO);
        tabla.setFillsViewportHeight(true);
        JTableHeader encabezado = tabla.getTableHeader();
        encabezado.setReorderingAllowed(false);
        DefaultTableCellRenderer render = new DefaultTableCellRenderer();
        render.setBackground(AZUL_TABLA);
        render.setForeground(BLANCO);
        render.setFont(NEGRITA);
        render.setBorder(BorderFactory.createEmptyBorder(6, 8, 6, 8));
        encabezado.setDefaultRenderer(render);
    }

    /** Muestra un mensaje de error de negocio (los textos vienen de NegocioException). */
    public static void error(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "Aristools", JOptionPane.ERROR_MESSAGE);
    }

    /** Muestra un mensaje informativo (por ejemplo, "Guardado correctamente"). */
    public static void info(Component padre, String mensaje) {
        JOptionPane.showMessageDialog(padre, mensaje, "Aristools", JOptionPane.INFORMATION_MESSAGE);
    }
}
