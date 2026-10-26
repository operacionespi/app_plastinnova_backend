package co.com.plast_innova.app_plastinnova_backend.modules.delivery;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.EntregasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.LineaEntregaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.LineaRemisionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.services.RemisionService;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.EstadoRemision;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ExistenciaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.SlotService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.CargaListaMaterialesService;
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
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Remisiones contra SQL Server real: salida de 1A001, paso a Entregado y anulación. */
@SpringBootTest
class RemisionIntegrationTest extends PruebaIntegracion {

    private static final String BODEGAS = """
            f150_id,f150_descripcion,f150_descripcion_corta
            1A001,Almacen PT,AlmacenPT
            """;
    private static final String ITEMS = """
            f120_id,f120_referencia,f120_descripcion,f120_id_tipo_inv_serv,f120_id_unidad_inventario
            1,IT-PT1,Lámpara IT,ProductoTerminado,Und
            2,LAM/IT,Lámina IT,Semiterminado,Und
            """;
    private static final String LISTA = """
            Secuencia,Item_Padre,Item_Hijo,Cantidad_Hijo,Id_Operacion,Bodega
            1,IT-PT1,LAM/IT,2,,1A001
            """;
    private static final String USUARIO = "admin@plast-innova.com.co";

    @Autowired private CargaListaMaterialesService cargaListaService;
    @Autowired private SlotService slotService;
    @Autowired private InventarioService inventarioService;
    @Autowired private ReservaInventarioService reservaService;
    @Autowired private RemisionService remisionService;
    @Autowired private JdbcTemplate jdbc;

    private int a1;
    private int a2;

    @BeforeEach
    void preparar() {
        limpiar();
        cargaListaService.cargar(BODEGAS.getBytes(StandardCharsets.UTF_8), ITEMS.getBytes(StandardCharsets.UTF_8),
                LISTA.getBytes(StandardCharsets.UTF_8), USUARIO);
        a1 = slotService.crear(new SlotRequestDTO(bodega("1A001"), "A-01", null, null)).id();
        a2 = slotService.crear(new SlotRequestDTO(bodega("1A001"), "A-02", null, null)).id();
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-PT1', 'IT-PT1', 'IT-PT1')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-SIN', 'IT-SIN', 'Sin ítem')");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Reserva_Inventario", "Movimiento_Inventario", "Remision_Linea", "Remision",
                "Historial_Estado_Detalle", "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido", "Cliente",
                "Producto", "Existencia", "Slot", "Lista_Material", "Item", "Bodega", "Carga_Lista_Materiales")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void laRemisionEntregaLasLineasYAnularlaDevuelveTodo() {
        entrada(a1, "8");
        entrada(a2, "2");
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 6);
        long l2 = linea(p1, 2, "IT-SIN", 2);
        jdbc.update("UPDATE Pedido_Detalle SET estado_actual_id = ?, cantidad_procesada = 6, avance_reportado = 1 WHERE id = ?",
                estado("ALMACEN1"), l1);
        reservaService.reservar(p1, l1, new BigDecimal("4"), USUARIO);
        // Otro pedido compromete 3: solo quedan 3 libres
        long p2 = pedido("IT-P2");
        long otra = linea(p2, 1, "IT-PT1", 3);
        reservaService.comprometer(p2, USUARIO);

        EntregasPedidoDTO entregas = remisionService.entregasPedido(p1);
        Map<Long, LineaEntregaDTO> lineas = entregas.lineas().stream()
                .collect(Collectors.toMap(LineaEntregaDTO::detalleId, l -> l));
        assertEquals(6, lineas.get(l1).maximoEntregable());
        assertEquals(new BigDecimal("3"), lineas.get(l1).libre());
        assertTrue(lineas.get(l2).sinInventario());
        assertEquals(2, lineas.get(l2).maximoEntregable());
        assertNull(entregas.motivoCerrado());

        assertThrows(SolicitudInvalidaException.class, () -> remisionService.emitir(p1, new RemisionRequestDTO(null, null,
                null, List.of(new LineaRemisionRequestDTO(l1, 1), new LineaRemisionRequestDTO(l1, 1))), USUARIO));
        BusinessRuleException demasiadas = assertThrows(BusinessRuleException.class, () -> emitir(p1, Map.of(l1, 7)));
        assertTrue(demasiadas.getMessage().contains("le faltan 6 por entregar"), demasiadas.getMessage());

        RemisionDTO remision = remisionService.emitir(p1, new RemisionRequestDTO("Transportes IT", "abc 123",
                "Entrega completa", List.of(new LineaRemisionRequestDTO(l1, 6), new LineaRemisionRequestDTO(l2, 2))), USUARIO);
        assertTrue(remision.numero().matches("REM-\\d{6}"), remision.numero());
        assertEquals("ABC 123", remision.placa());
        assertEquals(8, remision.totalPiezas());
        assertEquals("ENTREGADO", codigoEstadoLinea(l1));
        assertEquals("ENTREGADO", codigoEstadoLinea(l2));
        // 4 comprometidas + 2 libres; lo de P2 sigue intacto
        assertEquals(new BigDecimal("4"), existencia());
        assertEquals(new BigDecimal("3"), reservaService.reservasPedido(p2).lineas().getFirst().reservada());
        assertEquals(List.of(otra), jdbc.queryForList("SELECT pedido_detalle_id FROM Reserva_Inventario", Long.class));
        assertEquals(6, jdbc.queryForObject("SELECT cantidad_entregada FROM Pedido_Detalle WHERE id = ?", Integer.class, l1));
        BusinessRuleException entregada = assertThrows(BusinessRuleException.class, () -> emitir(p1, Map.of(l1, 1)));
        assertTrue(entregada.getMessage().contains("ya se entregó completa"), entregada.getMessage());

        // Anular: vuelve a los slots, queda comprometido para la línea y la línea regresa a Almacén 1
        RemisionDTO anulada = remisionService.anular(remision.id(), "El cliente no recibió la mercancía", USUARIO);
        assertEquals(EstadoRemision.ANULADA, anulada.estado());
        assertEquals(new BigDecimal("10"), existencia());
        assertEquals("ALMACEN1", codigoEstadoLinea(l1));
        assertEquals("CREADO", codigoEstadoLinea(l2));
        assertEquals(new BigDecimal("6"), reservaService.reservasPedido(p1).lineas().getFirst().reservada());
        assertEquals(0, jdbc.queryForObject("SELECT cantidad_entregada FROM Pedido_Detalle WHERE id = ?", Integer.class, l1));
        assertEquals("DEVOLUCION", jdbc.queryForObject("""
                SELECT TOP 1 tipo FROM Historial_Estado_Detalle WHERE pedido_detalle_id = ? ORDER BY id DESC""",
                String.class, l1));
        assertThrows(BusinessRuleException.class,
                () -> remisionService.anular(remision.id(), "Otra vez por error del usuario", USUARIO));

        // Entrega parcial: la línea sigue en su etapa y lo comprometido baja
        emitir(p1, Map.of(l1, 2));
        assertEquals("ALMACEN1", codigoEstadoLinea(l1));
        assertEquals(new BigDecimal("4"), reservaService.reservasPedido(p1).lineas().getFirst().reservada());
        assertEquals(2, remisionService.entregasPedido(p1).remisiones().size());
        assertEquals(2, remisionService.listar("it-p1", PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void unPedidoFacturadoNoAdmiteRemisiones() {
        entrada(a1, "5");
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 5);
        jdbc.update("UPDATE Pedido SET fecha_cierre = SYSUTCDATETIME(), estado_actual_id = "
                + "(SELECT id FROM Estado_Pedido WHERE codigo = 'FACTURADO') WHERE id = ?", p1);
        assertEquals("El pedido está cerrado en Facturado.", remisionService.entregasPedido(p1).motivoCerrado());
        assertThrows(BusinessRuleException.class, () -> emitir(p1, Map.of(l1, 1)));
    }

    private RemisionDTO emitir(long pedidoId, Map<Long, Integer> cantidades) {
        return remisionService.emitir(pedidoId, new RemisionRequestDTO(null, null, null, cantidades.entrySet().stream()
                .map(e -> new LineaRemisionRequestDTO(e.getKey(), e.getValue())).toList()), USUARIO);
    }

    private void entrada(int slot, String cantidad) {
        inventarioService.registrar(new MovimientoRequestDTO(TipoMovimientoInventario.ENTRADA, "IT-PT1", null, slot,
                new BigDecimal(cantidad), null, null), USUARIO);
    }

    private BigDecimal existencia() {
        return inventarioService.existencias("IT-PT1", null, PageRequest.of(0, 50)).getContent().stream()
                .map(ExistenciaDTO::cantidad).reduce(BigDecimal.ZERO, BigDecimal::add);
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
