package com.aristools.modelo;

import java.time.LocalDateTime;

/**
 * Cuenta de una persona que usa el aplicativo. La contraseña nunca se guarda
 * en texto plano: solo su hash BCrypt (RNF-001).
 */
public class Usuario {

    private int idUsuario;
    private String nombreCompleto;
    private String documento;
    private String correo;
    private String nombreUsuario;
    private String contrasenaHash;   // hash BCrypt, nunca la contraseña
    private Rol rol;
    private EstadoUsuario estado;
    private int intentosFallidos;
    private LocalDateTime ultimoAcceso;

    /** Constructor vacío: lo usan los DAO al leer filas de la base de datos. */
    public Usuario() {
        this.estado = EstadoUsuario.ACTIVO;
    }

    /** Constructor con todos los atributos. */
    public Usuario(int idUsuario, String nombreCompleto, String documento, String correo, String nombreUsuario, String contrasenaHash, Rol rol, EstadoUsuario estado, int intentosFallidos, LocalDateTime ultimoAcceso) {
        this.idUsuario = idUsuario;
        this.nombreCompleto = nombreCompleto;
        this.documento = documento;
        this.correo = correo;
        this.nombreUsuario = nombreUsuario;
        this.contrasenaHash = contrasenaHash;
        this.rol = rol;
        this.estado = estado;
        this.intentosFallidos = intentosFallidos;
        this.ultimoAcceso = ultimoAcceso;
    }

    /** Máximo de intentos fallidos consecutivos antes de bloquear la cuenta (RF-001). */
    private static final int MAX_INTENTOS = 3;

    /** @return true si la cuenta puede iniciar sesión */
    public boolean estaActivo() {
        return estado == EstadoUsuario.ACTIVO;
    }

    /** @return true si la cuenta fue bloqueada por intentos fallidos */
    public boolean estaBloqueado() {
        return estado == EstadoUsuario.BLOQUEADO;
    }

    /**
     * Suma un intento fallido. Al llegar al tercero la cuenta queda BLOQUEADA
     * hasta que el Administrador la reactive (RF-001).
     */
    public void registrarIntentoFallido() {
        intentosFallidos++;
        if (intentosFallidos >= MAX_INTENTOS) {
            estado = EstadoUsuario.BLOQUEADO;
        }
    }

    /** Vuelve a cero el contador después de un inicio de sesión correcto. */
    public void reiniciarIntentos() {
        intentosFallidos = 0;
    }

    // ----- get / set de cada atributo -----

    public int getIdUsuario() {
        return idUsuario;
    }

    public void setIdUsuario(int idUsuario) {
        this.idUsuario = idUsuario;
    }

    public String getNombreCompleto() {
        return nombreCompleto;
    }

    public void setNombreCompleto(String nombreCompleto) {
        this.nombreCompleto = nombreCompleto;
    }

    public String getDocumento() {
        return documento;
    }

    public void setDocumento(String documento) {
        this.documento = documento;
    }

    public String getCorreo() {
        return correo;
    }

    public void setCorreo(String correo) {
        this.correo = correo;
    }

    public String getNombreUsuario() {
        return nombreUsuario;
    }

    public void setNombreUsuario(String nombreUsuario) {
        this.nombreUsuario = nombreUsuario;
    }

    public String getContrasenaHash() {
        return contrasenaHash;
    }

    public void setContrasenaHash(String contrasenaHash) {
        this.contrasenaHash = contrasenaHash;
    }

    public Rol getRol() {
        return rol;
    }

    public void setRol(Rol rol) {
        this.rol = rol;
    }

    public EstadoUsuario getEstado() {
        return estado;
    }

    public void setEstado(EstadoUsuario estado) {
        this.estado = estado;
    }

    public int getIntentosFallidos() {
        return intentosFallidos;
    }

    public void setIntentosFallidos(int intentosFallidos) {
        this.intentosFallidos = intentosFallidos;
    }

    public LocalDateTime getUltimoAcceso() {
        return ultimoAcceso;
    }

    public void setUltimoAcceso(LocalDateTime ultimoAcceso) {
        this.ultimoAcceso = ultimoAcceso;
    }

    /** Texto que muestran los combo box y las tablas. */
    @Override
    public String toString() {
        return nombreUsuario;
    }

}
