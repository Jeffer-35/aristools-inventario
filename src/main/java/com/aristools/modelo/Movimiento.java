package com.aristools.modelo;

import com.aristools.util.NegocioException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Clase abstracta que reúne lo común a todo movimiento de inventario.
 * Nunca se crea un "movimiento" genérico: siempre es una Entrada, una
 * Salida, una Transferencia o un Ajuste (herencia).
 *
 * Cada subclase implementa a su manera validar() y calcularCambiosStock()
 * (polimorfismo). Así GestorInventario aplica cualquier movimiento sin
 * preguntar de qué tipo es.
 */
public abstract class Movimiento {

    protected int idMovimiento;
    protected Producto producto;
    protected TipoMovimiento tipo;
    protected Usuario usuarioRegistra;
    protected Usuario usuarioAutoriza;
    protected EstadoMovimiento estado;
    protected int cantidad;
    protected LocalDateTime fecha;
    protected LocalDateTime fechaAutorizacion;
    protected String motivo;
    protected String motivoRechazo;
    protected String documentoSoporte;
    protected String observacion;

    /** Todo movimiento nace Pendiente y con la fecha actual. */
    protected Movimiento() {
        this.estado = EstadoMovimiento.PENDIENTE;
        this.fecha = LocalDateTime.now();
    }

    /** Constructor con los datos mínimos de cualquier movimiento. */
    protected Movimiento(Producto producto, TipoMovimiento tipo, Usuario usuarioRegistra, int cantidad) {
        this();
        this.producto = producto;
        this.tipo = tipo;
        this.usuarioRegistra = usuarioRegistra;
        this.cantidad = cantidad;
    }

    // ----- Métodos abstractos (cada subclase los implementa) -----

    /**
     * Verifica que el movimiento esté completo y cumpla las reglas de su tipo.
     * @throws NegocioException con el mensaje para el usuario si algo falla
     */
    public abstract void validar();

    /**
     * Indica cuánto suma o resta este movimiento en cada bodega.
     * Ejemplo: una transferencia de 8 unidades devuelve -8 en el origen y +8 en el destino.
     */
    public abstract List<CambioStock> calcularCambiosStock();

    // ----- Métodos comunes -----

    /**
     * Reglas que aplican a todos los movimientos. Las subclases la llaman
     * al inicio de su propio validar().
     */
    protected void validarComunes() {
        if (producto == null) {
            throw new NegocioException("Seleccione un producto.");
        }
        if (!producto.estaActivo()) {
            throw new NegocioException("El producto " + producto.getCodigo() + " está inactivo.");
        }
        if (tipo == null) {
            throw new NegocioException("Seleccione el tipo de movimiento.");
        }
        if (usuarioRegistra == null) {
            throw new NegocioException("No hay un usuario responsable del movimiento.");
        }
        if (cantidad <= 0) {
            throw new NegocioException("La cantidad debe ser mayor que cero.");
        }
        if (fecha == null) {
            throw new NegocioException("Indique la fecha del movimiento.");
        }
    }

    /** @return true si el tipo de movimiento necesita aprobación antes de afectar el stock */
    public boolean requiereAutorizacion() {
        return tipo != null && tipo.isRequiereAutorizacion();
    }

    /** @return true si el movimiento todavía espera autorización */
    public boolean estaPendiente() {
        return estado == EstadoMovimiento.PENDIENTE;
    }

    /**
     * Aprueba el movimiento (RF-010, RF-011).
     * Reglas: debe estar Pendiente, el autorizador debe tener el rol que exige
     * el tipo y no puede ser quien lo registró (segregación de funciones).
     */
    public void autorizar(Usuario autorizador) {
        verificarQuienResuelve(autorizador);
        this.estado = EstadoMovimiento.AUTORIZADO;
        this.usuarioAutoriza = autorizador;
        this.fechaAutorizacion = LocalDateTime.now();
    }

    /**
     * Rechaza el movimiento; el rechazo exige motivo y no cambia el stock.
     */
    public void rechazar(Usuario autorizador, String motivo) {
        verificarQuienResuelve(autorizador);
        if (motivo == null || motivo.isBlank()) {
            throw new NegocioException("Escriba el motivo del rechazo.");
        }
        this.estado = EstadoMovimiento.RECHAZADO;
        this.usuarioAutoriza = autorizador;
        this.fechaAutorizacion = LocalDateTime.now();
        this.motivoRechazo = motivo.trim();
    }

    // Reglas compartidas por autorizar() y rechazar().
    private void verificarQuienResuelve(Usuario autorizador) {
        if (!estaPendiente()) {
            throw new NegocioException("El movimiento ya fue resuelto y no se puede modificar.");
        }
        if (autorizador == null) {
            throw new NegocioException("No hay un usuario autorizador.");
        }
        if (usuarioRegistra != null && autorizador.getIdUsuario() == usuarioRegistra.getIdUsuario()) {
            throw new NegocioException("Quien registra un movimiento no puede autorizarlo.");
        }
        Rol requerido = tipo == null ? null : tipo.getRolAutorizador();
        if (requerido != null && (autorizador.getRol() == null
                || autorizador.getRol().getIdRol() != requerido.getIdRol())) {
            throw new NegocioException("Este movimiento solo lo puede resolver el rol "
                    + requerido.getNombreRol() + ".");
        }
    }

    // ----- get / set de cada atributo -----

    public int getIdMovimiento() { return idMovimiento; }
    public void setIdMovimiento(int idMovimiento) { this.idMovimiento = idMovimiento; }

    public Producto getProducto() { return producto; }
    public void setProducto(Producto producto) { this.producto = producto; }

    public TipoMovimiento getTipo() { return tipo; }
    public void setTipo(TipoMovimiento tipo) { this.tipo = tipo; }

    public Usuario getUsuarioRegistra() { return usuarioRegistra; }
    public void setUsuarioRegistra(Usuario usuarioRegistra) { this.usuarioRegistra = usuarioRegistra; }

    public Usuario getUsuarioAutoriza() { return usuarioAutoriza; }
    public void setUsuarioAutoriza(Usuario usuarioAutoriza) { this.usuarioAutoriza = usuarioAutoriza; }

    public EstadoMovimiento getEstado() { return estado; }
    public void setEstado(EstadoMovimiento estado) { this.estado = estado; }

    public int getCantidad() { return cantidad; }
    public void setCantidad(int cantidad) { this.cantidad = cantidad; }

    public LocalDateTime getFecha() { return fecha; }
    public void setFecha(LocalDateTime fecha) { this.fecha = fecha; }

    public LocalDateTime getFechaAutorizacion() { return fechaAutorizacion; }
    public void setFechaAutorizacion(LocalDateTime fechaAutorizacion) { this.fechaAutorizacion = fechaAutorizacion; }

    public String getMotivo() { return motivo; }
    public void setMotivo(String motivo) { this.motivo = motivo; }

    public String getMotivoRechazo() { return motivoRechazo; }
    public void setMotivoRechazo(String motivoRechazo) { this.motivoRechazo = motivoRechazo; }

    public String getDocumentoSoporte() { return documentoSoporte; }
    public void setDocumentoSoporte(String documentoSoporte) { this.documentoSoporte = documentoSoporte; }

    public String getObservacion() { return observacion; }
    public void setObservacion(String observacion) { this.observacion = observacion; }
}
