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
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.AsignacionItemsRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.BajaProcesoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.CantidadProductoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ComponenteOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EntregaProcesoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EtapaProductoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.LineaOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProductoOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProductoOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.RutaProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.VinculoLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.EntregaProcesoService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.OrdenProduccionService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.RutaProduccionService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.TipoEntregaProceso;
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
 * Órdenes de producción contra SQL Server real. Lista: IT-PT1 = 2 × LAM/IT (1A001) + 0.5 kg RESINA-IT (1A013);
 * IT-PT2 no tiene componentes y sigue la ruta «Sin resina».
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
            4,IT-PT2,Aro IT,ProductoTerminado,Und
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
    @Autowired private EntregaProcesoService entregaService;
    @Autowired private RutaProduccionService rutaService;
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
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-PT2', 'IT-PT2', 'IT-PT2')");
        jdbc.update("INSERT INTO Producto (filemaker_id, sku, nombre) VALUES ('IT-OTRO', 'IT-OTRO', 'Otro')");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Reserva_Inventario", "Movimiento_Inventario", "Remision_Linea", "Remision",
                "Entrega_Proceso", "Historial_Orden_Produccion", "Orden_Produccion_Linea", "Orden_Produccion_Etapa",
                "Orden_Produccion_Producto", "Orden_Produccion", "Item_Ruta", "Ruta_Produccion_Etapa",
                "Ruta_Produccion", "Historial_Estado_Detalle",
                "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido", "Cliente", "Producto", "Existencia", "Slot",
                "Lista_Material", "Item", "Bodega", "Carga_Lista_Materiales")) {
            jdbc.update("DELETE FROM " + tabla);
        }
    }

    @Test
    void lasPiezasAvanzanPorEntregasEntreProcesosYLasLineasLasSiguen() {
        mover("LAM/IT", a1, "20");
        mover("RESINA-IT", r1, "2");
        mover("IT-PT1", a1, "1");
        rutaService.asignar(rutaService.crear(new RutaProduccionRequestDTO("Sin resina", null,
                List.of(estado("SOLICITADO"), estado("EN_ENSAMBLE"), estado("CALIDAD")), null)).id(),
                new AsignacionItemsRequestDTO(List.of("IT-PT2")));
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 6);
        long l2 = linea(p1, 2, "IT-PT2", 4);
        long p2 = pedido("IT-P2");
        long l3 = linea(p2, 1, "IT-PT1", 4);
        linea(p2, 2, "IT-OTRO", 4);
        reservaService.comprometer(p2, USUARIO); // la unidad en bodega queda para P2

        // Pendientes de los dos pedidos: a P2 le faltan 3; IT-OTRO no está en el maestro de ítems
        Map<Long, LineaOrdenDTO> pendientes = ordenService.lineasPendientes("IT-P", PageRequest.of(0, 20)).getContent()
                .stream().collect(Collectors.toMap(LineaOrdenDTO::detalleId, l -> l));
        assertEquals(6, pendientes.get(l1).cantidad());
        assertEquals(3, pendientes.get(l3).cantidad());
        assertTrue(pendientes.values().stream().anyMatch(l -> "IT-OTRO".equals(l.itemCodigo())
                && l.motivo().contains("no está en el maestro")), pendientes.toString());

        OrdenProduccionDTO orden = ordenService.crear(new OrdenProduccionRequestDTO(null, "Lote de prueba", List.of(
                new ProductoOrdenRequestDTO("IT-PT1", 10, null,
                        List.of(new VinculoLineaRequestDTO(l1, 6), new VinculoLineaRequestDTO(l3, 3))),
                new ProductoOrdenRequestDTO("IT-PT2", null, null, List.of(new VinculoLineaRequestDTO(l2, 4))))), USUARIO);
        assertTrue(orden.numero().matches("OP-\\d{6}"), orden.numero());
        assertEquals("ALMACEN1", orden.etapaRecepcion().codigo());
        ProductoOrdenDTO pt1 = producto(orden, "IT-PT1");
        ProductoOrdenDTO pt2 = producto(orden, "IT-PT2");
        assertEquals(Map.of("SOLICITADO", 10, "EN_ENSAMBLE", 0, "RESINA", 0, "CALIDAD", 0), piezas(pt1));
        assertEquals(List.of("SOLICITADO", "EN_ENSAMBLE", "CALIDAD"),
                pt2.etapas().stream().map(e -> e.etapa().codigo()).toList());
        assertEquals("Sin resina", pt2.ruta());
        assertEquals(4, pt2.cantidad());
        assertEquals(1, pt1.porAsignar());
        assertEquals(List.of("LAM/IT", "RESINA-IT"), pt1.componentes().stream().map(ComponenteOrdenDTO::codigo).toList());
        assertEquals("SOLICITADO", codigoEstadoLinea(l1));
        assertTrue(ordenService.lineasPendientes("IT-P", PageRequest.of(0, 20)).getContent().stream()
                .noneMatch(l -> l.detalleId().equals(l1)));

        // La línea no se reporta por separado, ni otra orden la toma
        BusinessRuleException separada = assertThrows(BusinessRuleException.class, () -> pedidoService
                .transicionarEstadoDetalle(l1, new CambioEstadoDetalleRequestDTO(estado("EN_ENSAMBLE"), 6, null), USUARIO));
        assertTrue(separada.getMessage().contains("sigue a la orden de producción " + orden.numero()), separada.getMessage());
        BusinessRuleException tomada = assertThrows(BusinessRuleException.class, () -> ordenService.crear(
                new OrdenProduccionRequestDTO(null, null, List.of(new ProductoOrdenRequestDTO("IT-PT1", 2, null,
                        List.of(new VinculoLineaRequestDTO(l1, 1))))), USUARIO));
        assertTrue(tomada.getMessage().contains("ya la atiende la orden " + orden.numero()), tomada.getMessage());

        // Entregas parciales: cada línea toma las piezas más avanzadas que quedan y va en la más atrasada
        entregar(orden.id(), pt1.id(), "SOLICITADO", "EN_ENSAMBLE", 10, null);
        OrdenProduccionDTO parcial = entregar(orden.id(), pt1.id(), "EN_ENSAMBLE", "RESINA", 7, null);
        assertEquals(Map.of("SOLICITADO", 0, "EN_ENSAMBLE", 3, "RESINA", 7, "CALIDAD", 0), piezas(producto(parcial, "IT-PT1")));
        assertEquals("RESINA", codigoEstadoLinea(l1));
        assertEquals("EN_ENSAMBLE", codigoEstadoLinea(l3));
        assertEquals(3, jdbc.queryForObject("SELECT cantidad_procesada FROM Pedido_Detalle WHERE id = ?", Integer.class, l3));
        BusinessRuleException salto = assertThrows(BusinessRuleException.class,
                () -> entregar(orden.id(), pt1.id(), "EN_ENSAMBLE", "CALIDAD", 1, null));
        assertTrue(salto.getMessage().contains("no puede saltarse"), salto.getMessage());
        assertThrows(BusinessRuleException.class, () -> entregar(orden.id(), pt1.id(), "RESINA", "CALIDAD", 8, null));
        assertThrows(BusinessRuleException.class, () -> entregar(orden.id(), pt1.id(), "RESINA", "ALMACEN1", 1, null));

        // Calidad devuelve piezas a Resina: solo con motivo
        entregar(orden.id(), pt1.id(), "RESINA", "CALIDAD", 7, null);
        assertThrows(BusinessRuleException.class, () -> entregar(orden.id(), pt1.id(), "CALIDAD", "RESINA", 2, null));
        entregar(orden.id(), pt1.id(), "CALIDAD", "RESINA", 2, "Burbujas en la resina");

        // 5 piezas piden 2.5 kg de resina y hay 2: se rechaza sin mover nada
        BusinessRuleException sinResina = assertThrows(BusinessRuleException.class,
                () -> entregar(orden.id(), pt1.id(), "CALIDAD", "ALMACEN1", 5, null));
        assertTrue(sinResina.getMessage().contains("RESINA-IT en 1A013: requiere 2.5 y hay 2"), sinResina.getMessage());
        assertEquals(new BigDecimal("20"), saldo("LAM/IT", "A-01"));

        mover("RESINA-IT", r1, "3");
        OrdenProduccionDTO recibida = entregar(orden.id(), pt1.id(), "CALIDAD", "ALMACEN1", 5, null);
        assertEquals(5, producto(recibida, "IT-PT1").cantidadRecibida());
        assertEquals(new BigDecimal("5"), saldo("IT-PT1", "PRODUCCION"));
        assertEquals(new BigDecimal("10"), saldo("LAM/IT", "A-01"));
        // Las 5 son para P1 (primera en asignarse); su pieza que falta está en Resina
        assertEquals(new BigDecimal("5"), reservaService.reservasPedido(p1).lineas().stream()
                .filter(l -> l.detalleId().equals(l1)).findFirst().orElseThrow().reservada());
        assertEquals("RESINA", codigoEstadoLinea(l1));

        // Baja de una pieza en Ensamble: sale de la orden y consume sus componentes
        assertThrows(BusinessRuleException.class, () -> entregaService.darDeBaja(orden.id(),
                new BajaProcesoRequestDTO(pt1.id(), estado("EN_ENSAMBLE"), 4, "Rota"), USUARIO));
        OrdenProduccionDTO baja = entregaService.darDeBaja(orden.id(),
                new BajaProcesoRequestDTO(pt1.id(), estado("EN_ENSAMBLE"), 1, "Se partió el aro"), USUARIO);
        assertEquals(1, producto(baja, "IT-PT1").cantidadBaja());
        assertEquals(new BigDecimal("8"), saldo("LAM/IT", "A-01"));
        assertEquals(TipoEntregaProceso.BAJA, baja.entregas().getFirst().tipo());

        // IT-PT2 no pasa por Resina; al llegar al almacén su línea queda comprometida y deja de seguir la orden
        entregar(orden.id(), pt2.id(), "SOLICITADO", "EN_ENSAMBLE", 4, null);
        assertThrows(BusinessRuleException.class, () -> entregar(orden.id(), pt2.id(), "EN_ENSAMBLE", "RESINA", 4, null));
        entregar(orden.id(), pt2.id(), "EN_ENSAMBLE", "CALIDAD", 4, null);
        OrdenProduccionDTO aros = entregar(orden.id(), pt2.id(), "CALIDAD", "ALMACEN1", 4, null);
        assertTrue(producto(aros, "IT-PT2").terminado());
        assertEquals(EstadoOrdenProduccion.ABIERTA, aros.estado());
        assertEquals("ALMACEN1", codigoEstadoLinea(l2));
        assertTrue(producto(aros, "IT-PT2").lineas().stream().noneMatch(LineaOrdenDTO::activa));

        // El resto de IT-PT1 llega al almacén: completa P1 y P2 y la orden termina
        entregar(orden.id(), pt1.id(), "EN_ENSAMBLE", "RESINA", 2, null);
        entregar(orden.id(), pt1.id(), "RESINA", "CALIDAD", 4, null);
        OrdenProduccionDTO terminada = entregar(orden.id(), pt1.id(), "CALIDAD", "ALMACEN1", 4, null);
        assertEquals(EstadoOrdenProduccion.TERMINADA, terminada.estado());
        assertEquals(new BigDecimal("0"), saldo("LAM/IT", "A-01"));
        assertEquals(new BigDecimal("0"), saldo("RESINA-IT", "R-01"));
        assertEquals(new BigDecimal("6"), reservaService.reservasPedido(p1).lineas().stream()
                .filter(l -> l.detalleId().equals(l1)).findFirst().orElseThrow().reservada());
        assertEquals(new BigDecimal("4"), reservaService.reservasPedido(p2).lineas().getFirst().reservada());
        assertEquals("ALMACEN1", codigoEstadoLinea(l1));
        assertEquals("ALMACEN1", codigoEstadoLinea(l3));
        assertTrue(terminada.movimientos().stream().allMatch(m -> orden.numero().equals(m.numeroOrdenProduccion())));
        assertThrows(BusinessRuleException.class, () -> entregar(orden.id(), pt1.id(), "SOLICITADO", "EN_ENSAMBLE", 1, null));

        assertEquals(1, ordenService.listar("IT-P2", null, null, PageRequest.of(0, 10)).getTotalElements());
        assertEquals(1, ordenService.listar("IT-PT2", null, null, PageRequest.of(0, 10)).getTotalElements());
    }

    @Test
    void cantidadesProductosYAnulacion() {
        long p1 = pedido("IT-P1");
        long l1 = linea(p1, 1, "IT-PT1", 5);
        OrdenProduccionDTO orden = ordenService.crear(new OrdenProduccionRequestDTO(null, null, List.of(
                new ProductoOrdenRequestDTO("IT-PT1", null, null, List.of(new VinculoLineaRequestDTO(l1, 5))),
                new ProductoOrdenRequestDTO("LAM/IT", 3, null, null),
                new ProductoOrdenRequestDTO("RESINA-IT", 3, null, null))), USUARIO);
        // Un semiterminado entra por defecto a la bodega donde lo consume la lista
        assertEquals("1A001", producto(orden, "LAM/IT").bodegaDestino());
        assertEquals("1A013", producto(orden, "RESINA-IT").bodegaDestino());
        long pt1 = producto(orden, "IT-PT1").id();

        BusinessRuleException reducir = assertThrows(BusinessRuleException.class,
                () -> ordenService.cambiarCantidad(orden.id(), pt1, new CantidadProductoRequestDTO(4), USUARIO));
        assertTrue(reducir.getMessage().contains("esperan 5 piezas"), reducir.getMessage());
        OrdenProduccionDTO mas = ordenService.cambiarCantidad(orden.id(), pt1, new CantidadProductoRequestDTO(8), USUARIO);
        assertEquals(8, piezas(producto(mas, "IT-PT1")).get("SOLICITADO"));
        assertThrows(BusinessRuleException.class, () -> ordenService.quitarProducto(orden.id(), pt1, USUARIO));
        assertEquals(2, ordenService.quitarProducto(orden.id(), producto(orden, "RESINA-IT").id(), USUARIO)
                .productos().size());

        // Un vínculo por línea y producto de su mismo ítem
        assertThrows(BusinessRuleException.class, () -> ordenService.agregarProducto(orden.id(),
                new ProductoOrdenRequestDTO("IT-PT1", 2, null, null), USUARIO));

        // 4 piezas avanzan: la línea tiene una pieza aún en Solicitado y va ahí
        entregar(orden.id(), pt1, "SOLICITADO", "EN_ENSAMBLE", 4, null);
        assertEquals("SOLICITADO", codigoEstadoLinea(l1));
        entregar(orden.id(), pt1, "SOLICITADO", "EN_ENSAMBLE", 4, null);
        assertEquals("EN_ENSAMBLE", codigoEstadoLinea(l1));

        OrdenProduccionDTO anulada = ordenService.anular(orden.id(), "Se cambió el diseño del producto", USUARIO);
        assertEquals(EstadoOrdenProduccion.ANULADA, anulada.estado());
        assertThrows(BusinessRuleException.class, () -> entregar(orden.id(), pt1, "EN_ENSAMBLE", "RESINA", 1, null));
        pedidoService.transicionarEstadoDetalle(l1, new CambioEstadoDetalleRequestDTO(estado("RESINA"), 5, null), USUARIO);
        assertEquals("RESINA", codigoEstadoLinea(l1));

        // Una línea que avanzó por su cuenta ya no entra en una orden
        BusinessRuleException avanzada = assertThrows(BusinessRuleException.class, () -> ordenService.crear(
                new OrdenProduccionRequestDTO(null, null, List.of(new ProductoOrdenRequestDTO("IT-PT1", 5, null,
                        List.of(new VinculoLineaRequestDTO(l1, 5))))), USUARIO));
        assertTrue(avanzada.getMessage().contains("ya avanzó por su cuenta"), avanzada.getMessage());

        // Las rutas solo llevan procesos: la etapa de almacén va sola al final
        assertThrows(BusinessRuleException.class, () -> rutaService.crear(new RutaProduccionRequestDTO("Mala", null,
                List.of(estado("EN_ENSAMBLE"), estado("ALMACEN1")), null)));
    }

    private OrdenProduccionDTO entregar(long ordenId, long productoId, String origen, String destino, int cantidad,
                                        String observaciones) {
        return entregaService.entregar(ordenId, new EntregaProcesoRequestDTO(productoId, estado(origen), estado(destino),
                cantidad, observaciones), USUARIO);
    }

    private static ProductoOrdenDTO producto(OrdenProduccionDTO orden, String item) {
        return orden.productos().stream().filter(p -> p.itemCodigo().equals(item)).findFirst().orElseThrow();
    }

    private static Map<String, Integer> piezas(ProductoOrdenDTO producto) {
        return producto.etapas().stream().collect(Collectors.toMap(e -> e.etapa().codigo(), EtapaProductoDTO::cantidad));
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
