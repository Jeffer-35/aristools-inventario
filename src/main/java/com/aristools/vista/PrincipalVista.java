package com.aristools.vista;

import com.aristools.controlador.LoginControlador;
import com.aristools.modelo.Naturaleza;
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
    public static final String COMPRAS = "Sugerencia de compra";
    public static final String ORDENES = "Órdenes de compra";
    public static final String AUTORIZACIONES = "Autorizaciones";
    public static final String EXISTENCIAS = "Existencias";
    public static final String VENCIMIENTOS = "Vencimientos";
    public static final String REPORTES = "Reportes";
    public static final String INDICADORES = "Indicadores";
    public static final String CONFIGURACION = "Configuración";
    public static final String BITACORA = "Bitácora";
    public static final String RESPALDOS = "Respaldos";
    private static final String CERRAR_SESION = "Cerrar sesión";
    private static final String CAMBIAR_CLAVE = "Cambiar mi contraseña";
    private MonitorInactividad monitor;   // cierra la sesión si nadie usa el programa

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

        panelContenedor.add(PanelDesplazable.envolver(inicioPanel), INICIO);
        paneles.put(INICIO, inicioPanel);
        mostrar(INICIO);
        // Respaldo automático de la base, si ya pasaron los días configurados (no frena al usuario)
        new com.aristools.controlador.RespaldoControlador().respaldoAutomaticoSiToca();
        // Cierre automático de la sesión por inactividad (parámetro minutos_inactividad)
        int minutos = new LoginControlador().minutosInactividad();
        monitor = MonitorInactividad.iniciar(minutos, () -> {
            Tema.info(this, "La sesión se cerró porque el sistema estuvo sin uso el tiempo "
                    + "configurado. Inicie sesión de nuevo.");
            cerrarSesion();
        });
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
        // Las opciones van en una zona con barra de desplazamiento (si la pantalla es pequeña) y
        // "Cambiar mi contraseña" y "Cerrar sesión" quedan fijas abajo, siempre visibles.
        JPanel menu = new JPanel();
        menu.setLayout(new BoxLayout(menu, BoxLayout.Y_AXIS));
        menu.setBackground(Tema.AZUL_OSCURO);
        menu.setBorder(BorderFactory.createEmptyBorder(18, 0, 6, 0));

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
        agregarOpcion(menu, COMPRAS, Permisos.puede(r, Accion.SUGERIR_COMPRA));
        agregarOpcion(menu, ORDENES, Permisos.puede(r, Accion.SUGERIR_COMPRA));
        agregarOpcion(menu, AUTORIZACIONES, Permisos.puede(r, Accion.AUTORIZAR_SALIDAS));
        agregarOpcion(menu, EXISTENCIAS, Permisos.puede(r, Accion.CONSULTAR_EXISTENCIAS));
        agregarOpcion(menu, VENCIMIENTOS, Permisos.puede(r, Accion.CONSULTAR_EXISTENCIAS));
        agregarOpcion(menu, REPORTES, Permisos.puede(r, Accion.VER_REPORTES));
        agregarOpcion(menu, INDICADORES, Permisos.puede(r, Accion.VER_REPORTES));
        agregarOpcion(menu, CONFIGURACION, Permisos.puede(r, Accion.CONFIGURAR_PARAMETROS));
        agregarOpcion(menu, BITACORA, Permisos.puede(r, Accion.VER_BITACORA));
        agregarOpcion(menu, RESPALDOS, Permisos.puede(r, Accion.CONFIGURAR_PARAMETROS));

        JPanel fijas = new JPanel();
        fijas.setLayout(new BoxLayout(fijas, BoxLayout.Y_AXIS));
        fijas.setBackground(Tema.AZUL_OSCURO);
        fijas.setBorder(BorderFactory.createEmptyBorder(6, 0, 12, 0));
        agregarOpcion(fijas, CAMBIAR_CLAVE, true);
        agregarOpcion(fijas, CERRAR_SESION, true);

        javax.swing.JScrollPane zona = new javax.swing.JScrollPane(menu,
                javax.swing.ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                javax.swing.ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        zona.setBorder(null);
        zona.getViewport().setBackground(Tema.AZUL_OSCURO);
        zona.getVerticalScrollBar().setUnitIncrement(24);
        JPanel lateral = new JPanel(new BorderLayout());
        lateral.setBackground(Tema.AZUL_OSCURO);
        lateral.setPreferredSize(new Dimension(240, 0));
        lateral.add(zona, BorderLayout.CENTER);
        lateral.add(fijas, BorderLayout.SOUTH);
        return lateral;
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
        b.setBorder(BorderFactory.createEmptyBorder(9, 22, 9, 10));
        b.setFocusPainted(false);
        b.setContentAreaFilled(true);
        b.setOpaque(true);
        b.setBorderPainted(false);
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        b.setMaximumSize(new Dimension(Integer.MAX_VALUE, 40));
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
        } else if (CAMBIAR_CLAVE.equals(opcion)) {
            CambiarClaveDialogo.mostrar(this, null, false);
        } else {
            mostrar(opcion);
        }
    }

    /**
     * Después de restaurar un respaldo los datos de la base cambiaron: se descartan las pantallas ya abiertas
     * (excepto Inicio y Respaldos) para que se vuelvan a crear con los datos restaurados.
     */
    public void descartarPantallasAbiertas() {
        for (String carta : new java.util.ArrayList<>(paneles.keySet())) {
            if (!INICIO.equals(carta) && !RESPALDOS.equals(carta)) {
                JPanel viejo = paneles.remove(carta);
                for (java.awt.Component c : panelContenedor.getComponents()) {
                    if (c == viejo || (c instanceof javax.swing.JScrollPane sp && sp.getViewport().getView() == viejo)
                            || (c instanceof javax.swing.JComponent jc && contiene(jc, viejo))) {
                        panelContenedor.remove(c);
                    }
                }
            }
        }
        inicioPanel.actualizar();
        panelContenedor.revalidate();
    }

    private static boolean contiene(java.awt.Container padre, java.awt.Component buscado) {
        for (java.awt.Component c : padre.getComponents()) {
            if (c == buscado || (c instanceof java.awt.Container h && contiene(h, buscado))) {
                return true;
            }
        }
        return false;
    }

    /** Muestra la carta indicada y resalta su opción en naranja. */
    public void mostrar(String carta) {
        JPanel panel = paneles.get(carta);
        if (panel == null) {
            panel = crearPanel(carta);   // se crea la primera vez que se abre
            panelContenedor.add(PanelDesplazable.envolver(panel), carta);   // con barra si no cabe
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

    /** Crea el panel de cada opción del menú la primera vez que se abre. */
    private JPanel crearPanel(String carta) {
        return switch (carta) {
            case USUARIOS -> new UsuariosPanel();
            case CATALOGOS -> new CatalogosPanel();
            case PROVEEDORES -> new ProveedoresPanel();
            case CONFIGURACION -> new ParametrosPanel();
            case BITACORA -> new BitacoraPanel();
            case RESPALDOS -> new RespaldoPanel();
            case MOVIMIENTOS -> new MovimientosPanel();
            case CONTEO -> new ConteoPanel();
            case AUTORIZAR_ENTRADAS -> new AutorizacionesPanel(Naturaleza.ENTRADA);
            case COMPRAS -> new SugerenciaCompraPanel();
            case ORDENES -> new OrdenesCompraPanel();
            case AUTORIZACIONES -> new AutorizacionesPanel(Naturaleza.SALIDA);
            case EXISTENCIAS -> new ExistenciasPanel();
            case VENCIMIENTOS -> new VencimientosPanel();
            case REPORTES -> new ReportesPanel();
            case INDICADORES -> new IndicadoresPanel();
            default -> new PanelEnConstruccion(carta, "siguiente parte");
        };
    }

    /** Cierra la sesión, vuelve al inicio de sesión y abre una ventana nueva para el siguiente usuario. */
    private void cerrarSesion() {
        if (monitor != null) {
            monitor.detener();
            monitor = null;
        }
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
