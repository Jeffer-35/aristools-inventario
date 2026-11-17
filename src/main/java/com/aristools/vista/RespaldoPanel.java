package com.aristools.vista;

import com.aristools.controlador.RespaldoControlador;
import com.aristools.util.NegocioException;
import com.aristools.util.RespaldoBD;
import java.awt.BorderLayout;
import java.awt.Cursor;
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridLayout;
import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.table.DefaultTableModel;

/**
 * Respaldos de la base de datos (solo el Administrador). Permite crear un
 * respaldo en cualquier momento, ver los que existen, restaurar uno y definir
 * cada cuántos días se hace el respaldo automático.
 */
public class RespaldoPanel extends JPanel implements Recargable {

    private final RespaldoControlador controlador = new RespaldoControlador();
    private final JTextField txtDias = new JTextField(4);
    private final JLabel lblResumen = new JLabel(" ");
    private final DefaultTableModel modelo = new DefaultTableModel(new String[]{"Archivo", "Fecha", "Tamaño", "Tipo"}, 0) {
        @Override
        public boolean isCellEditable(int f, int c) {
            return false;
        }
    };
    private final JTable tabla = new JTable(modelo);
    private List<File> respaldos = new ArrayList<>();

    public RespaldoPanel() {
        setLayout(new BorderLayout(0, 12));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(20, 24, 16, 24));
        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Respaldos"));
        textos.add(Tema.subtitulo("Copias de seguridad de la base de datos, para recuperar la información si el computador falla."));

        JButton crear = Tema.botonPrincipal("Crear respaldo ahora");
        JButton restaurar = Tema.botonSecundario("Restaurar el seleccionado");
        JButton abrir = Tema.botonSecundario("Abrir la carpeta");
        JButton guardar = Tema.botonSecundario("Guardar");
        crear.addActionListener(e -> crearRespaldo());
        restaurar.addActionListener(e -> restaurarSeleccionado());
        abrir.addActionListener(e -> abrirCarpeta());
        guardar.addActionListener(e -> guardarDias());
        JLabel lblDias = new JLabel("Respaldo automático cada");
        lblDias.setFont(Tema.NEGRITA);
        txtDias.setPreferredSize(new Dimension(50, 32));
        txtDias.setToolTipText("0 = desactivado. El respaldo automático se hace al iniciar sesión.");
        // Fila 1: acciones sobre los respaldos. Fila 2: el respaldo automático.
        JPanel acciones = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        acciones.setOpaque(false);
        acciones.add(crear);
        acciones.add(restaurar);
        acciones.add(abrir);
        JPanel automatico = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 6));
        automatico.setOpaque(false);
        automatico.add(lblDias);
        automatico.add(txtDias);
        automatico.add(new JLabel("días (0 = desactivado)"));
        automatico.add(guardar);
        JPanel barra = new JPanel(new GridLayout(2, 1));
        barra.setBackground(Tema.BLANCO);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE), BorderFactory.createEmptyBorder(4, 10, 4, 10)));
        barra.add(acciones);
        barra.add(automatico);
        JPanel norte = new JPanel(new BorderLayout(0, 12));
        norte.setOpaque(false);
        norte.add(textos, BorderLayout.NORTH);
        norte.add(barra, BorderLayout.CENTER);

        Tema.estilizarTabla(tabla);
        tabla.getColumnModel().getColumn(0).setPreferredWidth(320);
        JScrollPane scroll = new JScrollPane(tabla);
        scroll.setBorder(BorderFactory.createLineBorder(Tema.BORDE));

        lblResumen.setFont(Tema.NEGRITA);
        JLabel nota = new JLabel("<html><body style='width:700px'>Restaurar <b>reemplaza</b> toda la base por la del respaldo: "
                + "se pierde lo registrado después de esa fecha. Antes de hacerlo el sistema guarda un respaldo de tipo Seguridad "
                + "(se conservan los 5 más recientes), por si elige el archivo equivocado. "
                + "Se conservan los 10 respaldos automáticos más recientes; los manuales no se borran. "
                + "Copie la carpeta a una memoria USB o a la nube: un respaldo en el mismo computador no protege si el disco se daña.</body></html>");
        nota.setFont(Tema.NORMAL.deriveFont(12f));
        nota.setForeground(Tema.TEXTO_SUAVE);
        JPanel sur = new JPanel(new BorderLayout(0, 4));
        sur.setOpaque(false);
        sur.add(lblResumen, BorderLayout.NORTH);
        sur.add(nota, BorderLayout.CENTER);

        add(norte, BorderLayout.NORTH);
        add(scroll, BorderLayout.CENTER);
        add(sur, BorderLayout.SOUTH);
    }

    @Override
    public void recargar() {
        try {
            txtDias.setText(String.valueOf(controlador.diasRespaldoAutomatico()));
            respaldos = controlador.listar();
            modelo.setRowCount(0);
            SimpleDateFormat formato = new SimpleDateFormat("dd/MM/yyyy HH:mm");
            for (File f : respaldos) {
                modelo.addRow(new Object[]{f.getName(), formato.format(new Date(f.lastModified())),
                    Math.max(1, f.length() / 1024) + " KB",
                    RespaldoBD.tipo(f)});
            }
            lblResumen.setText(respaldos.isEmpty() ? "Todavía no hay respaldos. Carpeta: " + controlador.carpeta()
                    : respaldos.size() + " respaldo(s) en " + controlador.carpeta());
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void crearRespaldo() {
        ocupado(true);
        try {
            File archivo = controlador.respaldarAhora();
            recargar();
            Tema.info(this, "Respaldo creado:\n" + archivo.getAbsolutePath());
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        } finally {
            ocupado(false);
        }
    }

    private void restaurarSeleccionado() {
        int fila = tabla.getSelectedRow();
        if (fila < 0 || fila >= respaldos.size()) {
            Tema.error(this, "Seleccione en la tabla el respaldo que quiere restaurar.");
            return;
        }
        File archivo = respaldos.get(fila);
        int opcion = JOptionPane.showConfirmDialog(this, "Va a REEMPLAZAR toda la base de datos por el respaldo\n"
                + archivo.getName() + "\n\nSe perderá todo lo registrado después de esa fecha.\n"
                + "Antes de continuar se guardará un respaldo de seguridad de la base actual.\n\n¿Desea continuar?",
                "Restaurar respaldo", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
        if (opcion != JOptionPane.YES_OPTION) {
            return;
        }
        ocupado(true);
        try {
            controlador.restaurar(archivo);
            recargar();
            if (javax.swing.SwingUtilities.getWindowAncestor(this) instanceof PrincipalVista principal) {
                principal.descartarPantallasAbiertas();   // las demás pantallas se cargan de nuevo con los datos restaurados
            }
            Tema.info(this, "Restauración completada. Las demás pantallas ya muestran los datos restaurados.");
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        } finally {
            ocupado(false);
        }
    }

    private void guardarDias() {
        try {
            String texto = txtDias.getText().trim();
            if (!texto.matches("\\d{1,2}")) {
                throw new NegocioException("Escriba los días como un número entero entre 0 y 90.");
            }
            controlador.guardarDiasRespaldoAutomatico(Integer.parseInt(texto));
            Tema.info(this, "Guardado. " + (Integer.parseInt(texto) == 0 ? "El respaldo automático queda desactivado."
                    : "Se hará un respaldo automático cada " + texto + " día(s), al iniciar sesión."));
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void abrirCarpeta() {
        try {
            File carpeta = controlador.carpeta();
            if (!carpeta.isDirectory() && !carpeta.mkdirs()) {
                throw new NegocioException("No se pudo crear la carpeta " + carpeta);
            }
            Desktop.getDesktop().open(carpeta);
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        } catch (Exception ex) {
            Tema.error(this, "No se pudo abrir la carpeta: " + controlador.carpeta());
        }
    }

    private void ocupado(boolean si) {
        setCursor(Cursor.getPredefinedCursor(si ? Cursor.WAIT_CURSOR : Cursor.DEFAULT_CURSOR));
    }
}
