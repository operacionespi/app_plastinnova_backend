package co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiLineasPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiPedidosPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiProduccionOperadorDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoCicloDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoEntregaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiUbicacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.NivelUbicacion;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl.DashboardKpiService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * Tablero: listado de pedidos e indicadores. Los rangos (desde/hasta) son días calendario de Colombia,
 * ambos incluidos, con un máximo de 366 días.
 */
@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Tablero e indicadores", description = "Listado de pedidos y KPIs calculados en SQL Server.")
public class DashboardController {

    private final DashboardKpiService dashboardKpiService;

    @Operation(summary = "Listado paginado de pedidos",
            description = "Sin las líneas; incluye los totales de las líneas activas. Orden por defecto: fecha de creación descendente. "
                    + "Se puede ordenar por fechaCreacionFm, fechaEstimadaEntrega, fechaCierre, numeroPedido o fechaIngreso.")
    @PostMapping("/pedidos/busqueda")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public PagedModel<PedidoResumenDTO> buscarPedidos(@RequestBody FiltroPedidoRequestDTO filtro,
                                                      @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(dashboardKpiService.buscarPedidosPaginados(filtro, pageable));
    }

    @Operation(summary = "Pedidos por estado del listado",
            description = "Conteo por estado con los mismos filtros del listado, sin el filtro de estados. "
                    + "Incluye los estados activos aunque no tengan pedidos.")
    @PostMapping("/pedidos/conteo-por-estado")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public List<KpiPedidosPorEstadoDTO> contarPedidosPorEstado(@RequestBody FiltroPedidoRequestDTO filtro) {
        return dashboardKpiService.contarPedidosPorEstado(filtro);
    }

    @Operation(summary = "Vendedores", description = "Vendedores de los clientes con pedidos activos, para filtrar el listado.")
    @GetMapping("/vendedores")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public List<String> vendedores() {
        return dashboardKpiService.vendedores();
    }

    @Operation(summary = "Pedidos por estado", description = "Conteo actual de pedidos activos en cada estado.")
    @GetMapping("/kpis/pedidos-por-estado")
    @PreAuthorize("hasAuthority('kpi:ver')")
    public List<KpiPedidosPorEstadoDTO> pedidosPorEstado() {
        return dashboardKpiService.pedidosPorEstado();
    }

    @Operation(summary = "Carga de producción por estado de línea",
            description = "Líneas activas y piezas en cada estado; opcionalmente solo nacional o internacional.")
    @GetMapping("/kpis/lineas-por-estado")
    @PreAuthorize("hasAuthority('kpi:ver')")
    public List<KpiLineasPorEstadoDTO> lineasPorEstado(@RequestParam(required = false) TipoAlistamiento tipoAlistamiento) {
        return dashboardKpiService.lineasPorEstado(tipoAlistamiento);
    }

    @Operation(summary = "Tiempo de ciclo entre dos fases de producción",
            description = "Horas desde que una línea entra a la fase de origen hasta que entra a la de destino, "
                    + "para los ciclos que terminaron en el rango. Cada reproceso cuenta como un ciclo.")
    @GetMapping("/kpis/tiempos-ciclo")
    @PreAuthorize("hasAuthority('kpi:ver')")
    public KpiTiempoCicloDTO tiempoCiclo(
            @RequestParam Integer estadoOrigenId,
            @RequestParam Integer estadoDestinoId,
            @Parameter(description = "Primer día (hora Colombia)") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @Parameter(description = "Último día, incluido") @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardKpiService.calcularTiempoCiclo(estadoOrigenId, estadoDestinoId, desde, hasta);
    }

    @Operation(summary = "Tiempo de entrega de los pedidos",
            description = "Días desde la creación en FileMaker hasta el cierre (estado final), para los pedidos cerrados en el rango.")
    @GetMapping("/kpis/tiempo-entrega")
    @PreAuthorize("hasAuthority('kpi:ver')")
    public KpiTiempoEntregaDTO tiempoEntrega(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardKpiService.calcularTiempoEntrega(desde, hasta);
    }

    @Operation(summary = "Producción por operador",
            description = "Movimientos, líneas y piezas reportadas por cada usuario en el rango, por estado.")
    @GetMapping("/kpis/produccion-operadores")
    @PreAuthorize("hasAuthority('kpi:ver')")
    public List<KpiProduccionOperadorDTO> produccionPorOperador(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Integer usuarioId) {
        return dashboardKpiService.produccionPorOperador(desde, hasta, usuarioId);
    }

    @Operation(summary = "Pedidos por ubicación de entrega",
            description = "Pedidos creados en el rango agrupados por país, departamento, zona o ciudad.")
    @GetMapping("/kpis/pedidos-por-ubicacion")
    @PreAuthorize("hasAuthority('kpi:ver')")
    public List<KpiUbicacionDTO> pedidosPorUbicacion(
            @RequestParam(defaultValue = "CIUDAD") NivelUbicacion nivel,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        return dashboardKpiService.pedidosPorUbicacion(nivel, desde, hasta);
    }
}
