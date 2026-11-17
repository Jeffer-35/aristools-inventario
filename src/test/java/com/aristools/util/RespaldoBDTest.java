package com.aristools.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aristools.controlador.LoginControlador;
import com.aristools.controlador.RespaldoControlador;
import com.aristools.pruebas.BaseDePruebas;
import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

/**
 * Respaldo de la base de datos. Aquí se prueban las reglas (nombres, comando,
 * cuándo toca, qué se borra y quién puede); el respaldo real necesita MySQL
 * instalado y se prueba a mano desde la pantalla Respaldos.
 */
@ExtendWith(BaseDePruebas.class)
class RespaldoBDTest {

    private static final String CLAVE = "Aristools2026*";
    private final LoginControlador login = new LoginControlador();

    @AfterEach
    void salir() {
        login.cerrarSesion();
    }

    @Test
    void leeElServidorElPuertoYLaBaseDeLaUrl() {
        RespaldoBD.Servidor s = RespaldoBD.leerUrl("jdbc:mysql://localhost:3306/aristools?useSSL=false&serverTimezone=America/Bogota");
        assertEquals("localhost", s.host());
        assertEquals("3306", s.puerto());
        assertEquals("aristools", s.base());
        assertEquals("3306", RespaldoBD.leerUrl("jdbc:mysql://192.168.1.10/aristools").puerto(), "Sin puerto usa el 3306");
        assertThrows(NegocioException.class, () -> RespaldoBD.leerUrl("jdbc:h2:mem:aristools"), "Solo respalda MySQL");
    }

    @Test
    void elComandoNoLlevaLaClaveYElNombreLlevaLaFecha() {
        RespaldoBD.Servidor s = new RespaldoBD.Servidor("localhost", "3306", "aristools");
        List<String> comando = RespaldoBD.comandoRespaldo(new File("mysqldump"), s, "root", new File("r.sql"));
        assertTrue(comando.contains("--user=root"));
        assertTrue(comando.contains("--single-transaction"));
        assertTrue(comando.contains("--set-gtid-purged=OFF"), "Evita el error GTID_PURGED al restaurar");
        assertTrue(comando.contains("--triggers"), "Los disparadores de la bitácora van en el respaldo");
        assertTrue(comando.contains("aristools"));
        assertTrue(comando.stream().noneMatch(p -> p.toLowerCase().contains("password")), "La clave nunca va en el comando");
        LocalDateTime cuando = LocalDateTime.of(2026, 11, 17, 15, 30, 5);
        assertEquals("aristools_20261117_153005.sql", RespaldoBD.nombreArchivo(false, cuando));
        assertEquals("aristools_auto_20261117_153005.sql", RespaldoBD.nombreArchivo(true, cuando));
    }

    @Test
    void elRespaldoAutomaticoTocaCuandoPasanLosDias() {
        LocalDateTime ahora = LocalDateTime.of(2026, 11, 17, 8, 0);
        assertTrue(RespaldoBD.tocaRespaldo(null, 7, ahora), "Si nunca se ha hecho, toca");
        assertTrue(RespaldoBD.tocaRespaldo(ahora.minusDays(7), 7, ahora));
        assertFalse(RespaldoBD.tocaRespaldo(ahora.minusDays(6), 7, ahora));
        assertFalse(RespaldoBD.tocaRespaldo(null, 0, ahora), "Con 0 días está desactivado");
    }

    @Test
    void seConservanLosAutomaticosMasRecientesYNuncaSeBorranLosManuales() {
        List<File> respaldos = new ArrayList<>();
        for (int dia = 1; dia <= 12; dia++) {
            respaldos.add(new File(RespaldoBD.nombreArchivo(true, LocalDateTime.of(2026, 11, dia, 8, 0))));
        }
        respaldos.add(new File(RespaldoBD.nombreArchivo(false, LocalDateTime.of(2026, 1, 1, 8, 0))));   // manual y viejo
        List<File> sobran = RespaldoBD.automaticosSobrantes(respaldos, 10);
        assertEquals(2, sobran.size());
        assertTrue(sobran.stream().allMatch(f -> f.getName().startsWith(RespaldoBD.PREFIJO_AUTOMATICO)));
        assertTrue(sobran.stream().anyMatch(f -> f.getName().contains("20261101")), "Se borra el más antiguo");
        assertTrue(sobran.stream().noneMatch(f -> f.getName().contains("20261112")), "El más nuevo se conserva");
    }

    @Test
    void soloElAdministradorManejaLosRespaldos() {
        login.iniciarSesion("almacen", CLAVE);
        assertThrows(NegocioException.class, () -> new RespaldoControlador().listar());
        assertThrows(NegocioException.class, () -> new RespaldoControlador().respaldarAhora());
        assertThrows(NegocioException.class, () -> new RespaldoControlador().guardarDiasRespaldoAutomatico(3));

        login.iniciarSesion("admin", CLAVE);
        RespaldoControlador respaldos = new RespaldoControlador();
        assertThrows(NegocioException.class, () -> respaldos.restaurar(null), "Hay que elegir el archivo");
        assertThrows(NegocioException.class, () -> respaldos.guardarDiasRespaldoAutomatico(91));
        NegocioException e = assertThrows(NegocioException.class, respaldos::respaldarAhora);
        assertTrue(e.getMessage().contains("MySQL"), "La base de pruebas (H2) no se respalda: " + e.getMessage());
    }
    @Test
    void elRespaldoDeSeguridadTieneSuTipoYSoloSeConservanLosUltimos() {
        LocalDateTime cuando = LocalDateTime.of(2026, 11, 17, 0, 47, 6);
        assertEquals("aristools_antes_de_restaurar_20261117_004706.sql", RespaldoBD.nombreArchivoSeguridad(cuando));
        assertEquals("Seguridad", RespaldoBD.tipo(new File(RespaldoBD.nombreArchivoSeguridad(cuando))));
        assertEquals("Automático", RespaldoBD.tipo(new File(RespaldoBD.nombreArchivo(true, cuando))));
        assertEquals("Manual", RespaldoBD.tipo(new File(RespaldoBD.nombreArchivo(false, cuando))));
        List<File> respaldos = new ArrayList<>();
        for (int dia = 1; dia <= 8; dia++) {
            respaldos.add(new File(RespaldoBD.nombreArchivoSeguridad(LocalDateTime.of(2026, 11, dia, 8, 0))));
        }
        respaldos.add(new File(RespaldoBD.nombreArchivo(false, LocalDateTime.of(2026, 1, 1, 8, 0))));
        respaldos.add(new File(RespaldoBD.nombreArchivo(true, LocalDateTime.of(2026, 1, 2, 8, 0))));
        List<File> sobran = RespaldoBD.seguridadSobrantes(respaldos, 5);
        assertEquals(3, sobran.size(), "De 8 de seguridad se borran los 3 más viejos");
        assertTrue(sobran.stream().allMatch(f -> f.getName().startsWith(RespaldoBD.PREFIJO_SEGURIDAD)),
                "Los manuales y automáticos no se tocan");
    }
}
