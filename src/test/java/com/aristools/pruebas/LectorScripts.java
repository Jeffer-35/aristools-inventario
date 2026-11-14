package com.aristools.pruebas;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Convierte el texto de un script de MySQL (aristools.sql, datos_iniciales.sql)
 * en la lista de sentencias que se pueden ejecutar en la base H2 de las pruebas.
 *
 * Se usan los MISMOS scripts de la carpeta database, para que las pruebas
 * trabajen con la misma estructura que la base real. Solo se deja por fuera
 * lo que es propio del servidor MySQL:
 *   - DROP DATABASE, CREATE DATABASE y USE (H2 ya está en su propia base).
 *   - "ENGINE = InnoDB" (motor de almacenamiento de MySQL).
 *   - Los bloques DELIMITER (disparadores escritos en el lenguaje de MySQL).
 */
public final class LectorScripts {

    private LectorScripts() {
    }

    /**
     * Separa el script en sentencias. El punto y coma solo termina una
     * sentencia cuando está fuera de un texto entre comillas y fuera de un
     * comentario.
     *
     * @param script contenido completo del archivo .sql
     * @return sentencias listas para ejecutar, sin el punto y coma final
     */
    public static List<String> sentencias(String script) {
        List<String> lista = new ArrayList<>();
        StringBuilder actual = new StringBuilder();
        boolean enTexto = false;        // dentro de 'comillas simples'
        boolean enDelimiter = false;    // dentro de un bloque DELIMITER $$ ... DELIMITER ;

        for (String linea : script.split("\\R")) {
            String limpia = linea.trim().toUpperCase(Locale.ROOT);
            if (!enTexto && limpia.startsWith("DELIMITER")) {
                // "DELIMITER $$" abre el bloque y "DELIMITER ;" lo cierra
                enDelimiter = !limpia.equals("DELIMITER ;");
                continue;
            }
            if (enDelimiter) {
                continue;
            }
            for (int i = 0; i < linea.length(); i++) {
                char c = linea.charAt(i);
                if (enTexto) {
                    actual.append(c);
                    if (c == '\'') {
                        enTexto = false;
                    }
                } else if (c == '\'') {
                    enTexto = true;
                    actual.append(c);
                } else if (c == '-' && i + 1 < linea.length() && linea.charAt(i + 1) == '-') {
                    break;      // comentario: se ignora el resto de la línea
                } else if (c == ';') {
                    agregar(lista, actual.toString());
                    actual.setLength(0);
                } else {
                    actual.append(c);
                }
            }
            actual.append('\n');
        }
        agregar(lista, actual.toString());
        return lista;
    }

    // Guarda la sentencia si no está vacía y si no es exclusiva del servidor MySQL.
    private static void agregar(List<String> lista, String sentencia) {
        String s = sentencia.trim();
        if (s.isEmpty()) {
            return;
        }
        String inicio = s.toUpperCase(Locale.ROOT);
        if (inicio.startsWith("DROP DATABASE") || inicio.startsWith("CREATE DATABASE") || inicio.startsWith("USE ")) {
            return;
        }
        lista.add(s.replaceAll("(?i)\\)\\s*ENGINE\\s*=\\s*InnoDB", ")"));
    }
}
