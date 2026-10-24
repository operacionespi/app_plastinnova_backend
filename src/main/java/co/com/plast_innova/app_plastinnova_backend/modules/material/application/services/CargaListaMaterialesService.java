package co.com.plast_innova.app_plastinnova_backend.modules.material.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.CargaListaMaterialesDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService.ListaMaterialesCargada;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.CargaListaMateriales;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.CargaListaMaterialesRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Recarga completa desde los CSV del ERP: bodegas, maestro de ítems y lista de materiales.
 *
 * <p>Primero valida los tres archivos y, si algo no cuadra (columnas faltantes, cantidades inválidas, relaciones
 * repetidas, ciclos), rechaza la carga sin tocar nada. Si están bien, en una sola transacción: actualiza bodegas e
 * ítems por código (conservan su id; los ítems que salieron del maestro quedan con en_maestro = 0) y reemplaza la
 * lista de materiales. Los códigos de la lista que no están en el maestro se crean sin descripción y se avisan.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CargaListaMaterialesService {

    static final String ARCHIVO_BODEGAS = "bodegas.csv";
    static final String ARCHIVO_ITEMS = "items.csv";
    static final String ARCHIVO_LISTA = "lista_materiales.csv";

    private static final int MAXIMO_ERRORES = 20;
    private static final int MAXIMO_CODIGOS_EN_AVISO = 30;
    /** SQL Server admite 2100 parámetros por sentencia y 1000 filas por VALUES. */
    private static final int PARAMETROS_POR_SENTENCIA = 2000;

    private final JdbcTemplate jdbc;
    private final CargaListaMaterialesRepository cargaRepository;
    private final ApplicationEventPublisher eventos;

    private record BodegaCsv(String codigo, String descripcion, String descripcionCorta) {
    }

    private record ItemCsv(String codigo, Integer erpId, String descripcion, TipoItem tipo, String unidad,
                           boolean enMaestro) {
    }

    private record RelacionCsv(String padre, String hijo, int secuencia, BigDecimal cantidad, Integer operacion,
                               String bodega) {
    }

    /** Errores que impiden la carga y avisos que no; los errores se reportan juntos (hasta MAXIMO_ERRORES). */
    private static final class Revision {
        final List<String> errores = new ArrayList<>();
        final List<String> avisos = new ArrayList<>();

        void error(LectorCsv csv, int fila, String mensaje) {
            if (errores.size() < MAXIMO_ERRORES) {
                errores.add(csv.archivo() + ", línea " + LectorCsv.linea(fila) + ": " + mensaje);
            }
        }

        void validar() {
            if (!errores.isEmpty()) {
                throw new SolicitudInvalidaException("No se cargó la lista de materiales. "
                        + String.join(" | ", errores) + (errores.size() == MAXIMO_ERRORES ? " | (hay más errores)" : ""));
            }
        }
    }

    @Transactional
    public CargaListaMaterialesDTO cargar(byte[] bodegasCsv, byte[] itemsCsv, byte[] listaCsv, String usuario) {
        long inicio = System.currentTimeMillis();
        Revision revision = new Revision();

        Map<String, BodegaCsv> bodegas = leerBodegas(LectorCsv.leer(ARCHIVO_BODEGAS, bodegasCsv,
                "f150_id", "f150_descripcion", "f150_descripcion_corta"), revision);
        Map<String, ItemCsv> items = leerItems(LectorCsv.leer(ARCHIVO_ITEMS, itemsCsv,
                "f120_id", "f120_referencia", "f120_descripcion", "f120_id_tipo_inv_serv", "f120_id_unidad_inventario"), revision);
        List<RelacionCsv> relaciones = leerRelaciones(LectorCsv.leer(ARCHIVO_LISTA, listaCsv,
                "Secuencia", "Item_Padre", "Item_Hijo", "Cantidad_Hijo", "Id_Operacion", "Bodega"), bodegas, revision);
        revision.validar();
        validarSinCiclos(relaciones, revision);
        revision.validar();

        List<String> sinMaestro = agregarCodigosSinMaestro(relaciones, items);
        if (!sinMaestro.isEmpty()) {
            Collections.sort(sinMaestro);
            revision.avisos.add(sinMaestro.size() + " códigos de la lista de materiales no están en el maestro de ítems "
                    + "(quedan sin descripción, tipo ni unidad): "
                    + String.join(", ", sinMaestro.subList(0, Math.min(sinMaestro.size(), MAXIMO_CODIGOS_EN_AVISO)))
                    + (sinMaestro.size() > MAXIMO_CODIGOS_EN_AVISO ? "…" : ""));
        }

        guardarBodegas(bodegas.values());
        guardarItems(items.values());
        guardarRelaciones(relaciones);

        CargaListaMateriales carga = cargaRepository.save(CargaListaMateriales.builder()
                .fecha(FechaUtc.ahora())
                .usuario(usuario)
                .bodegas(bodegas.size())
                .items(items.size() - sinMaestro.size())
                .relaciones(relaciones.size())
                .itemsSinMaestro(sinMaestro.size())
                .avisos(revision.avisos.isEmpty() ? null : String.join("\n", revision.avisos))
                .build());
        eventos.publishEvent(new ListaMaterialesCargada());
        log.info("Lista de materiales cargada por {}: {} bodegas, {} ítems, {} relaciones en {} ms", usuario,
                bodegas.size(), items.size(), relaciones.size(), System.currentTimeMillis() - inicio);
        return CargaListaMaterialesDTO.de(carga);
    }

    // ------------------------------------------------------------------------------------------------------------
    // Lectura y validación
    // ------------------------------------------------------------------------------------------------------------

    private Map<String, BodegaCsv> leerBodegas(LectorCsv csv, Revision revision) {
        Map<String, BodegaCsv> bodegas = new LinkedHashMap<>();
        for (int f = 0; f < csv.cantidadFilas(); f++) {
            String codigo = csv.valor(f, "f150_id");
            String descripcion = csv.valor(f, "f150_descripcion");
            if (codigo == null || descripcion == null) {
                revision.error(csv, f, "falta el código o la descripción de la bodega.");
            } else if (codigo.length() > 20) {
                revision.error(csv, f, "el código " + codigo + " supera 20 caracteres.");
            } else if (bodegas.putIfAbsent(EstructuraMateriales.clave(codigo),
                    new BodegaCsv(codigo, recortar(descripcion, 100), recortar(csv.valor(f, "f150_descripcion_corta"), 50))) != null) {
                revision.error(csv, f, "la bodega " + codigo + " está repetida.");
            }
        }
        return bodegas;
    }

    private Map<String, ItemCsv> leerItems(LectorCsv csv, Revision revision) {
        Map<String, ItemCsv> items = new LinkedHashMap<>();
        Set<String> tiposDesconocidos = new HashSet<>();
        for (int f = 0; f < csv.cantidadFilas(); f++) {
            String codigo = csv.valor(f, "f120_referencia");
            if (codigo == null) {
                revision.error(csv, f, "falta la referencia del ítem.");
                continue;
            }
            if (codigo.length() > 50) {
                revision.error(csv, f, "la referencia " + codigo + " supera 50 caracteres.");
                continue;
            }
            String tipoErp = csv.valor(f, "f120_id_tipo_inv_serv");
            TipoItem tipo = TipoItem.desdeErp(tipoErp).orElse(null);
            if (tipo == null && tipoErp != null) {
                tiposDesconocidos.add(tipoErp);
            }
            String unidad = csv.valor(f, "f120_id_unidad_inventario");
            if (unidad != null && unidad.length() > 10) {
                revision.error(csv, f, "la unidad " + unidad + " supera 10 caracteres.");
                continue;
            }
            Integer erpId = entero(csv.valor(f, "f120_id"));
            ItemCsv item = new ItemCsv(codigo, erpId, recortar(csv.valor(f, "f120_descripcion"), 255), tipo, unidad, true);
            if (items.putIfAbsent(EstructuraMateriales.clave(codigo), item) != null) {
                revision.error(csv, f, "la referencia " + codigo + " está repetida.");
            }
        }
        if (!tiposDesconocidos.isEmpty()) {
            revision.avisos.add("Tipos de ítem desconocidos (los ítems quedan sin tipo): " + String.join(", ", tiposDesconocidos));
        }
        return items;
    }

    private List<RelacionCsv> leerRelaciones(LectorCsv csv, Map<String, BodegaCsv> bodegas, Revision revision) {
        List<RelacionCsv> relaciones = new ArrayList<>(csv.cantidadFilas());
        Set<String> pares = new HashSet<>();
        for (int f = 0; f < csv.cantidadFilas(); f++) {
            String padre = csv.valor(f, "Item_Padre");
            String hijo = csv.valor(f, "Item_Hijo");
            if (padre == null || hijo == null) {
                revision.error(csv, f, "falta el ítem padre o el ítem hijo.");
                continue;
            }
            if (padre.length() > 50 || hijo.length() > 50) {
                revision.error(csv, f, "un código supera 50 caracteres.");
                continue;
            }
            if (EstructuraMateriales.clave(padre).equals(EstructuraMateriales.clave(hijo))) {
                revision.error(csv, f, "el ítem " + padre + " es componente de sí mismo.");
                continue;
            }
            if (!pares.add(EstructuraMateriales.clave(padre) + "\u0000" + EstructuraMateriales.clave(hijo))) {
                revision.error(csv, f, hijo + " está repetido como componente de " + padre + ".");
                continue;
            }
            BigDecimal cantidad = decimal(csv.valor(f, "Cantidad_Hijo"));
            if (cantidad == null || cantidad.signum() <= 0 || cantidad.scale() > 6 || cantidad.precision() - cantidad.scale() > 12) {
                revision.error(csv, f, "la cantidad '" + csv.valor(f, "Cantidad_Hijo")
                        + "' no es válida (mayor que cero, hasta 6 decimales).");
                continue;
            }
            Integer secuencia = entero(csv.valor(f, "Secuencia"));
            String operacion = csv.valor(f, "Id_Operacion");
            Integer operacionId = entero(operacion);
            if (secuencia == null || (operacion != null && operacionId == null)) {
                revision.error(csv, f, "la secuencia y la operación deben ser números enteros.");
                continue;
            }
            String bodega = csv.valor(f, "Bodega");
            if (bodega != null && !bodegas.containsKey(EstructuraMateriales.clave(bodega))) {
                revision.error(csv, f, "la bodega " + bodega + " no está en " + ARCHIVO_BODEGAS + ".");
                continue;
            }
            relaciones.add(new RelacionCsv(padre, hijo, secuencia, cantidad, operacionId, bodega));
        }
        return relaciones;
    }

    /** Un ciclo haría infinita la explosión: se rechaza la carga indicando el camino. */
    private void validarSinCiclos(List<RelacionCsv> relaciones, Revision revision) {
        Map<String, List<String>> hijos = new HashMap<>();
        Map<String, String> nombres = new HashMap<>();
        for (RelacionCsv r : relaciones) {
            hijos.computeIfAbsent(EstructuraMateriales.clave(r.padre()), k -> new ArrayList<>()).add(EstructuraMateriales.clave(r.hijo()));
            nombres.putIfAbsent(EstructuraMateriales.clave(r.padre()), r.padre());
            nombres.putIfAbsent(EstructuraMateriales.clave(r.hijo()), r.hijo());
        }
        // Recorrido en profundidad iterativo (hasta 6 niveles hoy, pero no se asume): 1 = en el camino, 2 = revisado
        Map<String, Integer> marca = new HashMap<>();
        for (String inicio : hijos.keySet()) {
            if (marca.containsKey(inicio)) {
                continue;
            }
            List<String> camino = new ArrayList<>(List.of(inicio));
            List<Integer> siguiente = new ArrayList<>(List.of(0));
            marca.put(inicio, 1);
            while (!camino.isEmpty()) {
                int tope = camino.size() - 1;
                List<String> componentes = hijos.getOrDefault(camino.get(tope), List.of());
                int i = siguiente.get(tope);
                if (i >= componentes.size()) {
                    marca.put(camino.remove(tope), 2);
                    siguiente.remove(tope);
                    continue;
                }
                siguiente.set(tope, i + 1);
                String hijo = componentes.get(i);
                Integer estado = marca.get(hijo);
                if (estado == null) {
                    marca.put(hijo, 1);
                    camino.add(hijo);
                    siguiente.add(0);
                } else if (estado == 1) {
                    List<String> ciclo = new ArrayList<>(camino.subList(camino.indexOf(hijo), camino.size()));
                    ciclo.add(hijo);
                    revision.errores.add("La lista de materiales tiene un ciclo: "
                            + String.join(" → ", ciclo.stream().map(nombres::get).toList()) + ".");
                    return;
                }
            }
        }
    }

    /** Agrega a los ítems los códigos que solo están en la lista de materiales y los retorna. */
    private static List<String> agregarCodigosSinMaestro(List<RelacionCsv> relaciones, Map<String, ItemCsv> items) {
        List<String> sinMaestro = new ArrayList<>();
        for (RelacionCsv r : relaciones) {
            for (String codigo : List.of(r.padre(), r.hijo())) {
                if (items.putIfAbsent(EstructuraMateriales.clave(codigo),
                        new ItemCsv(codigo, null, null, null, null, false)) == null) {
                    sinMaestro.add(codigo);
                }
            }
        }
        return sinMaestro;
    }

    // ------------------------------------------------------------------------------------------------------------
    // Escritura: tablas temporales de la sesión + MERGE (decenas de miles de filas en pocas sentencias)
    // ------------------------------------------------------------------------------------------------------------

    private void guardarBodegas(Iterable<BodegaCsv> bodegas) {
        jdbc.execute("DROP TABLE IF EXISTS #Bodega_Carga");
        jdbc.execute("""
                CREATE TABLE #Bodega_Carga (
                    codigo NVARCHAR(20) COLLATE DATABASE_DEFAULT PRIMARY KEY,
                    descripcion NVARCHAR(100) COLLATE DATABASE_DEFAULT NOT NULL,
                    descripcion_corta NVARCHAR(50) COLLATE DATABASE_DEFAULT NULL)""");
        List<Object[]> filas = new ArrayList<>();
        bodegas.forEach(b -> filas.add(new Object[]{b.codigo(), b.descripcion(), b.descripcionCorta()}));
        insertar("#Bodega_Carga (codigo, descripcion, descripcion_corta)", 3, filas);
        jdbc.update("""
                MERGE Bodega AS t USING #Bodega_Carga AS s ON t.codigo = s.codigo
                WHEN MATCHED AND EXISTS (SELECT t.codigo, t.descripcion, t.descripcion_corta
                                         EXCEPT SELECT s.codigo, s.descripcion, s.descripcion_corta) THEN
                    UPDATE SET codigo = s.codigo, descripcion = s.descripcion, descripcion_corta = s.descripcion_corta,
                               fecha_actualizacion = SYSUTCDATETIME()
                WHEN NOT MATCHED BY TARGET THEN
                    INSERT (codigo, descripcion, descripcion_corta) VALUES (s.codigo, s.descripcion, s.descripcion_corta);""");
        jdbc.execute("DROP TABLE #Bodega_Carga");
    }

    private void guardarItems(Iterable<ItemCsv> items) {
        jdbc.execute("DROP TABLE IF EXISTS #Item_Carga");
        jdbc.execute("""
                CREATE TABLE #Item_Carga (
                    codigo NVARCHAR(50) COLLATE DATABASE_DEFAULT PRIMARY KEY,
                    erp_id INT NULL,
                    descripcion NVARCHAR(255) COLLATE DATABASE_DEFAULT NULL,
                    tipo NVARCHAR(20) COLLATE DATABASE_DEFAULT NULL,
                    unidad NVARCHAR(10) COLLATE DATABASE_DEFAULT NULL,
                    en_maestro BIT NOT NULL)""");
        List<Object[]> filas = new ArrayList<>();
        items.forEach(i -> filas.add(new Object[]{i.codigo(), i.erpId(), i.descripcion(),
                i.tipo() == null ? null : i.tipo().name(), i.unidad(), i.enMaestro()}));
        insertar("#Item_Carga (codigo, erp_id, descripcion, tipo, unidad, en_maestro)", 6, filas);
        // Los ítems que ya no vienen se conservan (el inventario los referenciará) pero dejan de estar en el maestro
        jdbc.update("""
                MERGE Item AS t USING #Item_Carga AS s ON t.codigo = s.codigo
                WHEN MATCHED AND EXISTS (SELECT t.codigo, t.erp_id, t.descripcion, t.tipo, t.unidad, t.en_maestro
                                         EXCEPT SELECT s.codigo, s.erp_id, s.descripcion, s.tipo, s.unidad, s.en_maestro) THEN
                    UPDATE SET codigo = s.codigo, erp_id = s.erp_id, descripcion = s.descripcion, tipo = s.tipo,
                               unidad = s.unidad, en_maestro = s.en_maestro, fecha_actualizacion = SYSUTCDATETIME()
                WHEN NOT MATCHED BY TARGET THEN
                    INSERT (codigo, erp_id, descripcion, tipo, unidad, en_maestro)
                    VALUES (s.codigo, s.erp_id, s.descripcion, s.tipo, s.unidad, s.en_maestro)
                WHEN NOT MATCHED BY SOURCE AND t.en_maestro = 1 THEN
                    UPDATE SET en_maestro = 0, fecha_actualizacion = SYSUTCDATETIME();""");
        jdbc.execute("DROP TABLE #Item_Carga");
    }

    private void guardarRelaciones(List<RelacionCsv> relaciones) {
        jdbc.execute("DROP TABLE IF EXISTS #Lista_Carga");
        jdbc.execute("""
                CREATE TABLE #Lista_Carga (
                    padre NVARCHAR(50) COLLATE DATABASE_DEFAULT NOT NULL,
                    hijo NVARCHAR(50) COLLATE DATABASE_DEFAULT NOT NULL,
                    secuencia INT NOT NULL,
                    cantidad DECIMAL(18,6) NOT NULL,
                    operacion_id INT NULL,
                    bodega NVARCHAR(20) COLLATE DATABASE_DEFAULT NULL)""");
        List<Object[]> filas = new ArrayList<>(relaciones.size());
        // Todas con la misma escala: el driver declara el DECIMAL con la escala del valor y reutiliza la sentencia
        // preparada, así que un 2 (escala 0) seguido de un 0.5 en la misma posición terminaría guardado como 1
        relaciones.forEach(r -> filas.add(new Object[]{r.padre(), r.hijo(), r.secuencia(), r.cantidad().setScale(6),
                r.operacion(), r.bodega()}));
        insertar("#Lista_Carga (padre, hijo, secuencia, cantidad, operacion_id, bodega)", 6, filas);
        jdbc.update("DELETE FROM Lista_Material");
        jdbc.update("""
                INSERT INTO Lista_Material (item_padre_id, item_hijo_id, secuencia, cantidad, operacion_id, bodega_id)
                SELECT p.id, h.id, c.secuencia, c.cantidad, c.operacion_id, b.id
                FROM #Lista_Carga c
                JOIN Item p ON p.codigo = c.padre
                JOIN Item h ON h.codigo = c.hijo
                LEFT JOIN Bodega b ON b.codigo = c.bodega""");
        jdbc.execute("DROP TABLE #Lista_Carga");
    }

    /** INSERT con varias filas por sentencia: mucho más rápido que una sentencia por fila. */
    private void insertar(String tablaYColumnas, int columnas, List<Object[]> filas) {
        int filasPorSentencia = Math.min(1000, PARAMETROS_POR_SENTENCIA / columnas);
        String fila = "(" + String.join(", ", Collections.nCopies(columnas, "?")) + ")";
        for (int desde = 0; desde < filas.size(); desde += filasPorSentencia) {
            List<Object[]> lote = filas.subList(desde, Math.min(desde + filasPorSentencia, filas.size()));
            Object[] parametros = lote.stream().flatMap(Arrays::stream).toArray();
            jdbc.update("INSERT INTO " + tablaYColumnas + " VALUES " + String.join(", ", Collections.nCopies(lote.size(), fila)),
                    parametros);
        }
    }

    private static Integer entero(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return Integer.valueOf(valor);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Acepta punto decimal; si no hay punto, la coma también (exportes con configuración regional en español). */
    private static BigDecimal decimal(String valor) {
        if (valor == null) {
            return null;
        }
        try {
            return new BigDecimal(valor.contains(".") ? valor : valor.replace(',', '.'));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String recortar(String valor, int maximo) {
        return valor == null || valor.length() <= maximo ? valor : valor.substring(0, maximo);
    }
}
