package co.com.plast_innova.app_plastinnova_backend.modules.inventory;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.EstadoCompromiso;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ExistenciaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaLineaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.SlotService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.TotalMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.CargaListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
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

import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.ENTRADA;
import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.SALIDA;
import static co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario.TRASLADO;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Producto terminado comprometido por los pedidos en 1A001, contra SQL Server real. Lista: IT-PT1 = 2 × LAM/IT
 * (1A001) + 0.5 kg RESINA-IT (1A013).
 */
@SpringBootTest
class ReservaInventarioIntegrationTest extends PruebaIntegracion {

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
    /** Usuario activo de V2: los cambios de estado de las líneas lo exigen. */
    private static final String USUARIO = "admin@plast-innova.com.co";

    @Autowired private CargaListaMaterialesService cargaListaService;
    @Autowired private ListaMaterialesService listaMaterialesService;
    @Autowired private SlotService slotService;
    @Autowired private InventarioService inventarioService;
    @Autowired private ReservaInventarioService reservaService;
    @Autowired private PedidoService pedidoService;
    @Autowired private JdbcTemplate jdbc;

    private int a1;
    private int a2;
    private int otraBodega;

    @BeforeEach
    void preparar() {
        limpiar();
        cargaListaService.cargar(BODEGAS.getBytes(StandardCharsets.UTF_8), ITEMS.getBytes(StandardCharsets.UTF_8),
                LISTA.getBytes(StandardCharsets.UTF_8), USUARIO);
        a1 = slotService.crear(new SlotRequestDTO(bodega("1A001"), "A-01", null, null)).id();
        a2 = slotService.crear(new SlotRequestDTO(bodega("1A001"), "A-02", null, null)).id();
        otraBodega = slotService.crear(new SlotRequestDTO(bodega("1A013"), "R-01", null, null)).id();
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-PT1', 'IT-PT1', 'IT-PT1')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-SIN', 'IT-SIN', 'Sin ítem')");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Reserva_Inventario", "Movimiento_Inventario", "Historial_Estado_Detalle",
                "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido", "Cliente", "Producto", "Existencia", "Slot",
                "Lista_Material", "Item", "Bodega", "Carga_Lista_Materiales")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void comprometerReservaLoLibreDeLaBodegaSinPasarDeLoSolicitado() {
        // 10 en 1A001 repartidas en dos slots; las 100 de otra bodega no cuentan
        mover(ENTRADA, null, a1, "6");
        mover(ENTRADA, null, a2, "4");
        mover(ENTRADA, null, otraBodega, "100");
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 8);
        linea(p1, 2, "IT-SIN", 3);
        long p2 = pedido("IT-P2");
        long l2 = linea(p2, 1, "IT-PT1", 5);

        ReservasPedidoDTO r1 = reservaService.comprometer(p1, USUARIO);
        assertEquals("1A001", r1.bodega());
        ReservaLineaDTO linea1 = r1.lineas().getFirst();
        assertEquals(new BigDecimal("8"), linea1.reservada());
        assertEquals(new BigDecimal("10"), linea1.existencia());
        assertEquals(new BigDecimal("2"), linea1.libre());
        ReservaLineaDTO sinItem = r1.lineas().get(1);
        assertFalse(sinItem.reservable());
        assertEquals("El producto no está en el maestro de ítems.", sinItem.motivoNoReservable());
        // La línea sin ítem queda pendiente sin reserva
        assertEquals(EstadoCompromiso.PARCIAL, r1.estado());

        // El segundo pedido solo alcanza a tomar las 2 libres
        ReservasPedidoDTO r2 = reservaService.comprometer(p2, USUARIO);
        assertEquals(new BigDecimal("2"), r2.lineas().getFirst().reservada());
        assertEquals(new BigDecimal("2"), r2.lineas().getFirst().maximoReservable());
        assertEquals(EstadoCompromiso.PARCIAL, r2.estado());
        BusinessRuleException sinLibre = assertThrows(BusinessRuleException.class,
                () -> reservaService.reservar(p2, l2, new BigDecimal("3"), USUARIO));
        assertTrue(sinLibre.getMessage().contains("Solo hay 2 de IT-PT1 libres en 1A001"), sinLibre.getMessage());
        assertThrows(BusinessRuleException.class, () -> reservaService.reservar(p1, l1, new BigDecimal("9"), USUARIO));

        // Al bajar la reserva del primero, el segundo puede completar la suya
        reservaService.reservar(p1, l1, new BigDecimal("5"), USUARIO);
        assertEquals(EstadoCompromiso.COMPROMETIDO, reservaService.comprometer(p2, USUARIO).estado());

        // Las salidas y traslados a otra bodega no pueden tomar lo comprometido; los traslados internos sí se hacen
        BusinessRuleException comprometidas = assertThrows(BusinessRuleException.class, () -> mover(SALIDA, a1, null, "1"));
        assertTrue(comprometidas.getMessage().contains("Hay 10 de IT-PT1 comprometidas para pedidos en 1A001"),
                comprometidas.getMessage());
        assertThrows(BusinessRuleException.class, () -> mover(TRASLADO, a1, otraBodega, "1"));
        mover(TRASLADO, a1, a2, "6");
        mover(ENTRADA, null, a1, "3");
        mover(SALIDA, a1, null, "3");

        // Los materiales solo explotan lo que no está comprometido: 3 de 8 → 6 láminas
        Map<String, TotalMaterialDTO> totales = listaMaterialesService.materialesPedido(p1).totales().stream()
                .collect(Collectors.toMap(TotalMaterialDTO::codigo, t -> t));
        assertEquals(new BigDecimal("6"), totales.get("LAM/IT").cantidad());
        assertEquals(new BigDecimal("5"), listaMaterialesService.materialesPedido(p1).lineas().getFirst().reservada());

        assertEquals(2, reservaService.listar("it-pt1", PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, reservaService.listar("IT-P2", PageRequest.of(0, 10)).getTotalElements());
        ReservasPedidoDTO liberado = reservaService.liberar(p2);
        assertEquals(EstadoCompromiso.SIN_COMPROMETER, liberado.estado());
        assertEquals(new BigDecimal("5"), liberado.lineas().getFirst().libre());
    }

    @Test
    void alEntregarLaLineaSaleLoComprometidoYNoSaleLoLibera() {
        mover(ENTRADA, null, a1, "6");
        mover(ENTRADA, null, a2, "4");
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 10);
        long p2 = pedido("IT-P2");
        long l2 = linea(p2, 1, "IT-PT1", 5);
        reservaService.reservar(p1, l1, new BigDecimal("9"), USUARIO);
        reservaService.reservar(p2, l2, new BigDecimal("1"), USUARIO);

        // Entrega 7 piezas: salen del slot con más saldo primero; las otras 2 reservadas quedan libres
        jdbc.update("UPDATE Pedido_Detalle SET estado_actual_id = ? WHERE id = ?", estado("ALMACEN1"), l1);
        pedidoService.transicionarEstadoDetalle(l1, new CambioEstadoDetalleRequestDTO(estado("ENTREGADO"), 7, null), USUARIO);
        assertEquals(Map.of("A-02", new BigDecimal("3")), saldos());
        List<MovimientoDTO> salidas = inventarioService.movimientos("IT-PT1", null, TipoMovimientoInventario.SALIDA,
                PageRequest.of(0, 10)).getContent();
        assertEquals(List.of(new BigDecimal("1"), new BigDecimal("6")), salidas.stream().map(MovimientoDTO::cantidad).toList());
        assertEquals("IT-P1", salidas.getFirst().numeroPedido());
        assertEquals("Despacho", salidas.getFirst().motivo());
        ReservasPedidoDTO entregado = reservaService.reservasPedido(p1);
        assertEquals(BigDecimal.ZERO, entregado.lineas().getFirst().reservada());
        assertEquals("La línea ya está en Entregado.", entregado.lineas().getFirst().motivoNoReservable());
        assertEquals(EstadoCompromiso.NO_APLICA, entregado.estado());

        // No sale: lo comprometido vuelve a quedar libre
        pedidoService.marcarNoSale(l2, new JustificacionRequestDTO("Cliente canceló la referencia"), USUARIO);
        assertEquals(0, jdbc.queryForObject("SELECT COUNT(*) FROM Reserva_Inventario", Integer.class));
        mover(SALIDA, a2, null, "3");
    }

    @Test
    void lasLineasEliminadasEnFileMakerOConMenosCantidadNoSostienenLaReserva() {
        mover(ENTRADA, null, a1, "10");
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 6);
        long l2 = linea(p1, 2, "IT-PT1", 4);
        reservaService.comprometer(p1, USUARIO);

        jdbc.update("UPDATE Pedido_Detalle SET eliminado_en_fm = 1 WHERE id = ?", l2);
        reservaService.liberarLineasEliminadas();
        assertEquals(List.of(l1), jdbc.queryForList("SELECT pedido_detalle_id FROM Reserva_Inventario", Long.class));
        assertEquals(new BigDecimal("4"), reservaService.reservasPedido(p1).lineas().getFirst().libre());
    }

    private MovimientoDTO mover(TipoMovimientoInventario tipo, Integer origen, Integer destino, String cantidad) {
        return inventarioService.registrar(new MovimientoRequestDTO(tipo, "IT-PT1", origen, destino,
                new BigDecimal(cantidad), null, null), USUARIO);
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

    private Map<String, BigDecimal> saldos() {
        return inventarioService.existencias("IT-PT1", null, PageRequest.of(0, 50)).getContent().stream()
                .collect(Collectors.toMap(ExistenciaDTO::slotCodigo, ExistenciaDTO::cantidad));
    }

    private int estado(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Detalle WHERE codigo = ?", Integer.class, codigo);
    }

    private int bodega(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Bodega WHERE codigo = ?", Integer.class, codigo);
    }
}
