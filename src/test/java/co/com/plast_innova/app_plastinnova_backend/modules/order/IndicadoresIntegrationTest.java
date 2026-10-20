package co.com.plast_innova.app_plastinnova_backend.modules.order;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.LineaColaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiLineasPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiPedidosPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiProduccionOperadorDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoCicloDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoEntregaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiUbicacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.NivelUbicacion;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl.ColaTrabajoService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl.DashboardKpiService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import co.com.plast_innova.app_plastinnova_backend.support.PruebaIntegracion;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Indicadores contra SQL Server real con un escenario de valores conocidos (fechas en UTC; Colombia = UTC-5).
 *
 * <ul>
 *   <li>A: Cali, creado 01/10 10:00, Facturado, cerrado 03/10 10:00 (2 días). Líneas L1 (10 u.) y L2 (5 u., eliminada en FM).</li>
 *   <li>B: Lima, creado 01/10 12:00, En alistamiento. Línea L3 (7 u., internacional).</li>
 *   <li>C: Cali, creado 20/09 (fuera del rango), Creado. Línea L4 (3 u.).</li>
 *   <li>D: Cali, creado 01/10, eliminado en FileMaker: no cuenta en nada.</li>
 * </ul>
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
class IndicadoresIntegrationTest extends PruebaIntegracion {

    private static final LocalDate DIA = LocalDate.of(2026, 10, 1);

    @Autowired private DashboardKpiService kpis;
    @Autowired private ColaTrabajoService colaTrabajo;
    @Autowired private PedidoService pedidoService;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private long pedidoA;

    @BeforeEach
    void preparar() {
        limpiar();
        jdbc.update("INSERT INTO Pais (nombre) VALUES (N'IT Colombia'), (N'IT Perú')");
        jdbc.update("INSERT INTO Departamento (pais_id, nombre) SELECT id, N'IT Valle' FROM Pais WHERE nombre = N'IT Colombia'");
        jdbc.update("INSERT INTO Zona (codigo, nombre) VALUES ('IT_OCC', N'IT Occidente')");
        jdbc.update("""
                INSERT INTO Ciudad (pais_id, departamento_id, zona_id, nombre)
                SELECT p.id, d.id, z.id, N'IT Cali' FROM Pais p, Departamento d, Zona z
                WHERE p.nombre = N'IT Colombia' AND d.nombre = N'IT Valle' AND z.codigo = 'IT_OCC'""");
        jdbc.update("INSERT INTO Ciudad (pais_id, nombre) SELECT id, N'IT Lima' FROM Pais WHERE nombre = N'IT Perú'");
        jdbc.update("INSERT INTO Cliente (filemaker_id, razon_social, vendedor) VALUES ('IT Cliente', 'IT Cliente', 'IT Vendedor')");
        jdbc.update("INSERT INTO Producto (filemaker_id, nombre) VALUES ('IT-P1', 'IT-P1'), ('IT-P2', 'IT-P2'), ('IT-P3', 'IT-P3')");

        pedidoA = pedido("IT-A", "IT Cali", "2026-10-01T10:00:00", "FACTURADO", "2026-10-03T10:00:00", false);
        long b = pedido("IT-B", "IT Lima", "2026-10-01T12:00:00", "EN_ALISTAMIENTO", null, false);
        long c = pedido("IT-C", "IT Cali", "2026-09-20T12:00:00", "CREADO", null, false);
        long d = pedido("IT-D", "IT Cali", "2026-10-01T12:00:00", "CREADO", null, true);

        long l1 = linea("L1", pedidoA, "IT-P1", "ALMACEN1", 10, "NACIONAL", false);
        linea("L2", pedidoA, "IT-P2", "CREADO", 5, "NACIONAL", true);
        long l3 = linea("L3", b, "IT-P3", "ALMACEN1", 7, "INTERNACIONAL", false);
        long l4 = linea("L4", c, "IT-P1", "CALIDAD", 3, "NACIONAL", false);
        linea("L5", d, "IT-P1", "CREADO", 9, "NACIONAL", false);

        // L1: ensamble 06:00 → calidad 08:00 (2 h), reproceso: ensamble 09:00 → calidad 12:00 (3 h)
        movimiento(l1, "CREADO", "2026-10-01T05:30:00", null, "SYNC");
        movimiento(l1, "EN_ENSAMBLE", "2026-10-01T06:00:00", null, "USUARIO");
        movimiento(l1, "RESINA", "2026-10-01T07:00:00", null, "USUARIO");
        movimiento(l1, "CALIDAD", "2026-10-01T08:00:00", 10, "USUARIO");
        movimiento(l1, "EN_ENSAMBLE", "2026-10-01T09:00:00", null, "USUARIO");
        movimiento(l1, "RESINA", "2026-10-01T09:30:00", null, "USUARIO");
        movimiento(l1, "CALIDAD", "2026-10-01T12:00:00", 4, "USUARIO");
        // L3: ensamble 06:00 → calidad 07:00 (1 h). La segunda entrada a calidad no tiene un ensamble nuevo: no es ciclo
        movimiento(l3, "EN_ENSAMBLE", "2026-10-01T06:00:00", null, "USUARIO");
        movimiento(l3, "CALIDAD", "2026-10-01T07:00:00", 7, "USUARIO");
        movimiento(l3, "ALMACEN1", "2026-10-01T07:30:00", null, "USUARIO");
        movimiento(l3, "CALIDAD", "2026-10-01T08:00:00", 7, "USUARIO");
        // Correcciones de cantidad (V10): no son entradas a una etapa ni producción. Si contaran, el primer ciclo de L1
        // empezaría a las 06:30 y Calidad tendría un movimiento más.
        correccion(l1, "EN_ENSAMBLE", "2026-10-01T06:30:00", 8, 10);
        correccion(l3, "CALIDAD", "2026-10-01T07:15:00", 7, 6);
        // L4: fuera del rango
        movimiento(l4, "EN_ENSAMBLE", "2026-09-20T06:00:00", null, "USUARIO");
        movimiento(l4, "CALIDAD", "2026-09-20T09:00:00", 3, "USUARIO");
    }

    @AfterEach
    void limpiar() {
        for (String tabla : List.of("Historial_Estado_Detalle", "Historial_Estado_Pedido", "Pedido_Detalle", "Pedido",
                "Cliente", "Producto", "Ciudad", "Departamento", "Pais")) {
            jdbc.update("DELETE FROM " + tabla);
        }
        jdbc.update("DELETE FROM Zona WHERE codigo = 'IT_OCC'");
    }

    @Test
    void tiempoDeCicloCuentaReprocesosYNoDuplicaCiclos() {
        KpiTiempoCicloDTO ciclo = kpis.calcularTiempoCiclo(estado("EN_ENSAMBLE"), estado("CALIDAD"), DIA, DIA);

        assertEquals(3, ciclo.ciclos());          // L1 dos veces + L3 una vez
        assertEquals(2, ciclo.lineas());
        assertEquals(21, ciclo.piezasProcesadas()); // 10 + 4 + 7
        assertEquals(new BigDecimal("2.00"), ciclo.promedioHoras());
        assertEquals(new BigDecimal("1.00"), ciclo.minimoHoras());
        assertEquals(new BigDecimal("3.00"), ciclo.maximoHoras());

        KpiTiempoCicloDTO vacio = kpis.calcularTiempoCiclo(estado("EN_ENSAMBLE"), estado("CALIDAD"), DIA.plusDays(5), DIA.plusDays(5));
        assertEquals(0, vacio.ciclos());
        assertEquals(new BigDecimal("0.00"), vacio.promedioHoras());
    }

    @Test
    void tiempoDeEntregaUsaLaFechaDeCierre() {
        // Cierre: 03/10 10:00 UTC = 03/10 05:00 Colombia
        KpiTiempoEntregaDTO entrega = kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2));
        assertEquals(1, entrega.pedidosCerrados());
        assertEquals(new BigDecimal("2.00"), entrega.promedioDias());
        assertEquals(0, entrega.pedidosConFechaEntrega()); // sin fecha estimada no se mide si fue a tiempo
        assertEquals(0, entrega.pedidosATiempo());

        assertEquals(0, kpis.calcularTiempoEntrega(DIA, DIA).pedidosCerrados());
    }

    @Test
    void entregaATiempoEsCerrarAMasTardarElDiaEstimadoEnHoraColombia() {
        // 04/10 03:00 UTC = 03/10 22:00 en Colombia: todavía es el día estimado
        jdbc.update("UPDATE Pedido SET fecha_cierre = '2026-10-04T03:00:00', fecha_estimada_entrega = '2026-10-03' WHERE id = ?", pedidoA);
        KpiTiempoEntregaDTO aTiempo = kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2));
        assertEquals(1, aTiempo.pedidosConFechaEntrega());
        assertEquals(1, aTiempo.pedidosATiempo());

        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = '2026-10-02' WHERE id = ?", pedidoA);
        KpiTiempoEntregaDTO tarde = kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2));
        assertEquals(1, tarde.pedidosConFechaEntrega());
        assertEquals(0, tarde.pedidosATiempo());
    }

    @Test
    void otifExigeATiempoYTodasLasLineasEnEntregadoONoSale() {
        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = '2026-10-03' WHERE id = ?", pedidoA);
        // L1 sigue en Almacén 1 (L2 está eliminada en FileMaker y no cuenta): a tiempo pero incompleto
        KpiTiempoEntregaDTO incompleto = kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2));
        assertEquals(1, incompleto.pedidosATiempo());
        assertEquals(0, incompleto.pedidosCompletos());
        assertEquals(0, incompleto.pedidosOtif());

        // Una línea entregada y otra que no sale: completo
        jdbc.update("UPDATE Pedido_Detalle SET estado_actual_id = ? WHERE filemaker_id = 'L1'", estado("ENTREGADO"));
        linea("L6", pedidoA, "IT-P3", "NO_SALE", 2, "NACIONAL", false);
        KpiTiempoEntregaDTO otif = kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2));
        assertEquals(1, otif.pedidosCompletos());
        assertEquals(1, otif.pedidosOtif());

        // Completo pero tarde: no es OTIF
        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = '2026-10-02' WHERE id = ?", pedidoA);
        KpiTiempoEntregaDTO tarde = kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2));
        assertEquals(1, tarde.pedidosCompletos());
        assertEquals(0, tarde.pedidosOtif());
    }

    @Test
    void losPedidosAnuladosNoCuentanEnLasEntregas() {
        jdbc.update("UPDATE Pedido SET estado_actual_id = (SELECT id FROM Estado_Pedido WHERE codigo = 'ANULADO') WHERE id = ?", pedidoA);
        assertEquals(0, kpis.calcularTiempoEntrega(DIA.plusDays(2), DIA.plusDays(2)).pedidosCerrados());
    }

    @Test
    void pedidosYLineasPorEstadoExcluyenLosEliminados() {
        var pedidos = kpis.pedidosPorEstado().stream()
                .collect(Collectors.toMap(KpiPedidosPorEstadoDTO::codigo, KpiPedidosPorEstadoDTO::pedidos));
        assertEquals(1, pedidos.get("FACTURADO"));
        assertEquals(1, pedidos.get("EN_ALISTAMIENTO"));
        assertEquals(1, pedidos.get("CREADO")); // C; D está eliminado
        assertEquals(0, pedidos.get("EN_DESPACHOS"));

        var lineas = kpis.lineasPorEstado(null).stream()
                .collect(Collectors.toMap(KpiLineasPorEstadoDTO::codigo, Function.identity()));
        assertEquals(2, lineas.get("ALMACEN1").lineas());
        assertEquals(17, lineas.get("ALMACEN1").piezasSolicitadas());
        assertEquals(0, lineas.get("CREADO").lineas()); // L2 eliminada, L5 de un pedido eliminado
        assertEquals(1, lineas.get("CALIDAD").lineas());

        var internacionales = kpis.lineasPorEstado(TipoAlistamiento.INTERNACIONAL).stream()
                .collect(Collectors.toMap(KpiLineasPorEstadoDTO::codigo, KpiLineasPorEstadoDTO::lineas));
        assertEquals(1, internacionales.get("ALMACEN1"));
        assertEquals(0, internacionales.get("CALIDAD"));
    }

    @Test
    void produccionPorOperadorExcluyeLaSincronizacionYLasCorrecciones() {
        Integer admin = jdbc.queryForObject("SELECT id FROM Usuario WHERE email = 'admin@plast-innova.com.co'", Integer.class);
        jdbc.update("UPDATE Historial_Estado_Detalle SET usuario_id = ? WHERE origen = 'USUARIO'", admin);

        var porEstado = kpis.produccionPorOperador(DIA, DIA, admin).stream()
                .collect(Collectors.toMap(KpiProduccionOperadorDTO::estadoNombre, Function.identity()));
        assertEquals(4, porEstado.get("Calidad").movimientos());
        assertEquals(2, porEstado.get("Calidad").lineas());
        assertEquals(28, porEstado.get("Calidad").piezas());
        assertEquals(3, porEstado.get("En ensamble").movimientos());
        assertTrue(porEstado.values().stream().noneMatch(p -> p.estadoNombre().equals("Creado"))); // el de SYNC
    }

    @Test
    void pedidosPorUbicacion() {
        var ciudades = kpis.pedidosPorUbicacion(NivelUbicacion.CIUDAD, DIA, DIA).stream()
                .collect(Collectors.toMap(KpiUbicacionDTO::ubicacion, Function.identity()));
        assertEquals(2, ciudades.size());
        KpiUbicacionDTO cali = ciudades.get("IT Cali (IT Colombia)");
        assertEquals(1, cali.pedidos());
        assertEquals(1, cali.pedidosCerrados());
        assertEquals(1, cali.lineas());
        assertEquals(10, cali.piezasSolicitadas());
        assertEquals(7, ciudades.get("IT Lima (IT Perú)").piezasSolicitadas());

        var zonas = kpis.pedidosPorUbicacion(NivelUbicacion.ZONA, DIA, DIA).stream()
                .collect(Collectors.toMap(KpiUbicacionDTO::ubicacion, KpiUbicacionDTO::pedidos));
        assertEquals(1, zonas.get("IT Occidente"));
        assertEquals(1, zonas.get("Sin zona"));
    }

    @Test
    void listadoDePedidosFiltraOrdenaYTotalizaSinNMasUno() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        Page<PedidoResumenDTO> pagina = kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, null, null, null, DIA, DIA, null, null), PageRequest.of(0, 10));

        // página (con cliente, estado y ciudad) + totales de líneas; el conteo se omite porque la primera página no se llenó
        assertEquals(2, estadisticas.getPrepareStatementCount());
        assertEquals(List.of("IT-B", "IT-A"), pagina.map(PedidoResumenDTO::numeroPedido).toList());
        PedidoResumenDTO a = pagina.getContent().get(1);
        assertEquals(1, a.lineas());
        assertEquals(10, a.piezasSolicitadas());
        assertEquals("IT Colombia", a.pais());
        assertEquals("IT Vendedor", a.vendedor());

        assertEquals(List.of("IT-B"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, null, null, null, DIA, DIA, true, null), PageRequest.of(0, 10))
                .map(PedidoResumenDTO::numeroPedido).toList());
        assertEquals(List.of("IT-A"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO("it-a", null, null, null, null, null, null, null, null), PageRequest.of(0, 10))
                .map(PedidoResumenDTO::numeroPedido).toList());
        // Ciudad: contiene, sin distinguir mayúsculas ni tildes; IT-D (Cali) está eliminado en FileMaker
        assertEquals(List.of("IT-C", "IT-A"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, " cali ", null, null, null, null, null, null),
                PageRequest.of(0, 10, Sort.by("fechaCreacionFm")))
                .map(PedidoResumenDTO::numeroPedido).toList());
        assertEquals(List.of("IT-B"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, "lim", null, null, null, null, null, null), PageRequest.of(0, 10))
                .map(PedidoResumenDTO::numeroPedido).toList());
        jdbc.update("UPDATE Ciudad SET nombre = N'IT Bogotá' WHERE nombre = N'IT Lima'");
        assertEquals(List.of("IT-B"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, "BOGOTA", null, null, null, null, null, null), PageRequest.of(0, 10))
                .map(PedidoResumenDTO::numeroPedido).toList());

        // Vendedor: lista de los clientes con pedidos activos (IT-D está eliminado) y filtro por nombre completo
        jdbc.update("""
                INSERT INTO Cliente (filemaker_id, razon_social, vendedor) VALUES
                 ('IT Cliente 2', 'IT Cliente 2', 'IT Vendedora Norte'), ('IT Cliente 3', 'IT Cliente 3', 'IT Solo Eliminado'),
                 ('IT Cliente 4', 'IT Cliente 4', NULL)""");
        jdbc.update("UPDATE Pedido SET cliente_id = (SELECT id FROM Cliente WHERE filemaker_id = 'IT Cliente 2') WHERE numero_pedido = 'IT-B'");
        jdbc.update("UPDATE Pedido SET cliente_id = (SELECT id FROM Cliente WHERE filemaker_id = 'IT Cliente 3') WHERE numero_pedido = 'IT-D'");
        jdbc.update("UPDATE Pedido SET cliente_id = (SELECT id FROM Cliente WHERE filemaker_id = 'IT Cliente 4') WHERE numero_pedido = 'IT-C'");
        assertEquals(List.of("IT Vendedor", "IT Vendedora Norte"), kpis.vendedores());
        assertEquals(List.of("IT-B"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, null, List.of(" it vendedora norte ", " "), null, null, null, null, null),
                PageRequest.of(0, 10))
                .map(PedidoResumenDTO::numeroPedido).toList());
        assertEquals(List.of("IT-B", "IT-A"), kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, null, List.of("IT Vendedor", "IT Vendedora Norte"), null, DIA, DIA, null, null),
                PageRequest.of(0, 10))
                .map(PedidoResumenDTO::numeroPedido).toList());
        assertThrows(SolicitudInvalidaException.class, () -> kpis.buscarPedidosPaginados(
                new FiltroPedidoRequestDTO(null, null, null, null, null, null, null, null, null),
                PageRequest.of(0, 10, Sort.by("usuarioAsignado.passwordHash"))));
    }

    @Test
    void colaDeTrabajoMuestraSoloLineasPendientesDePedidosAbiertosSinNMasUno() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        Page<LineaColaDTO> pagina = colaTrabajo.buscar(new FiltroLineaRequestDTO(null, null, null, null, null), PageRequest.of(0, 10));

        // Una sola consulta (línea + pedido + cliente + producto + estado); sin conteo porque la página no se llenó.
        // Fuera: L1 (pedido IT-A cerrado), L2 (eliminada en FileMaker) y L5 (pedido IT-D eliminado)
        assertEquals(1, estadisticas.getPrepareStatementCount());
        assertEquals(List.of("IT-B", "IT-C"), pagina.map(LineaColaDTO::numeroPedido).toList());
        LineaColaDTO l3 = pagina.getContent().getFirst();
        assertEquals("ALMACEN1", l3.estadoActual().codigo());
        assertNotNull(l3.estadoActual().color());
        assertEquals("IT Cliente", l3.clienteRazonSocial());
        assertEquals("IT-P3", l3.productoNombre());
        assertEquals(7, l3.cantidadSolicitada());
        assertFalse(l3.atrasado());

        // Filtros: etapa, tipo de alistamiento y prefijo del número de pedido
        assertEquals(List.of("IT-C"), cola(new FiltroLineaRequestDTO(List.of(estado("CALIDAD")), null, null, null, null), Sort.unsorted()));
        assertEquals(List.of("IT-B"), cola(new FiltroLineaRequestDTO(null, TipoAlistamiento.INTERNACIONAL, null, null, null), Sort.unsorted()));
        assertEquals(List.of("IT-C"), cola(new FiltroLineaRequestDTO(null, null, "it-c", null, null), Sort.unsorted()));
        assertEquals(List.of("IT-C", "IT-B"), cola(new FiltroLineaRequestDTO(null, null, null, null, null),
                Sort.by(Sort.Direction.DESC, "numeroPedido")));
        assertThrows(SolicitudInvalidaException.class, () -> cola(new FiltroLineaRequestDTO(null, null, null, null, null),
                Sort.by("pedido.cliente.razonSocial")));

        // Una línea en estado final sale de la cola; un pedido con la entrega vencida se marca atrasado
        jdbc.update("UPDATE Pedido_Detalle SET estado_actual_id = ? WHERE filemaker_id = 'L4'", estado("ENTREGADO"));
        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = DATEADD(DAY, -2, CAST(SYSUTCDATETIME() AS DATE)) WHERE filemaker_id = 'IT-B'");
        Page<LineaColaDTO> despues = colaTrabajo.buscar(new FiltroLineaRequestDTO(null, null, null, null, null), PageRequest.of(0, 10));
        assertEquals(List.of("IT-B"), despues.map(LineaColaDTO::numeroPedido).toList());
        assertTrue(despues.getContent().getFirst().atrasado());
    }

    private List<String> cola(FiltroLineaRequestDTO filtro, Sort orden) {
        return colaTrabajo.buscar(filtro, PageRequest.of(0, 10, orden)).map(LineaColaDTO::numeroPedido).toList();
    }

    @Test
    void ordenarPorFechaDeEntregaDejaLosPedidosSinFechaAlFinal() {
        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = '2026-10-20' WHERE filemaker_id = 'IT-C'");
        jdbc.update("UPDATE Pedido SET fecha_estimada_entrega = '2026-10-10' WHERE filemaker_id = 'IT-B'");
        FiltroPedidoRequestDTO todos = new FiltroPedidoRequestDTO(null, null, null, null, null, null, null, null, null);

        for (Sort.Direction sentido : Sort.Direction.values()) {
            List<String> numeros = kpis.buscarPedidosPaginados(todos, PageRequest.of(0, 10, Sort.by(sentido, "fechaEstimadaEntrega")))
                    .map(PedidoResumenDTO::numeroPedido).toList();
            assertEquals("IT-A", numeros.getLast(), sentido + ": " + numeros); // sin fecha
        }
        List<String> cola = cola(new FiltroLineaRequestDTO(null, null, null, null, null), Sort.by(Sort.Direction.ASC, "fechaEstimadaEntrega"));
        assertEquals("IT-B", cola.getFirst());
    }

    @Test
    void detalleDelPedidoCargaLasLineasEnLotes() {
        Statistics estadisticas = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        estadisticas.clear();

        PedidoResponseDTO pedido = pedidoService.obtenerPedidoPorId(pedidoA);

        // pedido + líneas + productos (lote) + estados de línea (lote), sin importar cuántas líneas tenga
        assertEquals(4, estadisticas.getPrepareStatementCount());
        assertEquals(2, pedido.detalles().size());
        assertTrue(pedido.detalles().get(1).eliminadoEnFm()); // L2 se muestra marcada
        // Ubicación, vendedor y posición en el mismo SELECT del pedido
        assertEquals("IT Cali", pedido.ciudad());
        assertEquals("IT Colombia", pedido.pais());
        assertEquals("IT Vendedor", pedido.vendedor());
        assertEquals(1, pedido.detalles().get(0).posicion());
        assertFalse(pedido.atrasado()); // cerrado: nunca atrasado
    }

    // ------------------------------------------------------------------ datos de prueba

    private long pedido(String numero, String ciudad, String creado, String estado, String cierre, boolean eliminado) {
        jdbc.update("""
                INSERT INTO Pedido (filemaker_id, numero_pedido, cliente_id, ciudad_envio_id, estado_actual_id,
                                    fecha_creacion_fm, fecha_cierre, eliminado_en_fm)
                SELECT ?, ?, cl.id, ci.id, e.id, CAST(? AS DATETIME2), CAST(? AS DATETIME2), ?
                FROM Cliente cl, Ciudad ci, Estado_Pedido e
                WHERE cl.filemaker_id = 'IT Cliente' AND ci.nombre = ? AND e.codigo = ?""",
                numero, numero, creado, cierre, eliminado, ciudad, estado);
        return jdbc.queryForObject("SELECT id FROM Pedido WHERE filemaker_id = ?", Long.class, numero);
    }

    private long linea(String id, long pedidoId, String producto, String estado, int cantidad, String tipo, boolean eliminada) {
        jdbc.update("""
                INSERT INTO Pedido_Detalle (filemaker_id, posicion, pedido_id, producto_id, estado_actual_id,
                                            cantidad_solicitada, precio_unitario, tipo_alistamiento, eliminado_en_fm)
                SELECT ?, ?, ?, p.id, e.id, ?, 0, ?, ?
                FROM Producto p, Estado_Detalle e WHERE p.filemaker_id = ? AND e.codigo = ?""",
                id, Integer.parseInt(id.substring(1)), pedidoId, cantidad, tipo, eliminada, producto, estado);
        return jdbc.queryForObject("SELECT id FROM Pedido_Detalle WHERE filemaker_id = ?", Long.class, id);
    }

    private void movimiento(long lineaId, String estado, String fecha, Integer cantidad, String origen) {
        jdbc.update("""
                INSERT INTO Historial_Estado_Detalle (pedido_detalle_id, estado_id, origen, cantidad_procesada, fecha_cambio)
                SELECT ?, id, ?, ?, CAST(? AS DATETIME2) FROM Estado_Detalle WHERE codigo = ?""",
                lineaId, origen, cantidad, fecha, estado);
    }

    private void correccion(long lineaId, String estado, String fecha, int antes, int despues) {
        jdbc.update("""
                INSERT INTO Historial_Estado_Detalle (pedido_detalle_id, estado_anterior_id, estado_id, origen, tipo,
                                                      cantidad_anterior, cantidad_procesada, fecha_cambio, observaciones)
                SELECT ?, id, id, 'USUARIO', 'CORRECCION_CANTIDAD', ?, ?, CAST(? AS DATETIME2), 'Corrección IT'
                FROM Estado_Detalle WHERE codigo = ?""",
                lineaId, antes, despues, fecha, estado);
    }

    private int estado(String codigo) {
        return jdbc.queryForObject("SELECT id FROM Estado_Detalle WHERE codigo = ?", Integer.class, codigo);
    }
}
