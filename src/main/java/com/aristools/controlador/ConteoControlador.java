package com.aristools.controlador;

import com.aristools.dao.BodegaDAO;
import com.aristools.dao.ExistenciaDAO;
import com.aristools.dao.ProductoDAO;
import com.aristools.dao.TipoMovimientoDAO;
import com.aristools.modelo.Ajuste;
import com.aristools.modelo.Bodega;
import com.aristools.modelo.Existencia;
import com.aristools.modelo.LineaConteo;
import com.aristools.modelo.Producto;
import com.aristools.modelo.TipoMovimiento;
import com.aristools.seguridad.Accion;
import com.aristools.seguridad.Sesion;
import com.aristools.util.NegocioException;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Conteo físico y ajustes (RF-009).
 * 1) Se descarga una plantilla CSV con el stock del sistema.
 * 2) El almacenista escribe lo que contó (en Excel).
 * 3) Se carga el archivo: se procesa completo o se rechaza completo.
 * 4) Diferencia = contado − sistema: positivos se aplican, negativos esperan al Supervisor.
 */
public class ConteoControlador {

    private static final String SEPARADOR = ";";   // Excel en español usa punto y coma
    private static final String BOM = "\uFEFF";    // para que Excel muestre bien las tildes
    private static final String ENCABEZADO = "codigo;producto;stock_sistema;cantidad_contada";

    private final ExistenciaDAO existenciaDAO = new ExistenciaDAO();
    private final ProductoDAO productoDAO = new ProductoDAO();
    private final BodegaDAO bodegaDAO = new BodegaDAO();
    private final TipoMovimientoDAO tipoDAO = new TipoMovimientoDAO();
    private final GestorInventario gestor = new GestorInventario();

    /** Escribe la plantilla con todos los productos activos y su stock en la bodega. */
    public void generarPlantilla(int idBodega, File archivo) {
        Sesion.exigir(Accion.REALIZAR_CONTEO);
        try {
            List<String> lineas = new ArrayList<>();
            lineas.add(BOM + ENCABEZADO);
            for (Existencia e : existenciaDAO.listarPorBodega(idBodega)) {
                lineas.add(e.getProducto().getCodigo() + SEPARADOR
                        + e.getProducto().getNombre().replace(SEPARADOR, ",") + SEPARADOR
                        + e.getCantidad() + SEPARADOR);
            }
            Files.write(archivo.toPath(), lineas, StandardCharsets.UTF_8);
        } catch (java.sql.SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        } catch (IOException e) {
            throw new NegocioException("No se pudo guardar la plantilla en " + archivo.getName()
                    + ". Verifique que el archivo no esté abierto en Excel.");
        }
    }

    /**
     * Lee y valida TODO el archivo. Si alguna línea tiene error no se procesa
     * nada y se informan las líneas con error. El stock del sistema se toma
     * de la base de datos en este momento, no del archivo.
     */
    public List<LineaConteo> leerPlantilla(int idBodega, File archivo) {
        Sesion.exigir(Accion.REALIZAR_CONTEO);
        List<String> filas;
        try {
            filas = Files.readAllLines(archivo.toPath(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new NegocioException("No se pudo leer el archivo. Verifique que sea la plantilla .csv y que no esté abierta en Excel.");
        }
        Map<String, Existencia> enBodega = new LinkedHashMap<>();
        try {
            for (Existencia e : existenciaDAO.listarPorBodega(idBodega)) {
                enBodega.put(e.getProducto().getCodigo().toUpperCase(), e);
            }
        } catch (java.sql.SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }

        List<LineaConteo> lineas = new ArrayList<>();
        List<String> errores = new ArrayList<>();
        Set<String> vistos = new HashSet<>();
        for (int i = 0; i < filas.size(); i++) {
            String fila = filas.get(i).replace(BOM, "").trim();
            int numero = i + 1;
            if (fila.isEmpty() || fila.toLowerCase().startsWith("codigo")) {
                continue;                                   // encabezado o línea vacía
            }
            String[] campos = fila.split(fila.contains(SEPARADOR) ? SEPARADOR : ",", -1);
            if (campos.length < 4) {
                errores.add("Línea " + numero + ": faltan columnas.");
                continue;
            }
            String codigo = campos[0].trim().toUpperCase();
            Existencia e = enBodega.get(codigo);
            if (e == null) {
                errores.add("Línea " + numero + ": el código " + codigo + " no es un producto activo.");
                continue;
            }
            if (!vistos.add(codigo)) {
                errores.add("Línea " + numero + ": el código " + codigo + " está repetido.");
                continue;
            }
            String contado = campos[3].trim();
            if (!contado.matches("\\d+")) {
                errores.add("Línea " + numero + ": la cantidad contada de " + codigo
                        + (contado.isEmpty() ? " está vacía." : " no es un número entero mayor o igual a cero."));
                continue;
            }
            lineas.add(new LineaConteo(codigo, e.getProducto().getNombre(), e.getCantidad(), Integer.parseInt(contado)));
        }
        if (!errores.isEmpty()) {
            List<String> mostrar = errores.size() > 10 ? errores.subList(0, 10) : errores;
            throw new NegocioException("El archivo no se procesó porque tiene " + errores.size()
                    + " línea(s) con error. Corríjalas y cárguelo de nuevo:\n" + String.join("\n", mostrar));
        }
        if (lineas.isEmpty()) {
            throw new NegocioException("El archivo no tiene productos contados.");
        }
        return lineas;
    }

    /**
     * Crea un ajuste por cada diferencia distinta de cero, todos en una sola
     * transacción. @return número de ajustes generados
     */
    public int generarAjustes(int idBodega, List<LineaConteo> lineas) {
        Sesion.exigir(Accion.REALIZAR_CONTEO);
        try {
            Bodega bodega = bodegaDAO.buscarPorId(idBodega);
            TipoMovimiento positivo = tipoDAO.buscarPorNombre("Ajuste positivo");
            TipoMovimiento negativo = tipoDAO.buscarPorNombre("Ajuste negativo");
            String documento = "CONTEO-" + LocalDate.now();
            List<Ajuste> ajustes = new ArrayList<>();
            for (LineaConteo l : lineas) {
                int diferencia = l.diferencia();
                if (diferencia == 0) {
                    continue;
                }
                Producto p = productoDAO.buscarPorCodigo(l.getCodigoProducto());
                Ajuste a = new Ajuste(p, diferencia > 0 ? positivo : negativo, Sesion.getUsuario(),
                        Math.abs(diferencia), bodega, diferencia > 0);
                a.setMotivo("Conteo físico");
                a.setDocumentoSoporte(documento);
                a.validar();
                ajustes.add(a);
            }
            if (ajustes.isEmpty()) {
                throw new NegocioException("No hay diferencias entre lo contado y el sistema: no se generan ajustes.");
            }
            gestor.registrar(ajustes);
            return ajustes.size();
        } catch (java.sql.SQLException e) {
            throw NegocioException.deBaseDeDatos(e);
        }
    }
}
