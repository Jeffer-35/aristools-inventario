package com.aristools.vista;

import com.aristools.controlador.BodegaControlador;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.EstadoRegistro;
import java.util.List;
import javax.swing.JCheckBox;
import javax.swing.JTextField;

/**
 * Gestión de bodegas (RF-004). Código y nombre únicos; una bodega inactiva no recibe movimientos.
 * Una bodega se puede marcar como cuarentena: allí se separa la mercancía averiada.
 */
public class BodegasPanel extends PanelCatalogo<Bodega> {

    private final BodegaControlador controlador = new BodegaControlador();
    private final JTextField txtCodigo = new JTextField();
    private final JTextField txtNombre = new JTextField();
    private final JTextField txtUbicacion = new JTextField();
    private final JCheckBox chkCuarentena = new JCheckBox("Es bodega de cuarentena (mercancía averiada)");

    public BodegasPanel() {
        super("Bodegas", "Lugares físicos donde se guarda la mercancía.");
        construir();
    }

    @Override
    protected String[] columnas() {
        return new String[]{"Código", "Nombre", "Ubicación", "Tipo", "Estado"};
    }

    @Override
    protected int[] anchosColumnas() {
        return new int[]{80, 180, 240, 100, 80};
    }

    @Override
    protected Object[] fila(Bodega b) {
        return new Object[]{b.getCodigo(), b.getNombre(), b.getUbicacion(),
            b.isCuarentena() ? "Cuarentena" : "Normal", b.getEstado()};
    }

    @Override
    protected List<Bodega> consultar(String filtro, boolean inactivos) {
        return controlador.listar(inactivos).stream()
                .filter(b -> contiene(filtro, b.getCodigo(), b.getNombre(), b.getUbicacion())).toList();
    }

    @Override
    protected void construirFormulario() {
        agregarCampo("Código", txtCodigo);
        agregarCampo("Nombre", txtNombre);
        agregarCampo("Ubicación", txtUbicacion);
        chkCuarentena.setOpaque(false);
        agregarCampo("Cuarentena", chkCuarentena);
        agregarNota("El código y el nombre no se pueden repetir. Una bodega inactiva conserva su historial, "
                + "pero no se puede usar en movimientos nuevos. La bodega de cuarentena guarda la mercancía "
                + "averiada: no recibe compras y de ella no sale consumo interno.");
    }

    @Override
    protected void mostrar(Bodega b) {
        txtCodigo.setText(b.getCodigo());
        txtNombre.setText(b.getNombre());
        txtUbicacion.setText(b.getUbicacion());
        chkCuarentena.setSelected(b.isCuarentena());
    }

    @Override
    protected void limpiar() {
        txtCodigo.setText("");
        txtNombre.setText("");
        txtUbicacion.setText("");
        chkCuarentena.setSelected(false);
    }

    @Override
    protected void guardar() {
        Bodega b = new Bodega(seleccionado == null ? 0 : seleccionado.getIdBodega(),
                txtCodigo.getText().trim(), txtNombre.getText().trim(), txtUbicacion.getText().trim(),
                seleccionado == null ? EstadoRegistro.ACTIVO : seleccionado.getEstado());
        b.setCuarentena(chkCuarentena.isSelected());
        controlador.guardar(b);
    }

    @Override
    protected void cambiarEstado(Bodega b) {
        controlador.cambiarEstado(b.getIdBodega(), b.estaActiva() ? EstadoRegistro.INACTIVO : EstadoRegistro.ACTIVO);
    }

    @Override
    protected boolean estaActivo(Bodega b) {
        return b.estaActiva();
    }
}
