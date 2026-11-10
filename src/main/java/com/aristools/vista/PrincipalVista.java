package com.aristools.vista;

import com.aristools.controlador.LoginControlador;
import com.aristools.modelo.Rol;
import com.aristools.modelo.Usuario;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Permisos;
import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;

/**
 * Ventana principal del aplicativo.
 *
 * BorderLayout divide la ventana en zonas:
 *   NORTH  -> barra superior con el nombre del sistema y el usuario conectado
 *   WEST   -> menú lateral con solo las opciones que permite el rol
 *   CENTER -> panelContenedor con CardLayout
 *
 * CardLayout funciona como un mazo de cartas: cada opción del menú es una
 * carta (un panel) y solo se ve una a la vez. Al hacer clic en el menú se
 * ejecuta cardLayout.show(panelContenedor, nombreDeLaCarta).
 */
public class PrincipalVista extends JFrame implements ActionListener {

    // Nombres de las cartas del CardLayout
    public static final String INICIO = "Inicio";
    public static final String USUARIOS = "Usuarios y roles";
    public static final String CATALOGOS = "Catálogos";
    public static final String PROVEEDORES = "Proveedores";
    public static final String MOVIMIENTOS = "Movimientos";
    public static final String CONTEO = "Inventario físico";
    public static final String AUTORIZAR_ENTRADAS = "Autorizar entradas";
    public static final String AUTORIZACIONES = "Autorizaciones";
    public static final String EXISTENCIAS = "Existencias";
    public static final String REPORTES = "Reportes";
    public static final String CONFIGURACION = "Configuración";
    private static final String CERRAR_SESION = "Cerrar sesión";

    private final Usuario usuario;
    private final CardLayout cardLayout = new CardLayout();
    private final JPanel panelContenedor = new JPanel(cardLayout);
    private final Map<String, JButton> botonesMenu = new LinkedHashMap<>();
    private final Map<String, JPanel> paneles = new HashMap<>();
    private final InicioPanel inicioPanel;

    public PrincipalVista(Usuario usuario) {
        super("Aristools - Sistema de Gestión de Inventarios");
        this.usuario = usuario;
        this.inicioPanel = new InicioPanel(usuario.getNombreCompleto());
        setDefaultCloseOperation(EXIT_ON_CLOSE);
        setMinimumSize(new Dimension(1000, 600));
        setSize(1280, 760);
        setLocationRelativeTo(null);
        setExtendedState(MAXIMIZED_BOTH);   // ocupa la pantalla sin quedar debajo de la barra de tareas

        setLayout(new BorderLayout());
        add(crearBarraSuperior(), BorderLayout.NORTH);
        add(crearMenuLateral(), BorderLayout.WEST);
        add(panelContenedor, BorderLayout.CENTER);

        panelContenedor.add(inicioPanel, INICIO);
        paneles.put(INICIO, inicioPanel);
        mostrar(INICIO);
    }

    /** Barra superior (zona NORTH). */
    private JPanel crearBarraSuperior() {
        JPanel barra = new JPanel(new BorderLayout());
        barra.setBackground(Tema.BLANCO);
        barra.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 1, 0, Tema.BORDE),
                BorderFactory.createEmptyBorder(12, 22, 12, 22)));
        JLabel sistema = new JLabel("Ferretería Aristools · Gestión de inventarios");
        sistema.setFont(Tema.NEGRITA.deriveFont(16f));
        sistema.setForeground(Tema.TEXTO);
        JLabel conectado = new JLabel(usuario.getNombreCompleto() + "  |  " + usuario.getRol().getNombreRol());
        conectado.setFont(Tema.NORMAL);
        conectado.setForeground(Tema.TEXTO_SUAVE);
        barra.add(sistema, BorderLayout.WEST);
        barra.add(conectado, BorderLayout.EAST);
        return barra;
    }

    /** Menú lateral (zona WEST): solo aparecen las opciones permitidas para el rol. */
    private JPanel crearMenuLateral() {
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBackground(Tema.AZUL_OSCURO);
        menu.setPreferredSize(new Dimension(240, 0));
        menu.setBorder(BorderFactory.createEmptyBorder(18, 0, 18, 0));

        JLabel logo = new JLabel("ARISTOOLS", SwingConstants.LEFT);
        logo.setFont(Tema.TITULO);
        logo.setForeground(Tema.NARANJA);
        logo.setBorder(BorderFactory.createEmptyBorder(0, 22, 4, 0));
        JLabel rol = new JLabel(usuario.getRol().getNombreRol());
        rol.setFont(Tema.NORMAL);
        rol.setForeground(new java.awt.Color(0xC9D3DC));
        rol.setBorder(BorderFactory.createEmptyBorder(0, 22, 18, 0));
        menu.add(logo);
        menu.add(rol);

        Rol r = usuario.getRol();
        agregarOpcion(menu, INICIO, true);
        agregarOpcion(menu, USUARIOS, Permisos.puede(r, Accion.GESTIONAR_USUARIOS));
        agregarOpcion(menu, CATALOGOS, Permisos.puede(r, Accion.GESTIONAR_CATALOGOS));
        // El Administrador gestiona proveedores dentro de Catálogos; el Jefe de Compras tiene su propia opción.
        agregarOpcion(menu, PROVEEDORES, Permisos.puede(r, Accion.GESTIONAR_PROVEEDORES)
                && !Permisos.puede(r, Accion.GESTIONAR_CATALOGOS));
        agregarOpcion(menu, MOVIMIENTOS, Permisos.puede(r, Accion.REGISTRAR_MOVIMIENTOS));
        agregarOpcion(menu, CONTEO, Permisos.puede(r, Accion.REALIZAR_CONTEO));
        agregarOpcion(menu, AUTORIZAR_ENTRADAS, Permisos.puede(r, Accion.AUTORIZAR_ENTRADAS));
        agregarOpcion(menu, AUTORIZACIONES, Permisos.puede(r, Accion.AUTORIZAR_SALIDAS));
        agregarOpcion(menu, EXISTENCIAS, Permisos.puede(r, Accion.CONSULTAR_EXISTENCIAS));
        agregarOpcion(menu, REPORTES, Permisos.puede(r, Accion.VER_REPORTES));
        agregarOpcion(menu, CONFIGURACION, Permisos.puede(r, Accion.CONFIGURAR_PARAMETROS));

        menu.add(Box.createVerticalGlue());
        agregarOpcion(menu, CERRAR_SESION, true);
        return menu;
    }

    private void agregarOpcion(JPanel menu, String texto, boolean permitido) {
        if (!permitido) {
            return;
        }
        JButton b = new JButton(texto);
        b.setActionCommand(texto);
        b.addActionListener(this);
        b.setHorizontalAlignment(SwingConstants.LEFT);
        b.setFont(Tema.NORMAL.deriveFont(15f));
        b.setForeground(Tema.BLANCO);
        b.setBackground(Tema.AZUL_OSCURO);
        b.setBorder(BorderFactory.createEmptyBorder(11, 22, 11, 10));
        b.setFocusPainted(false);
        b.setContentAreaFilled(true);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 44));
        b.setAlignmentX(LEFT_ALIGNMENT);
        b.putClientProperty("JButton.buttonType", "borderless");
        menu.add(b);
        botonesMenu.put(texto, b);
    }

    /** Responde a los clics del menú lateral (igual que el ejemplo del profesor). */
    @Override
    public void actionPerformed(ActionEvent e) {
        String opcion = e.getActionCommand();
        if (CERRAR_SESION.equals(opcion)) {
            cerrarSesion();
        } else {
            mostrar(opcion);
        }
    }

    /** Muestra la carta indicada y resalta su opción en naranja. */
    public void mostrar(String carta) {
        JPanel panel = paneles.get(carta);
        if (panel == null) {
            panel = crearPanel(carta);   // se crea la primera vez que se abre
            panelContenedor.add(panel, carta);
            paneles.put(carta, panel);
        }
        if (INICIO.equals(carta)) {
            inicioPanel.actualizar();
        } else if (panel instanceof Recargable r) {
            r.recargar();   // datos siempre actualizados al abrir la opción
        }
        cardLayout.show(panelContenedor, carta);
        for (Map.Entry<String, JButton> b : botonesMenu.entrySet()) {
            boolean activo = b.getKey().equals(carta);
            b.getValue().setBackground(activo ? Tema.NARANJA : Tema.AZUL_OSCURO);
        }
    }

    /**
     * Crea el panel de cada opción del menú. En las partes 9.3 y 9.4 se
     * reemplaza cada PanelEnConstruccion por el panel real.
     */
    private JPanel crearPanel(String carta) {
        return switch (carta) {
            case USUARIOS -> new UsuariosPanel();
            case CATALOGOS -> new CatalogosPanel();
            case PROVEEDORES -> new ProveedoresPanel();
            case CONFIGURACION -> new ParametrosPanel();
            case MOVIMIENTOS, CONTEO -> new PanelEnConstruccion(carta, "parte 9.3");
            case AUTORIZAR_ENTRADAS, AUTORIZACIONES, EXISTENCIAS, REPORTES -> new PanelEnConstruccion(carta, "parte 9.4");
            default -> new PanelEnConstruccion(carta, "siguiente parte");
        };
    }

    /** Cierra la sesión, vuelve al inicio de sesión y abre una ventana nueva para el siguiente usuario. */
    private void cerrarSesion() {
        new LoginControlador().cerrarSesion();
        dispose();
        Usuario siguiente = LoginVista.mostrarLogin();
        if (siguiente != null) {
            new PrincipalVista(siguiente).setVisible(true);
        } else {
            System.exit(0);
        }
    }
}
