package com.aristools.vista;

import java.awt.AWTEvent;
import java.awt.Toolkit;
import java.awt.event.AWTEventListener;
import javax.swing.Timer;

/**
 * Cierra la sesión cuando nadie usa el programa durante los minutos
 * configurados en el parámetro minutos_inactividad (0 = desactivado).
 * Cualquier movimiento del mouse o tecla presionada reinicia la cuenta.
 * Si el Administrador cambia los minutos en Configuración, el cambio se
 * aplica de inmediato a la sesión abierta (método aplicar).
 */
public final class MonitorInactividad {

    private static volatile MonitorInactividad activo;   // monitor de la sesión abierta

    private final AWTEventListener oyente;
    private final Timer reloj;
    private volatile int minutos;
    private volatile long ultimaActividad = System.currentTimeMillis();

    private MonitorInactividad(int minutos, Runnable alVencer) {
        this.minutos = minutos;
        oyente = evento -> ultimaActividad = System.currentTimeMillis();
        Toolkit.getDefaultToolkit().addAWTEventListener(oyente,
                AWTEvent.MOUSE_EVENT_MASK | AWTEvent.MOUSE_MOTION_EVENT_MASK | AWTEvent.KEY_EVENT_MASK);
        reloj = new Timer(5_000, e -> {            // revisa cada 5 segundos
            int m = this.minutos;
            if (m > 0 && System.currentTimeMillis() - ultimaActividad >= m * 60_000L) {
                detener();
                alVencer.run();
            }
        });
        reloj.start();
    }

    /** Inicia el monitor de la sesión (con 0 minutos queda inactivo hasta que se configure). */
    public static MonitorInactividad iniciar(int minutos, Runnable alVencer) {
        MonitorInactividad m = new MonitorInactividad(minutos, alVencer);
        activo = m;
        return m;
    }

    /** Aplica de inmediato los nuevos minutos a la sesión abierta y reinicia la cuenta. */
    public static void aplicar(int minutos) {
        MonitorInactividad m = activo;
        if (m != null) {
            m.minutos = minutos;
            m.ultimaActividad = System.currentTimeMillis();
        }
    }

    /** Detiene el reloj y deja de escuchar el teclado y el mouse. */
    public void detener() {
        reloj.stop();
        Toolkit.getDefaultToolkit().removeAWTEventListener(oyente);
        if (activo == this) {
            activo = null;
        }
    }
}
