package co.com.plast_innova.app_plastinnova_backend.modules.inventory;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.CargaExistenciasDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ExistenciaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.CargaExistenciasService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.SlotService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.MaterialesPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.TotalMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.CargaListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import co.com.plast_innova.app_plastinnova_backend.support.PruebaIntegracion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.AJUSTE;
import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.CARGA_INICIAL;
import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.ENTRADA;
import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.SALIDA;
import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.TRASLADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Inventario contra SQL Server real. Lista: IT-PT1 = 2 × LAM/IT (bodega 1A001) + 0.5 kg RESINA-IT (1A013);
 * LAM/IT = 3 × TORN-IT (1A001).
 */
@SpringBootTest
class InventarioIntegrationTest extends PruebaIntegracion {

    private static final String BODEGAS = """
            f150_id,f150_descripcion,f150_descripcion_corta
            1A001,Almacen PT,AlmacenPT
            1A013,Almacen de resina,Almacen13
            """;
    private static final String ITEMS = """
            f120_id,f120_referencia,f120_descripcion,f120_id_tipo_inv_serv,f120_id_unidad_inventario
            1,IT-PT1,Lámpara IT,ProductoTerminado,Und
            2,LAM/IT,Lámina IT,Semiterminado,Und
            3,RESINA-IT,Resina IT,MateriaPrima,Kg
            4,TORN-IT,Tornillo IT,MateriaPrima,Und
            """;
    private static final String LISTA = """
            Secuencia,Item_Padre,Item_Hijo,Cantidad_Hijo,Id_Operacion,Bodega
            1,IT-PT1,LAM/IT,2,,1A001
            2,IT-PT1,RESINA-IT,0.5,,1A013
            1,LAM/IT,TORN-IT,3,,1A001
            """;
    private static final String USUARIO = "it@plast-innova.com.co";

    @Autowired private CargaListaMaterialesService cargaListaService;
    @Autowired private ListaMaterialesService listaMaterialesService;
    @Autowired private SlotService slotService;
    @Autowired private InventarioService inventarioService;
    @Autowired private CargaExistenciasService cargaExistenciasService;
    @Autowired private JdbcTemplate jdbc;

    private int a1;
    private int a2;
    private int resina;

    @BeforeEach
    void preparar() {
        limpiar();
        cargaListaService.cargar(BODEGAS.getBytes(StandardCharsets.UTF_8), ITEMS.getBytes(StandardCharsets.UTF_8),
                LISTA.getBytes(StandardCharsets.UTF_8), USUARIO);
        a1 = slotService.crear(new SlotRequestDTO(bodega("1A001"), " a-01 ", "Estante A", null)).id();
        a2 = slotService.crear(new SlotRequestDTO(bodega("1A001"), "A-02", null, null)).id();
        resina = slotService.crear(new SlotRequestDTO(bodega("1A013"), "R-01", null, null)).id();
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Pedido_Detalle", "Pedido", "Cliente", "Producto", "Movimiento_Inventario", "Existencia",
                "Slot", "Lista_Material", "Item", "Bodega", "Carga_Lista_Materiales")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void losSlotsSonUnicosPorBodegaYNoSeDesactivanConExistencias() {
        assertEquals(List.of("A-01", "A-02"), slotService.listar(bodega("1A001")).stream().map(SlotDTO::codigo).toList());
        assertThrows(BusinessRuleException.class, () -> slotService.crear(new SlotRequestDTO(bodega("1A001"), "a-01", null, null)));
        // El mismo código en otra bodega sí se puede
        slotService.crear(new SlotRequestDTO(bodega("1A013"), "A-01", null, null));

        mover(ENTRADA, "LAM/IT", null, a1, "5");
        BusinessRuleException conExistencias = assertThrows(BusinessRuleException.class,
                () -> slotService.actualizar(a1, new SlotRequestDTO(bodega("1A001"), "A-01", null, false)));
        assertTrue(conExistencias.getMessage().contains("tiene existencias"));

        SlotDTO inactivo = slotService.actualizar(a2, new SlotRequestDTO(bodega("1A001"), "A-02", "Vacío", false));
        assertEquals(false, inactivo.activo());
        assertThrows(BusinessRuleException.class, () -> mover(ENTRADA, "LAM/IT", null, a2, "1"));
    }

    @Test
    void entradasSalidasTrasladosYAjustesMantienenLosSaldos() {
        mover(ENTRADA, "lam/it", null, a1, "10");
        mover(SALIDA, "LAM/IT", a1, null, "3");
        mover(TRASLADO, "LAM/IT", a1, a2, "4");
        assertEquals(Map.of("A-01", new BigDecimal("3"), "A-02", new BigDecimal("4")), saldos("LAM/IT"));

        BusinessRuleException insuficiente = assertThrows(BusinessRuleException.class, () -> mover(SALIDA, "LAM/IT", a1, null, "3.5"));
        assertTrue(insuficiente.getMessage().contains("hay 3"), insuficiente.getMessage());
        assertThrows(BusinessRuleException.class, () -> mover(SALIDA, "RESINA-IT", resina, null, "1"));
        assertThrows(SolicitudInvalidaException.class, () -> mover(TRASLADO, "LAM/IT", a1, a1, "1"));
        assertThrows(SolicitudInvalidaException.class, () -> mover(ENTRADA, "LAM/IT", null, a1, "0"));
        assertThrows(SolicitudInvalidaException.class, () -> mover(SALIDA, "LAM/IT", a1, a2, "1"));
        assertThrows(SolicitudInvalidaException.class, () -> mover(CARGA_INICIAL, "LAM/IT", null, a1, "1"));

        // Ajuste por conteo: había 3, se contaron 1.5 → faltante de 1.5 que resta del slot
        MovimientoDTO ajuste = mover(AJUSTE, "LAM/IT", null, a1, "1.5");
        assertEquals("1A001 / A-01", ajuste.origen());
        assertNull(ajuste.destino());
        assertEquals(new BigDecimal("1.5"), ajuste.cantidad());
        assertEquals(new BigDecimal("1.5"), ajuste.cantidadContada());
        assertThrows(BusinessRuleException.class, () -> mover(AJUSTE, "LAM/IT", null, a1, "1.5"));
        // Contar más de lo que hay suma la diferencia
        assertEquals("1A001 / A-02", mover(AJUSTE, "LAM/IT", null, a2, "6").destino());
        assertEquals(Map.of("A-01", new BigDecimal("1.5"), "A-02", new BigDecimal("6")), saldos("LAM/IT"));

        List<MovimientoDTO> bitacora = inventarioService.movimientos("LAM/IT", null, null, PageRequest.of(0, 20)).getContent();
        assertEquals(List.of(AJUSTE, AJUSTE, TRASLADO, SALIDA, ENTRADA), bitacora.stream().map(MovimientoDTO::tipo).toList());
        assertEquals(USUARIO, bitacora.getFirst().usuario());
        assertEquals(1, inventarioService.movimientos("", bodega("1A001"), TipoMovimientoInventario.TRASLADO,
                PageRequest.of(0, 20)).getTotalElements());
        // La suma de la bitácora coincide con los saldos: 10 − 3 − 1.5 + 2 = 7.5
        assertEquals(0, new BigDecimal("7.5").compareTo(jdbc.queryForObject("""
                SELECT SUM(CASE WHEN slot_destino_id IS NOT NULL AND slot_origen_id IS NULL THEN cantidad
                                WHEN slot_origen_id IS NOT NULL AND slot_destino_id IS NULL THEN -cantidad ELSE 0 END)
                FROM Movimiento_Inventario""", BigDecimal.class)));
    }

    @Test
    void laCargaInicialDejaLosSaldosDelArchivoYCreaLosSlots() {
        mover(ENTRADA, "TORN-IT", null, a1, "100");
        mover(ENTRADA, "LAM/IT", null, a2, "7");

        CargaExistenciasDTO carga = cargar("""
                Bodega,Slot,Item,Cantidad
                1A001,A-01,TORN-IT,40
                1A001,a-01,LAM/IT,"2,5"
                1A013,R-09,RESINA-IT,12.5
                1A001,A-02,LAM/IT,7
                """);

        assertEquals(4, carga.filas());
        assertEquals(3, carga.movimientos());   // A-02 ya tenía 7
        assertEquals(1, carga.slotsCreados());
        assertTrue(carga.avisos().getFirst().contains("1A013 / R-09"));
        assertEquals(Map.of("A-01", new BigDecimal("40")), saldos("TORN-IT"));
        assertEquals(Map.of("A-01", new BigDecimal("2.5"), "A-02", new BigDecimal("7")), saldos("LAM/IT"));
        assertEquals(Map.of("R-09", new BigDecimal("12.5")), saldos("RESINA-IT"));

        SolicitudInvalidaException errores = assertThrows(SolicitudInvalidaException.class, () -> cargar("""
                Bodega,Slot,Item,Cantidad
                9Z999,A-01,TORN-IT,1
                1A001,A-01,NO-EXISTE,1
                1A001,A-01,TORN-IT,-1
                1A001,A-01,LAM/IT,1
                1A001,A-01,lam/it,2
                """));
        assertTrue(errores.getMessage().contains("línea 2: la bodega '9Z999'"), errores.getMessage());
        assertTrue(errores.getMessage().contains("línea 3: el ítem 'NO-EXISTE'"), errores.getMessage());
        assertTrue(errores.getMessage().contains("línea 4: la cantidad '-1'"), errores.getMessage());
        assertTrue(errores.getMessage().contains("línea 6: lam/it está repetido"), errores.getMessage());
        assertEquals(Map.of("A-01", new BigDecimal("40")), saldos("TORN-IT"));
    }

    @Test
    void elPedidoDescuentaLasExistenciasDeLaBodegaDeLaLista() {
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-PT1', 'IT-PT1', 'IT-PT1')");
        jdbc.update("""
                INSERT INTO Pedido (filemaker_id, numero_pedido, cliente_id, estado_actual_id, fecha_creacion_fm)
                SELECT 'IT-INV', 'IT-INV', c.id, e.id, SYSUTCDATETIME() FROM Cliente c, Estado_Pedido e
                WHERE c.filemaker_id = 'IT Cliente' AND e.codigo = 'CREADO'""");
        long pedidoId = jdbc.queryForObject("SELECT id FROM Pedido WHERE filemaker_id = 'IT-INV'", Long.class);
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, posicion, pedido_id, producto_id, estado_actual_id,
                                            cantidad_solicitada, precio_unitario)
                SELECT 'IT-INV-1', 1, ?, p.id, e.id, 10, 0 FROM Producto p, Estado_Detalle e
                WHERE p.filemaker_id = 'IT-PT1' AND e.codigo = 'CREADO'""", pedidoId);

        // 20 láminas requeridas en 1A001: hay 8 repartidas en dos slots y 50 en otra bodega (no cuentan)
        mover(ENTRADA, "LAM/IT", null, a1, "5");
        mover(ENTRADA, "LAM/IT", null, a2, "3");
        mover(ENTRADA, "LAM/IT", null, resina, "50");
        mover(ENTRADA, "RESINA-IT", null, resina, "2");

        Map<String, TotalMaterialDTO> totales = listaMaterialesService.materialesPedido(pedidoId).totales().stream()
                .collect(Collectors.toMap(TotalMaterialDTO::codigo, Function.identity()));
        assertEquals(new BigDecimal("20"), totales.get("LAM/IT").cantidad());
        assertEquals(new BigDecimal("8"), totales.get("LAM/IT").disponible());
        assertEquals(new BigDecimal("12"), totales.get("LAM/IT").faltante());
        assertEquals(new BigDecimal("36"), totales.get("TORN-IT").cantidad());     // solo las 12 láminas que faltan × 3
        assertEquals(new BigDecimal("36"), totales.get("TORN-IT").faltante());
        assertEquals(new BigDecimal("5"), totales.get("RESINA-IT").cantidad());    // 10 × 0.5
        assertEquals(new BigDecimal("3"), totales.get("RESINA-IT").faltante());

        // Las existencias de cada pedido no se reservan: consultar otra vez da lo mismo
        MaterialesPedidoDTO otraVez = listaMaterialesService.materialesPedido(pedidoId);
        assertEquals(new BigDecimal("12"), otraVez.totales().stream()
                .filter(t -> t.codigo().equals("LAM/IT")).findFirst().orElseThrow().faltante());
        List<ExistenciaDTO> existencias = inventarioService.existencias("lám", bodega("1A001"), PageRequest.of(0, 10)).getContent();
        assertEquals(List.of("A-01", "A-02"), existencias.stream().map(ExistenciaDTO::slotCodigo).toList());
    }

    private MovimientoDTO mover(TipoMovimientoInventario tipo, String item, Integer origen, Integer destino, String cantidad) {
        return inventarioService.registrar(new MovimientoRequestDTO(tipo, item, origen, destino, new BigDecimal(cantidad),
                null, null), USUARIO);
    }

    private CargaExistenciasDTO cargar(String csv) {
        return cargaExistenciasService.cargar(csv.getBytes(StandardCharsets.UTF_8), USUARIO);
    }

    private Map<String, BigDecimal> saldos(String item) {
        return inventarioService.existencias("", null, PageRequest.of(0, 50)).getContent().stream()
                .filter(e -> e.itemCodigo().equals(item))
                .collect(Collectors.toMap(ExistenciaDTO::slotCodigo, ExistenciaDTO::cantidad));
    }

    private int bodega(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Bodega WHERE codigo = ?", Integer.class, codigo);
    }
}
