package com.aristools.vista;

import com.aristools.controlador.ParametroControlador;
import com.aristools.modelo.Parametro;
import com.aristools.util.NegocioException;
import java.awt.BorderLayout;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.GridLayout;
import java.awt.Insets;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JTextField;

/**
 * Configuración de parámetros globales (RF-015): nombre del establecimiento y
 * umbrales de stock por defecto. Los tres se guardan juntos en una
 * transacción. Cambiar los umbrales generales no altera los umbrales que ya
 * tiene definidos cada producto.
 */
public class ParametrosPanel extends JPanel implements Recargable {

    private final ParametroControlador controlador = new ParametroControlador();
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtMinimo = new JTextField();
    private final JTextField txtMaximo = new JTextField();
    private final JTextField txtVigencia = new JTextField();
    private final JTextField txtInactividad = new JTextField();

    public ParametrosPanel() {
        setLayout(new BorderLayout(0, 18));
        setBackground(Tema.FONDO);
        setBorder(BorderFactory.createEmptyBorder(24, 28, 24, 28));

        JPanel textos = new JPanel(new GridLayout(2, 1));
        textos.setOpaque(false);
        textos.add(Tema.titulo("Configuración"));
        textos.add(Tema.subtitulo("Parámetros generales del sistema y umbrales de stock por defecto."));
        add(textos, BorderLayout.NORTH);

        JPanel tarjeta = new JPanel(new GridBagLayout());
        tarjeta.setBackground(Tema.BLANCO);
        tarjeta.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Tema.BORDE),
                BorderFactory.createEmptyBorder(22, 26, 22, 26)));
        int fila = 0;
        fila = agregar(tarjeta, fila, "Nombre del establecimiento", txtNombre);
        fila = agregar(tarjeta, fila, "Stock mínimo por defecto", txtMinimo);
        fila = agregar(tarjeta, fila, "Stock máximo por defecto", txtMaximo);
        fila = agregar(tarjeta, fila, "Vigencia de la contraseña (días)", txtVigencia);
        fila = agregar(tarjeta, fila, "Cierre por inactividad (minutos)", txtInactividad);

        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila++;
        c.gridx = 0;
        c.gridwidth = 2;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(10, 0, 10, 0);
        JLabel nota = new JLabel("<html><body style='width:420px'>Los umbrales por defecto se aplican a los "
                + "productos que no tienen mínimo o máximo propio. Cambiarlos no modifica los umbrales que ya "
                + "están definidos en cada producto.<br><br><b>Seguridad:</b> con vigencia 0 la contraseña no "
                + "vence (recomendación NIST SP 800-63B); escriba 15 o 30 si la política de la empresa exige "
                + "cambiarla. Con 0 minutos no se cierra la sesión por inactividad.</body></html>");
        nota.setFont(Tema.NORMAL.deriveFont(12f));
        nota.setForeground(Tema.TEXTO_SUAVE);
        tarjeta.add(nota, c);

        JButton btnGuardar = Tema.botonPrincipal("Guardar cambios");
        btnGuardar.addActionListener(e -> guardar());
        JPanel botones = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        botones.setOpaque(false);
        botones.add(btnGuardar);
        c.gridy = fila;
        tarjeta.add(botones, c);

        JPanel contenedor = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
        contenedor.setOpaque(false);
        contenedor.add(tarjeta);
        add(contenedor, BorderLayout.CENTER);
    }

    private int agregar(JPanel panel, int fila, String etiqueta, JTextField campo) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = fila;
        c.gridx = 0;
        c.anchor = GridBagConstraints.WEST;
        c.insets = new Insets(6, 0, 6, 16);
        JLabel l = new JLabel(etiqueta);
        l.setFont(Tema.NEGRITA);
        panel.add(l, c);
        c.gridx = 1;
        c.insets = new Insets(6, 0, 6, 0);
        campo.setFont(Tema.NORMAL);
        campo.setPreferredSize(new Dimension(280, 34));
        panel.add(campo, c);
        return fila + 1;
    }

    /** Carga los valores actuales desde la base. */
    @Override
    public void recargar() {
        txtVigencia.setText("0");        // valores si la base aún no tiene estos parámetros
        txtInactividad.setText("15");
        try {
            for (Parametro p : controlador.listar()) {
                switch (p.getClave()) {
                    case "nombre_establecimiento" -> txtNombre.setText(p.getValor());
                    case "stock_minimo_defecto" -> txtMinimo.setText(p.getValor());
                    case "stock_maximo_defecto" -> txtMaximo.setText(p.getValor());
                    case "dias_vigencia_clave" -> txtVigencia.setText(p.getValor());
                    case "minutos_inactividad" -> txtInactividad.setText(p.getValor());
                    default -> { }
                }
            }
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private void guardar() {
        try {
            int minutos = entero(txtInactividad.getText(), "cierre por inactividad");
            controlador.guardar(txtNombre.getText(),
                    entero(txtMinimo.getText(), "stock mínimo por defecto"),
                    entero(txtMaximo.getText(), "stock máximo por defecto"),
                    entero(txtVigencia.getText(), "vigencia de la contraseña"),
                    minutos);
            MonitorInactividad.aplicar(minutos);   // el nuevo tiempo rige desde ya, sin cerrar sesión
            Tema.info(this, "Configuración guardada correctamente.");
            recargar();
        } catch (NegocioException ex) {
            Tema.error(this, ex.getMessage());
        }
    }

    private static int entero(String texto, String campo) {
        try {
            return Integer.parseInt(texto.trim());
        } catch (NumberFormatException e) {
            throw new NegocioException("El campo " + campo + " debe ser un número entero mayor o igual a cero.");
        }
    }
}
