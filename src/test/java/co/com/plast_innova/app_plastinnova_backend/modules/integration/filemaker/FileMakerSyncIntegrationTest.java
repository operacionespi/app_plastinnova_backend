package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services.FileMakerSyncService;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.EstadoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.ModoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.SyncLog;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.repositories.SyncLogRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoDetalleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.support.PruebaIntegracion;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sincronización de punta a punta: FileMaker simulado con un servidor HTTP local + SQL Server real.
 */
@SpringBootTest(properties = "app.filemaker.sync.tamano-pagina=2")
class FileMakerSyncIntegrationTest extends PruebaIntegracion {

    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpServer FILEMAKER = iniciarFileMakerSimulado();
    /** Registros que "tiene" FileMaker en cada prueba. */
    private static final List<Map<String, Object>> REGISTROS = new CopyOnWriteArrayList<>();
    private static final List<String> AUTORIZACIONES = new CopyOnWriteArrayList<>();
    private static final List<String> BUSQUEDAS = new CopyOnWriteArrayList<>();

    @Autowired private FileMakerSyncService syncService;
    @Autowired private SyncLogRepository syncLogRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PedidoService pedidoService;

    @DynamicPropertySource
    static void filemaker(DynamicPropertyRegistry registry) {
        registry.add("app.filemaker.api.base-url",
                () -> "http://localhost:" + FILEMAKER.getAddress().getPort() + "/fmi/data/v1/databases");
    }

    @AfterAll
    static void detener() {
        FILEMAKER.stop(0);
    }

    @BeforeEach
    void limpiar() {
        for (String tabla : List.of("Historial_Estado_Detalle", "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido",
                "Cliente", "Producto", "Ciudad", "Departamento", "Pais", "Sync_Log")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.update("UPDATE Sync_Control SET ultima_fecha_modificacion_fm = NULL WHERE entidad = 'PedidosLineas'");
        REGISTROS.clear();
        AUTORIZACIONES.clear();
        BUSQUEDAS.clear();
    }

    @Test
    void sincronizaCreaActualizaYMarcaEliminados() {
        // --- 1. Carga inicial: 2 pedidos, 3 líneas válidas y 1 inválida, en páginas de 2 registros
        // Valores como los envía FileMaker: estados pegados ("EnAlistamiento") y áreas en plural
        REGISTROS.add(registro("101", "5001", 1, "PT-100", 10, "Nacionales", "EnAlistamiento", "EnAlistamiento"));
        Map<String, Object> anexo = registro("102", "5001", 2, "PT-200", 5, "PuntoDeVenta", "En ensamble", "EnAlistamiento");
        campos(anexo).put("LinPed_Anexo", "SI");
        REGISTROS.add(anexo);
        REGISTROS.add(registro("201", "5002", 1, "PT-100", 7, "Internacionales", "Pintura", "ParaAlistar"));
        Map<String, Object> invalido = registro("301", "5003", 1, "", 1, "Nacional", "Creado", "Creado");
        REGISTROS.add(invalido);
        // Anterior al corte (01/07/2026 00:00 hora Colombia): se lee pero no se sincroniza
        Map<String, Object> antiguo = registro("401", "4001", 1, "PT-900", 3, "Nacional", "Creado", "Creado");
        campos(antiguo).put("LinPed_FechaCreacion", "06/30/2026 23:59:59");
        REGISTROS.add(antiguo);
        // Ya facturado (estado final local) o despachado (estado omitido) en FileMaker: no se importan
        REGISTROS.add(registro("501", "5005", 1, "PT-500", 4, "Nacional", "Entregado", "Facturado"));
        REGISTROS.add(registro("502", "5006", 1, "PT-501", 4, "Nacional", "Entregado", "Despachado"));

        SyncLog primera = ejecutar();
        assertEquals(ModoSync.COMPLETA, primera.getModo()); // sin marca todavía: primera carga completa
        assertEquals(EstadoSync.PARCIAL, primera.getEstado());
        assertEquals(7, primera.getRegistrosLeidos());
        assertEquals(2, primera.getRegistrosOmitidos());
        assertEquals(0, numero("SELECT COUNT(*) FROM Pedido WHERE filemaker_id IN ('5005', '5006')"));
        assertEquals(0, numero("SELECT COUNT(*) FROM Producto WHERE filemaker_id IN ('PT-500', 'PT-501')"));
        assertEquals("[{\"LinPed_FechaCreacion\":\">=07/01/2026\"}]", BUSQUEDAS.getFirst());
        assertEquals(0, numero("SELECT COUNT(*) FROM Pedido WHERE filemaker_id = '4001'"));
        assertEquals(3, primera.getRegistrosInsertados());
        assertEquals(0, primera.getRegistrosActualizados());
        assertEquals(1, primera.getRegistrosConError());
        assertTrue(primera.getMensajeError().contains("Registro 301") && primera.getMensajeError().contains("LinPed_CodPT"));
        assertTrue(primera.getAvisos().contains("'Pintura'"), primera.getAvisos());
        // Basic para abrir sesión y Bearer para leer: se leyeron 4 páginas de 2
        assertTrue(AUTORIZACIONES.getFirst().startsWith("Basic "));
        assertEquals(4, AUTORIZACIONES.stream().filter(a -> a.equals("Bearer token-prueba")).count());

        assertEquals("CREADO", texto("""
                SELECT e.codigo FROM Pedido p JOIN Estado_Pedido e ON e.id = p.estado_actual_id
                WHERE p.filemaker_id = '5001'"""));     // EnAlistamiento de pedido equivale a Creado
        assertEquals("EN_ENSAMBLE", estadoLinea("102"));
        assertEquals("CREADO", estadoLinea("101"));   // EnAlistamiento de línea equivale a Creado
        assertEquals("CREADO", texto("""
                SELECT e.codigo FROM Pedido p JOIN Estado_Pedido e ON e.id = p.estado_actual_id
                WHERE p.filemaker_id = '5002'"""));     // ParaAlistar equivale a Creado
        assertFalse(primera.getAvisos().contains("ParaAlistar"), primera.getAvisos());
        assertFalse(primera.getAvisos().contains("EnAlistamiento"), primera.getAvisos());
        assertEquals(1, numero("SELECT COUNT(*) FROM Pedido_Detalle WHERE es_anexo = 1 AND filemaker_id = '102'"));
        assertEquals(1, numero("SELECT COUNT(*) FROM Pedido_Detalle WHERE es_anexo = 1"));
        assertEquals("CREADO", estadoLinea("201")); // 'Pintura' no existe: estado inicial
        assertEquals("INTERNACIONAL", texto("SELECT tipo_alistamiento FROM Pedido_Detalle WHERE filemaker_id = '201'"));
        assertEquals("NACIONAL", texto("SELECT tipo_alistamiento FROM Pedido_Detalle WHERE filemaker_id = '102'"));
        // 08:30 hora Colombia = 13:30 UTC
        assertEquals("2026-10-08 13:30:00", texto("""
                SELECT CONVERT(VARCHAR(19), fecha_creacion_fm, 120) FROM Pedido WHERE filemaker_id = '5001'"""));
        // Marca de modificación de FileMaker: 09:00 Colombia = 14:00 UTC
        assertEquals("2026-10-08 14:00:00", texto("""
                SELECT CONVERT(VARCHAR(19), fecha_modificacion_fm, 120) FROM Pedido WHERE filemaker_id = '5001'"""));
        assertEquals("Juan Pérez|Cali|Colombia", texto("""
                SELECT c.vendedor + '|' + ci.nombre + '|' + pa.nombre
                FROM Cliente c JOIN Ciudad ci ON ci.id = c.ciudad_id JOIN Pais pa ON pa.id = ci.pais_id"""));
        assertEquals(2, numero("SELECT COUNT(*) FROM Producto"));
        assertEquals(3, numero("SELECT COUNT(*) FROM Historial_Estado_Detalle WHERE origen = 'SYNC'"));
        assertEquals(2, numero("SELECT COUNT(*) FROM Historial_Estado_Pedido WHERE origen = 'SYNC'"));

        // La marca es la modificación más reciente leída: 09:00 Colombia = 14:00 UTC
        assertEquals("2026-10-08 14:00:00", marca());

        // --- 2. Sin cambios en FileMaker: lectura incremental, no se escribe nada
        BUSQUEDAS.clear();
        SyncLog segunda = ejecutar();
        assertEquals(ModoSync.INCREMENTAL, segunda.getModo());
        // Paso 1: modificados desde la marca menos 60 min (hora Colombia). Paso 2: esos pedidos completos
        assertEquals("[{\"zMarcaFechaYHoraModificación\":\">=10/08/2026 08:00:00\"}]", BUSQUEDAS.getFirst());
        String porPedido = BUSQUEDAS.stream().filter(b -> b.contains("LinPed_NumPedido")).findFirst().orElseThrow();
        assertTrue(porPedido.contains("{\"LinPed_NumPedido\":\"==5001\"}"), porPedido);
        assertFalse(porPedido.contains("4001"), porPedido); // anterior al corte: no se descarga
        assertEquals(6, segunda.getRegistrosLeidos());
        assertEquals(0, segunda.getRegistrosInsertados());
        assertEquals(0, segunda.getRegistrosActualizados());
        assertEquals(0, segunda.getRegistrosEliminados());

        // --- 3. Un usuario avanza la línea 101 en la app; FileMaker cambia su cantidad y su estado
        jdbc.update("""
                UPDATE Pedido_Detalle SET estado_actual_id = (SELECT id FROM Estado_Detalle WHERE codigo = 'RESINA')
                WHERE filemaker_id = '101'""");
        // Ya importado: aunque en FileMaker pase a Facturado se sigue sincronizando (el estado lo maneja la app)
        REGISTROS.set(0, registro("101", "5001", 1, "PT-100", 12, "Nacional", "Calidad", "Facturado"));
        campos(REGISTROS.getFirst()).put("zMarcaFechaYHoraModificación", "10/09/2026 11:15:00");
        // El pedido 5002 desaparece de FileMaker; el registro inválido sigue existiendo
        REGISTROS.remove(2);

        SyncLog tercera = ejecutar();
        assertEquals(0, tercera.getRegistrosInsertados());
        assertEquals(1, tercera.getRegistrosActualizados());
        // Borrar en FileMaker no deja marca: la lectura incremental no ve el pedido 5002
        assertEquals(0, tercera.getRegistrosEliminados());
        assertEquals(0, numero("SELECT CAST(eliminado_en_fm AS INT) FROM Pedido WHERE filemaker_id = '5002'"));
        assertEquals("2026-10-09 16:15:00", marca());

        // La reconciliación relee los pedidos abiertos (5001 y 5002) y detecta el borrado
        SyncLog reconciliacion = reconciliar();
        assertEquals(ModoSync.RECONCILIACION, reconciliacion.getModo());
        assertEquals(1, reconciliacion.getRegistrosEliminados());
        assertEquals("2026-10-09 16:15:00", marca()); // la reconciliación no mueve la marca
        assertEquals(12, numero("SELECT cantidad_solicitada FROM Pedido_Detalle WHERE filemaker_id = '101'"));
        assertEquals("RESINA", estadoLinea("101")); // la sincronización no pisa el estado de la app
        // La línea guarda su marca; el pedido toma la más reciente de sus líneas (16:15 UTC)
        assertEquals("2026-10-09 16:15:00", texto("""
                SELECT CONVERT(VARCHAR(19), fecha_modificacion_fm, 120) FROM Pedido_Detalle WHERE filemaker_id = '101'"""));
        assertEquals("2026-10-08 14:00:00", texto("""
                SELECT CONVERT(VARCHAR(19), fecha_modificacion_fm, 120) FROM Pedido_Detalle WHERE filemaker_id = '102'"""));
        assertEquals("2026-10-09 16:15:00", texto("""
                SELECT CONVERT(VARCHAR(19), fecha_modificacion_fm, 120) FROM Pedido WHERE filemaker_id = '5001'"""));
        assertEquals(1, numero("SELECT CAST(eliminado_en_fm AS INT) FROM Pedido_Detalle WHERE filemaker_id = '201'"));
        assertEquals(1, numero("SELECT CAST(eliminado_en_fm AS INT) FROM Pedido WHERE filemaker_id = '5002'"));
        assertEquals(0, numero("SELECT CAST(eliminado_en_fm AS INT) FROM Pedido WHERE filemaker_id = '5001'"));
        assertEquals("CREADO", texto("""
                SELECT e.codigo FROM Pedido p JOIN Estado_Pedido e ON e.id = p.estado_actual_id
                WHERE p.filemaker_id = '5001'"""));
        // La consulta del pedido muestra la línea eliminada, marcada
        List<PedidoDetalleResponseDTO> lineas5002 = pedidoService.obtenerPedidoPorId(
                (long) numero("SELECT id FROM Pedido WHERE filemaker_id = '5002'")).detalles();
        assertEquals(1, lineas5002.size());
        assertTrue(lineas5002.getFirst().eliminadoEnFm());

        // --- 4. El pedido 5002 reaparece (modificado después de la marca): se reactiva sin duplicarse
        Map<String, Object> reaparece = registro("201", "5002", 1, "PT-100", 7, "Internacional", "Pintura", "Creado");
        campos(reaparece).put("zMarcaFechaYHoraModificación", "10/09/2026 12:00:00");
        REGISTROS.add(reaparece);
        BUSQUEDAS.clear();
        SyncLog cuarta = ejecutar();
        assertEquals("[{\"zMarcaFechaYHoraModificación\":\">=10/09/2026 10:15:00\"}]", BUSQUEDAS.getFirst());
        assertEquals(3, cuarta.getRegistrosLeidos()); // solo los pedidos 5001 (2 líneas) y 5002 (1)
        assertEquals(1, cuarta.getRegistrosActualizados());
        assertEquals("2026-10-09 17:00:00", marca());
        assertEquals(0, numero("SELECT CAST(eliminado_en_fm AS INT) FROM Pedido WHERE filemaker_id = '5002'"));
        assertEquals(3, numero("SELECT COUNT(*) FROM Pedido_Detalle"));

        // --- 5. FileMaker no devuelve registros: no se marca nada como eliminado, ni siquiera al reconciliar
        REGISTROS.clear();
        SyncLog quinta = ejecutar();
        assertEquals(EstadoSync.EXITO, quinta.getEstado());
        assertEquals(0, quinta.getRegistrosEliminados());
        assertEquals(0, reconciliar().getRegistrosEliminados());
        assertEquals(0, numero("SELECT COUNT(*) FROM Pedido_Detalle WHERE eliminado_en_fm = 1"));
        assertNotNull(texto("SELECT CONVERT(VARCHAR(30), ultima_ejecucion_exitosa, 126) FROM Sync_Control WHERE entidad = 'PedidosLineas'"));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> campos(Map<String, Object> registro) {
        return (Map<String, Object>) registro.get("fieldData");
    }

    private SyncLog ejecutar() {
        syncService.ejecutarProgramada();
        return ultimaBitacora();
    }

    private SyncLog reconciliar() {
        syncService.ejecutarReconciliacion();
        return ultimaBitacora();
    }

    private SyncLog ultimaBitacora() {
        return syncLogRepository.findByEntidadOrderByFechaInicioDesc(FileMakerSyncService.ENTIDAD, PageRequest.of(0, 1))
                .getContent().getFirst();
    }

    /** Marca de la sincronización incremental (UTC). */
    private String marca() {
        return texto("""
                SELECT CONVERT(VARCHAR(19), ultima_fecha_modificacion_fm, 120) FROM Sync_Control
                WHERE entidad = 'PedidosLineas'""");
    }

    private String estadoLinea(String recordId) {
        return texto("""
                SELECT e.codigo FROM Pedido_Detalle d JOIN Estado_Detalle e ON e.id = d.estado_actual_id
                WHERE d.filemaker_id = ?""", recordId);
    }

    private String texto(String sql, Object... args) {
        return jdbc.queryForObject(sql, String.class, args);
    }

    private int numero(String sql) {
        Integer valor = jdbc.queryForObject(sql, Integer.class);
        return valor != null ? valor : -1;
    }

    private static Map<String, Object> registro(String recordId, String numPedido, int posicion, String codigo,
                                                int cantidad, String alistamiento, String estadoLinea, String estadoPedido) {
        Map<String, Object> campos = new LinkedHashMap<>();
        campos.put("LinPed_FechaCreacion", "10/08/2026 08:30:00");
        campos.put("zMarcaFechaYHoraModificación", "10/08/2026 09:00:00");
        campos.put("LinPed_AlistamArea", alistamiento);
        campos.put("LinPed_Estado", estadoLinea);
        campos.put("LinPed_Cliente", "Plásticos del Valle S.A.S.");
        campos.put("LinPed_NumPedido", numPedido);
        campos.put("LinPed_PosPed", posicion);
        campos.put("LinPed_CodPT", codigo);
        campos.put("LinPed_CantPedido", cantidad);
        campos.put("Pedidos::Ped_Estado", estadoPedido);
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Ciudad", "Cali");
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Distribuidor", "Juan Pérez");
        campos.put("_Clientes_LnsPeds_CodCliente::Cliente_Pais", "Colombia");
        campos.put("LinPed_Anexo", "No");
        Map<String, Object> registro = new HashMap<>();
        registro.put("fieldData", campos);
        registro.put("portalData", Map.of());
        registro.put("recordId", recordId);
        registro.put("modId", "1");
        return registro;
    }

    /** Responde como la Data API de FileMaker: sesiones y búsquedas (_find) paginadas. */
    private static HttpServer iniciarFileMakerSimulado() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            String base = "/fmi/data/v1/databases/PlastInnova_Info";
            server.createContext(base + "/sessions", ex -> {
                if ("POST".equals(ex.getRequestMethod())) {
                    AUTORIZACIONES.add(ex.getRequestHeaders().getFirst("Authorization"));
                    responder(ex, Map.of("response", Map.of("token", "token-prueba"), "messages", List.of(ok())));
                } else {
                    responder(ex, Map.of("response", Map.of(), "messages", List.of(ok())));
                }
            });
            server.createContext(base + "/layouts/PedidosLineas-Api/_find", ex -> {
                AUTORIZACIONES.add(ex.getRequestHeaders().getFirst("Authorization"));
                Map<?, ?> busqueda = JSON.readValue(ex.getRequestBody().readAllBytes(), Map.class);
                BUSQUEDAS.add(JSON.writeValueAsString(busqueda.get("query")));
                int offset = ((Number) busqueda.get("offset")).intValue();
                int limite = ((Number) busqueda.get("limit")).intValue();
                @SuppressWarnings("unchecked")
                List<Map<String, String>> consultas = (List<Map<String, String>>) busqueda.get("query");
                List<Map<String, Object>> todos = REGISTROS.stream().filter(r -> cumple(r, consultas)).toList();
                if (todos.isEmpty()) {
                    // Así responde FileMaker cuando la búsqueda no encuentra registros
                    responder(ex, 500, Map.of("response", Map.of(),
                            "messages", List.of(Map.of("code", "401", "message", "No records match the request"))));
                    return;
                }
                List<Map<String, Object>> pagina = todos.subList(
                        Math.min(offset - 1, todos.size()), Math.min(offset - 1 + limite, todos.size()));
                responder(ex, Map.of(
                        "response", Map.of(
                                "dataInfo", Map.of("database", "PlastInnova_Info", "layout", "PedidosLineas-Api",
                                        "totalRecordCount", todos.size(), "foundCount", todos.size(),
                                        "returnedCount", pagina.size()),
                                "data", pagina),
                        "messages", List.of(ok())));
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Consultas OR; dentro de cada una, todos los criterios (AND). Soporta "==valor" y ">=fecha". */
    private static boolean cumple(Map<String, Object> registro, List<Map<String, String>> consultas) {
        return consultas.stream().anyMatch(consulta -> consulta.entrySet().stream().allMatch(criterio -> {
            String valor = String.valueOf(campos(registro).get(criterio.getKey()));
            String condicion = criterio.getValue();
            if (criterio.getKey().equals("LinPed_FechaCreacion")) {
                // No se filtra: así la prueba cubre el corte por hora que aplica la app
                return true;
            }
            if (condicion.startsWith("==")) {
                return valor.equals(condicion.substring(2));
            }
            if (condicion.startsWith(">=")) {
                return !fechaFm(valor).isBefore(fechaFm(condicion.substring(2)));
            }
            throw new IllegalArgumentException("Criterio no simulado: " + condicion);
        }));
    }

    private static LocalDateTime fechaFm(String valor) {
        return LocalDateTime.parse(valor, DateTimeFormatter.ofPattern("MM/dd/yyyy HH:mm:ss"));
    }

    private static Map<String, String> ok() {
        return Map.of("code", "0", "message", "OK");
    }

    private static void responder(HttpExchange ex, Object cuerpo) throws IOException {
        responder(ex, 200, cuerpo);
    }

    private static void responder(HttpExchange ex, int estado, Object cuerpo) throws IOException {
        byte[] json = JSON.writeValueAsString(cuerpo).getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(estado, json.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(json);
        }
    }
}
