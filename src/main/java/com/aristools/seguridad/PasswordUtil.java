package com.aristools.seguridad;

import org.mindrot.jbcrypt.BCrypt;

/**
 * Protección de contraseñas con BCrypt (RNF-001). BCrypt agrega una sal
 * aleatoria a cada contraseña, por eso dos usuarios con la misma clave
 * quedan con hashes distintos y el hash no se puede revertir.
 */
public final class PasswordUtil {

    /** Costo del algoritmo: 2^10 rondas. Más alto = más seguro, pero más lento. */
    private static final int COSTO = 10;

    private PasswordUtil() {
    }

    /** Convierte la contraseña en un hash BCrypt para guardarlo en la base de datos. */
    public static String encriptar(String clave) {
        return BCrypt.hashpw(clave, BCrypt.gensalt(COSTO));
    }

    /**
     * Compara la contraseña escrita con el hash guardado.
     * @return true si coinciden; false si no coinciden o el hash no es válido
     */
    public static boolean verificar(String clave, String hash) {
        if (clave == null || hash == null || hash.isBlank()) {
            return false;
        }
        try {
            return BCrypt.checkpw(clave, hash);
        } catch (IllegalArgumentException e) {
            return false;   // el hash guardado no tiene formato BCrypt
        }
    }
}
