package co.com.plast_innova.app_plastinnova_backend.modules.production;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ExistenciaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.SlotService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.CargaListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CorreccionCantidadRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.AvanceOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ComponenteOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EdicionOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.LineaOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.VinculoLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.OrdenProduccionService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Órdenes de producción contra SQL Server real. Lista: IT-PT1 = 2 × LAM/IT (1A001) + 0.5 kg RESINA-IT (1A013).
 */
@SpringBootTest
class OrdenProduccionIntegrationTest extends PruebaIntegracion {

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
            """;
    private static final String LISTA = """
            Secuencia,Item_Padre,Item_Hijo,Cantidad_Hijo,Id_Operacion,Bodega
            1,IT-PT1,LAM/IT,2,,1A001
            2,IT-PT1,RESINA-IT,0.5,,1A013
            """;
    private static final String USUARIO = "admin@plast-innova.com.co";

    @Autowired private CargaListaMaterialesService cargaListaService;
    @Autowired private SlotService slotService;
    @Autowired private InventarioService inventarioService;
    @Autowired private ReservaInventarioService reservaService;
    @Autowired private OrdenProduccionService ordenService;
    @Autowired private PedidoService pedidoService;
    @Autowired private JdbcTemplate jdbc;

    private int a1;
    private int r1;

    @BeforeEach
    void preparar() {
        limpiar();
        cargaListaService.cargar(BODEGAS.getBytes(StandardCharsets.UTF_8), ITEMS.getBytes(StandardCharsets.UTF_8),
                LISTA.getBytes(StandardCharsets.UTF_8), USUARIO);
        a1 = slotService.crear(new SlotRequestDTO(bodega("1A001"), "A-01", null, null)).id();
        r1 = slotService.crear(new SlotRequestDTO(bodega("1A013"), "R-01", null, null)).id();
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-PT1', 'IT-PT1', 'IT-PT1')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-OTRO', 'IT-OTRO', 'Otro')");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Reserva_Inventario", "Movimiento_Inventario", "Remision_Linea", "Remision",
                "Historial_Orden_Produccion", "Orden_Produccion_Linea", "Orden_Produccion", "Historial_Estado_Detalle",
                "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido", "Cliente", "Producto", "Existencia", "Slot",
                "Lista_Material", "Item", "Bodega", "Carga_Lista_Materiales")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void laOrdenArrastraSusLineasConsumeMaterialesYDejaLoProducidoComprometido() {
        mover("LAM/IT", a1, "20");
        mover("RESINA-IT", r1, "3");
        mover("IT-PT1", a1, "1");
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 6);
        long p2 = pedido("IT-P2");
        long l2 = linea(p2, 1, "IT-PT1", 4);
        linea(p2, 2, "IT-OTRO", 4);
        reservaService.comprometer(p2, USUARIO); // la unidad en bodega queda para P2

        OrdenProduccionDTO orden = ordenService.crear(
                new OrdenProduccionRequestDTO("it-pt1", 10, null, "Lote de prueba", null), USUARIO);
        assertTrue(orden.numero().matches("OP-\\d{6}"), orden.numero());
        assertEquals("CREADO", orden.etapa().codigo());
        assertEquals("1A001", orden.bodegaDestino());
        assertEquals(List.of("LAM/IT", "RESINA-IT"), orden.componentes().stream().map(ComponenteOrdenDTO::codigo).toList());

        // A P2 le faltan 3 (tiene 1 comprometida); la línea de otro producto no aparece
        Map<Long, Integer> candidatas = ordenService.candidatas(orden.id()).stream()
                .collect(Collectors.toMap(LineaOrdenDTO::detalleId, LineaOrdenDTO::cantidad));
        assertEquals(Map.of(l1, 6, l2, 3), candidatas);
        BusinessRuleException demasiadas = assertThrows(BusinessRuleException.class,
                () -> ordenService.vincular(orden.id(), new VinculoLineaRequestDTO(l2, 4), USUARIO));
        assertTrue(demasiadas.getMessage().contains("le faltan 3 piezas"), demasiadas.getMessage());
        ordenService.vincular(orden.id(), new VinculoLineaRequestDTO(l1, 6), USUARIO);
        OrdenProduccionDTO vinculada = ordenService.vincular(orden.id(), new VinculoLineaRequestDTO(l2, 3), USUARIO);
        assertEquals(9, vinculada.cantidadAsignada());
        assertTrue(ordenService.candidatas(orden.id()).isEmpty());
        BusinessRuleException reducir = assertThrows(BusinessRuleException.class,
                () -> ordenService.editar(orden.id(), new EdicionOrdenRequestDTO(8, null, null), USUARIO));
        assertTrue(reducir.getMessage().contains("tienen 9 piezas asignadas"), reducir.getMessage());

        // Otra orden no puede tomar la misma línea, y la línea ya no se reporta por separado
        OrdenProduccionDTO otra = ordenService.crear(new OrdenProduccionRequestDTO("IT-PT1", 5, null, null, null), USUARIO);
        BusinessRuleException tomada = assertThrows(BusinessRuleException.class,
                () -> ordenService.vincular(otra.id(), new VinculoLineaRequestDTO(l1, 1), USUARIO));
        assertTrue(tomada.getMessage().contains("ya la atiende la orden " + orden.numero()), tomada.getMessage());
        BusinessRuleException separada = assertThrows(BusinessRuleException.class, () -> pedidoService
                .transicionarEstadoDetalle(l1, new CambioEstadoDetalleRequestDTO(estado("SOLICITADO"), 6, null), USUARIO));
        assertTrue(separada.getMessage().contains("sigue a la orden de producción " + orden.numero()), separada.getMessage());

        // Las etapas: cada línea avanza con su parte
        avanzar(orden.id(), "SOLICITADO", 10);
        avanzar(orden.id(), "EN_ENSAMBLE", 10);
        avanzar(orden.id(), "RESINA", 10);
        OrdenProduccionDTO calidad = avanzar(orden.id(), "CALIDAD", 9);
        assertEquals(9, calidad.cantidadMaximaReportable());
        assertEquals("CALIDAD", codigoEstadoLinea(l1));
        assertEquals(3, jdbc.queryForObject("SELECT cantidad_procesada FROM Pedido_Detalle WHERE id = ?", Integer.class, l2));
        assertThrows(BusinessRuleException.class, () -> avanzar(orden.id(), "ALMACEN1", 10));

        // 9 piezas piden 4.5 kg de resina y hay 3: se rechaza sin mover nada
        BusinessRuleException sinResina = assertThrows(BusinessRuleException.class, () -> avanzar(orden.id(), "ALMACEN1", 9));
        assertTrue(sinResina.getMessage().contains("RESINA-IT en 1A013: requiere 4.5 y hay 3"), sinResina.getMessage());
        assertEquals(new BigDecimal("20"), saldo("LAM/IT", "A-01"));
        assertFalse(ordenService.obtener(orden.id()).componentes().get(1).alcanza());

        mover("RESINA-IT", r1, "2");
        OrdenProduccionDTO terminada = avanzar(orden.id(), "ALMACEN1", 9);
        assertEquals(EstadoOrdenProduccion.TERMINADA, terminada.estado());
        assertTrue(terminada.etapasSiguientes().isEmpty());
        assertEquals(new BigDecimal("2"), saldo("LAM/IT", "A-01"));
        assertEquals(new BigDecimal("0.5"), saldo("RESINA-IT", "R-01"));
        assertEquals(new BigDecimal("9"), saldo("IT-PT1", "PRODUCCION"));
        Map<String, BigDecimal> movimientos = terminada.movimientos().stream()
                .collect(Collectors.toMap(m -> m.itemCodigo() + " " + m.tipo(), MovimientoDTO::cantidad));
        assertEquals(Map.of("LAM/IT SALIDA", new BigDecimal("18"), "RESINA-IT SALIDA", new BigDecimal("4.5"),
                "IT-PT1 ENTRADA", new BigDecimal("9")), movimientos);
        assertTrue(terminada.movimientos().stream().allMatch(m -> orden.numero().equals(m.numeroOrdenProduccion())));

        // Cada línea queda con su parte comprometida (P2: 1 que tenía + 3) y en Almacén 1
        assertEquals(new BigDecimal("6"), reservaService.reservasPedido(p1).lineas().getFirst().reservada());
        assertEquals(new BigDecimal("4"), reservaService.reservasPedido(p2).lineas().getFirst().reservada());
        assertEquals("ALMACEN1", codigoEstadoLinea(l1));
        assertTrue(terminada.lineas().stream().noneMatch(LineaOrdenDTO::activa));

        // Ya terminada no cambia, y lo que registró no se corrige desde la línea
        assertThrows(BusinessRuleException.class,
                () -> ordenService.editar(orden.id(), new EdicionOrdenRequestDTO(12, null, null), USUARIO));
        BusinessRuleException correccion = assertThrows(BusinessRuleException.class, () -> pedidoService
                .corregirCantidadDetalle(l1, new CorreccionCantidadRequestDTO(5, "Una pieza llegó rota"), USUARIO));
        assertTrue(correccion.getMessage().contains("lo registró la orden " + orden.numero()), correccion.getMessage());

        assertEquals(1, ordenService.listar("IT-P2", null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(List.of(otra.numero()), ordenService.listar("", EstadoOrdenProduccion.ABIERTA, null,
                PageRequest.of(0, 10)).map(o -> o.numero()).getContent());
    }

    @Test
    void anularLaOrdenDejaLasLineasEnSuEtapaParaReportarlasPorSeparado() {
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 5);
        OrdenProduccionDTO orden = ordenService.crear(new OrdenProduccionRequestDTO("IT-PT1", 5, null, null, null), USUARIO);
        ordenService.vincular(orden.id(), new VinculoLineaRequestDTO(l1, 5), USUARIO);
        avanzar(orden.id(), "SOLICITADO", 5);
        avanzar(orden.id(), "EN_ENSAMBLE", 4);

        OrdenProduccionDTO anulada = ordenService.anular(orden.id(), "Se cambió el diseño del producto", USUARIO);
        assertEquals(EstadoOrdenProduccion.ANULADA, anulada.estado());
        assertThrows(BusinessRuleException.class, () -> avanzar(orden.id(), "RESINA", 4));
        assertEquals("EN_ENSAMBLE", codigoEstadoLinea(l1));
        pedidoService.transicionarEstadoDetalle(l1, new CambioEstadoDetalleRequestDTO(estado("RESINA"), 4, null), USUARIO);
        assertEquals("RESINA", codigoEstadoLinea(l1));

        // Un semiterminado entra por defecto a la bodega donde lo consume la lista
        assertEquals("1A001", ordenService.crear(new OrdenProduccionRequestDTO("LAM/IT", 3, null, null, null), USUARIO)
                .bodegaDestino());
        assertEquals("1A013", ordenService.crear(new OrdenProduccionRequestDTO("RESINA-IT", 3, null, null, null), USUARIO)
                .bodegaDestino());
    }

    private OrdenProduccionDTO avanzar(long ordenId, String etapa, int cantidad) {
        return ordenService.avanzar(ordenId, new AvanceOrdenRequestDTO(estado(etapa), cantidad, null), USUARIO);
    }

    private void mover(String item, int slot, String cantidad) {
        inventarioService.registrar(new MovimientoRequestDTO(TipoMovimientoInventario.ENTRADA, item, null, slot,
                new BigDecimal(cantidad), null, null), USUARIO);
    }

    private BigDecimal saldo(String item, String slot) {
        return inventarioService.existencias(item, null, PageRequest.of(0, 50)).getContent().stream()
                .filter(e -> e.slotCodigo().equals(slot) && e.itemCodigo().equals(item))
                .map(ExistenciaDTO::cantidad).findFirst().orElse(BigDecimal.ZERO);
    }

    private long pedido(String numero) {
        jdbc.update("""
                INSERT INTO Pedido (filemaker_id, numero_pedido, cliente_id, estado_actual_id, fecha_creacion_fm)
                SELECT ?, ?, c.id, e.id, SYSUTCDATETIME() FROM Cliente c, Estado_Pedido e
                WHERE c.filemaker_id = 'IT Cliente' AND e.codigo = 'CREADO'""", numero, numero);
        return jdbc.queryForObject("SELECT id FROM Pedido WHERE filemaker_id = ?", Long.class, numero);
    }

    private long linea(long pedidoId, int posicion, String producto, int cantidad) {
        String filemakerId = pedidoId + "-" + posicion;
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, posicion, pedido_id, producto_id, estado_actual_id,
                                            cantidad_solicitada, precio_unitario)
                SELECT ?, ?, ?, p.id, e.id, ?, 0 FROM Producto p, Estado_Detalle e
                WHERE p.filemaker_id = ? AND e.codigo = 'CREADO'""", filemakerId, posicion, pedidoId, cantidad, producto);
        return jdbc.queryForObject("SELECT id FROM Pedido_Detalle WHERE filemaker_id = ?", Long.class, filemakerId);
    }

    private String codigoEstadoLinea(long detalleId) {
        return jdbc.queryForObject("""
                SELECT e.codigo FROM Pedido_Detalle d JOIN Estado_Detalle e ON e.id = d.estado_actual_id
                WHERE d.id = ?""", String.class, detalleId);
    }

    private int estado(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Detalle WHERE codigo = ?", Integer.class, codigo);
    }

    private int bodega(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Bodega WHERE codigo = ?", Integer.class, codigo);
    }
}
