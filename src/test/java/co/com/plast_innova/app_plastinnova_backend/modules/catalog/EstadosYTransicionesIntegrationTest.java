package co.com.plast_innova.app_plastinnova_backend.modules.catalog;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.ResultadoEliminacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.TransicionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosPedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.HistorialEstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.support.PruebaIntegracion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reglas de estados contra SQL Server real: consulta de uso, índice único del estado inicial y cambios de estado
 * con historial. Usa los estados y transiciones de V2 y el usuario administrador inicial.
 */
@SpringBootTest
class EstadosYTransicionesIntegrationTest extends PruebaIntegracion {

    private static final String ADMIN = "admin@plast-innova.com.co";

    @Autowired private PedidoService pedidoService;
    @Autowired private GestionEstadosPedidoService estadosPedido;
    @Autowired private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Historial_Estado_Detalle", "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido",
                "Cliente", "Producto")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        // Estados creados por estas pruebas (prefijo IT_) y estado inicial de V2
        jdbc.update("""
                DELETE t FROM Transicion_Estado_Pedido t
                JOIN Estado_Pedido o ON o.id = t.estado_origen_id JOIN Estado_Pedido d ON d.id = t.estado_destino_id
                WHERE o.codigo LIKE 'IT[_]%' OR d.codigo LIKE 'IT[_]%'""");
        jdbc.update("UPDATE Estado_Pedido SET es_inicial = 0 WHERE codigo LIKE 'IT[_]%'");
        jdbc.update("UPDATE Estado_Pedido SET es_inicial = 1 WHERE codigo = 'CREADO'");
        jdbc.update("DELETE FROM Estado_Pedido WHERE codigo LIKE 'IT[_]%'");
    }

    @Test
    void cambiaEstadosSoloPorTransicionesPermitidasYRegistraHistorial() {
        long pedidoId = crearPedido("CREADO");
        long detalleId = crearLinea(pedidoId, "CREADO", 10);

        // Salto no configurado: CREADO → FACTURADO
        BusinessRuleException salto = assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoPedido(pedidoId, new CambioEstadoPedidoRequestDTO(estadoPedido("FACTURADO"), null), ADMIN));
        assertEquals("No se permite pasar de Creado a Facturado; los estados permitidos son: En alistamiento.", salto.getMessage());

        for (String codigo : List.of("EN_ALISTAMIENTO", "EN_DESPACHOS", "PARA_DESPACHAR")) {
            pedidoService.transicionarEstadoPedido(pedidoId, new CambioEstadoPedidoRequestDTO(estadoPedido(codigo), null), ADMIN);
            assertNull(texto("SELECT CONVERT(VARCHAR(30), fecha_cierre, 126) FROM Pedido WHERE id = " + pedidoId));
        }
        pedidoService.transicionarEstadoPedido(pedidoId, new CambioEstadoPedidoRequestDTO(estadoPedido("FACTURADO"), "Factura 123"), ADMIN);
        assertNotNull(texto("SELECT CONVERT(VARCHAR(30), fecha_cierre, 126) FROM Pedido WHERE id = " + pedidoId));
        assertEquals(4L, numero("SELECT version FROM Pedido WHERE id = " + pedidoId)); // un incremento por cambio (@Version)

        List<HistorialEstadoResponseDTO> historial = pedidoService.obtenerHistorialPedido(pedidoId);
        assertEquals(4, historial.size());
        HistorialEstadoResponseDTO ultimo = historial.getFirst();
        assertEquals("FACTURADO", ultimo.estado().codigo());
        assertEquals("PARA_DESPACHAR", ultimo.estadoAnterior().codigo());
        assertEquals(OrigenCambio.USUARIO, ultimo.origen());
        assertEquals(ADMIN, ultimo.usuarioEmail());
        assertEquals("Factura 123", ultimo.observaciones());
        assertEquals("EN_ALISTAMIENTO", historial.getLast().estado().codigo());

        // Línea: la cantidad no puede superar la solicitada
        int solicitado = estadoDetalle("SOLICITADO");
        assertThrows(BusinessRuleException.class, () ->
                pedidoService.transicionarEstadoDetalle(detalleId, new CambioEstadoDetalleRequestDTO(solicitado, 11, null), ADMIN));
        pedidoService.transicionarEstadoDetalle(detalleId, new CambioEstadoDetalleRequestDTO(solicitado, 10, "OK"), ADMIN);

        List<HistorialEstadoResponseDTO> historialLinea = pedidoService.obtenerHistorialDetalle(detalleId);
        assertEquals(1, historialLinea.size());
        assertEquals("CREADO", historialLinea.getFirst().estadoAnterior().codigo());
        assertEquals(10, historialLinea.getFirst().cantidadProcesada());
        assertEquals(10L, numero("SELECT cantidad_procesada FROM Pedido_Detalle WHERE id = " + detalleId));
    }

    @Test
    void cambiarElEstadoInicialRespetaElIndiceUnico() {
        EstadoCatalogoResponseDTO nuevo = estadosPedido.crear(
                new EstadoCatalogoRequestDTO("it revisión", "IT Revisión", null, 5, "#123abc", true, false));

        assertEquals("IT_REVISION", nuevo.codigo());
        assertEquals(1L, numero("SELECT COUNT(*) FROM Estado_Pedido WHERE es_inicial = 1 AND activo = 1"));
        assertEquals("IT_REVISION", texto("SELECT codigo FROM Estado_Pedido WHERE es_inicial = 1"));

        // Volver a marcar CREADO como inicial (UPDATE + UPDATE)
        int creado = estadoPedido("CREADO");
        estadosPedido.actualizar(creado, new EstadoCatalogoRequestDTO("CREADO", "Creado", null, 10, "#9E9E9E", true, false));
        assertEquals("CREADO", texto("SELECT codigo FROM Estado_Pedido WHERE es_inicial = 1"));
    }

    @Test
    void eliminarBorraLoNoUsadoYDesactivaLoUsado() {
        int creado = estadoPedido("CREADO");

        EstadoCatalogoResponseDTO libre = estadosPedido.crear(new EstadoCatalogoRequestDTO("IT_LIBRE", "IT Libre", null, 90, null, false, false));
        estadosPedido.crearTransicion(new TransicionRequestDTO(creado, libre.id()));
        ResultadoEliminacionDTO borrado = estadosPedido.eliminar(libre.id());
        assertEquals(ResultadoEliminacionDTO.Accion.ELIMINADO, borrado.accion());
        assertEquals(0L, numero("SELECT COUNT(*) FROM Estado_Pedido WHERE codigo = 'IT_LIBRE'"));

        EstadoCatalogoResponseDTO usado = estadosPedido.crear(new EstadoCatalogoRequestDTO("IT_USADO", "IT Usado", null, 91, null, false, false));
        estadosPedido.crearTransicion(new TransicionRequestDTO(creado, usado.id()));
        long pedidoId = crearPedido("CREADO");
        pedidoService.transicionarEstadoPedido(pedidoId, new CambioEstadoPedidoRequestDTO(usado.id(), null), ADMIN);

        ResultadoEliminacionDTO desactivado = estadosPedido.eliminar(usado.id());
        assertEquals(ResultadoEliminacionDTO.Accion.DESACTIVADO, desactivado.accion());
        assertEquals(0L, numero("SELECT CAST(activo AS INT) FROM Estado_Pedido WHERE codigo = 'IT_USADO'"));
        // Ya no se ofrece como destino, pero el pedido que está en él puede salir por sus transiciones
        assertTrue(estadosPedido.destinosPermitidos(creado).stream().noneMatch(e -> e.id().equals(usado.id())));

        // El estado inicial no se puede eliminar
        assertThrows(BusinessRuleException.class, () -> estadosPedido.eliminar(creado));
    }

    // ------------------------------------------------------------------ datos de prueba

    private long crearPedido(String estado) {
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social) VALUES ('IT Cliente', 'IT Cliente')");
        jdbc.update("""
                INSERT INTO Pedido (filemaker_id, numero_pedido, cliente_id, estado_actual_id, fecha_creacion_fm)
                SELECT 'IT-1', 'IT-1', c.id, e.id, SYSUTCDATETIME()
                FROM Cliente c CROSS JOIN Estado_Pedido e WHERE c.filemaker_id = 'IT Cliente' AND e.codigo = ?""", estado);
        return numero("SELECT id FROM Pedido WHERE filemaker_id = 'IT-1'");
    }

    private long crearLinea(long pedidoId, String estado, int cantidad) {
        jdbc.update("INSERT INTO Producto (filemaker_id, nombre) VALUES ('IT-PT', 'IT-PT')");
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, pedido_id, producto_id, estado_actual_id, cantidad_solicitada, precio_unitario)
                SELECT 'IT-L1', ?, p.id, e.id, ?, 0
                FROM Producto p CROSS JOIN Estado_Detalle e WHERE p.filemaker_id = 'IT-PT' AND e.codigo = ?""",
                pedidoId, cantidad, estado);
        return numero("SELECT id FROM Pedido_Detalle WHERE filemaker_id = 'IT-L1'");
    }

    private int estadoPedido(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Pedido WHERE codigo = ?", Integer.class, codigo);
    }

    private int estadoDetalle(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Detalle WHERE codigo = ?", Integer.class, codigo);
    }

    private long numero(String sql) {
        return jdbc.queryForObject(sql, Long.class);
    }

    private String texto(String sql) {
        return jdbc.queryForObject(sql, String.class);
    }
}
