package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories.EstadoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiLineasPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiPedidosPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiProduccionOperadorDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoCicloDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoEntregaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiUbicacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.NivelUbicacion;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.kpi.KpiRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.kpi.KpiRepository.ResumenTiempos;
import co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.specification.PedidoSpecification;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.TotalesLineasPedido;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.RangoFechas;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Listado de pedidos para el tablero e indicadores. Los indicadores se calculan en SQL Server (KpiRepository).
 * Los rangos de fechas se reciben como días calendario de Colombia.
 */
@Service
@RequiredArgsConstructor
public class DashboardKpiService {

    /** Campos por los que se puede ordenar el listado (evita ordenar por relaciones o datos internos). */
    private static final Set<String> ORDEN_PERMITIDO = Set.of(
            "fechaCreacionFm", "fechaEstimadaEntrega", "fechaCierre", "numeroPedido", "fechaIngreso");
    private static final Set<String> FECHAS_OPCIONALES = Set.of("fechaEstimadaEntrega", "fechaCierre");
    private static final Sort ORDEN_POR_DEFECTO = Sort.by(Sort.Direction.DESC, "fechaCreacionFm").and(Sort.by("id"));

    private final PedidoRepository pedidoRepository;
    private final PedidoDetalleRepository pedidoDetalleRepository;
    private final EstadoDetalleRepository estadoDetalleRepository;
    private final KpiRepository kpiRepository;
    private final PedidoMapper pedidoMapper;

    /**
     * Dos consultas por página (más el conteo): pedidos con cliente, estado y ciudad, y los totales de sus líneas.
     */
    @Transactional(readOnly = true)
    public Page<PedidoResumenDTO> buscarPedidosPaginados(FiltroPedidoRequestDTO filtro, Pageable pageable) {
        Page<Pedido> pedidos = pedidoRepository.findAll(PedidoSpecification.construirFiltro(filtro), conOrdenValido(pageable));

        Map<Long, TotalesLineasPedido> totales = pedidos.isEmpty() ? Map.of()
                : pedidoDetalleRepository.totalizarPorPedido(pedidos.map(Pedido::getId).toList()).stream()
                        .collect(Collectors.toMap(TotalesLineasPedido::pedidoId, Function.identity()));

        LocalDate hoy = LocalDate.now(FechaUtc.ZONA_COLOMBIA);
        return pedidos.map(p -> resumen(p, totales.get(p.getId()), hoy));
    }

    @Transactional(readOnly = true)
    public List<String> vendedores() {
        return pedidoRepository.findVendedores();
    }

    @Transactional(readOnly = true)
    public List<KpiPedidosPorEstadoDTO> pedidosPorEstado() {
        return kpiRepository.pedidosPorEstado();
    }

    @Transactional(readOnly = true)
    public List<KpiLineasPorEstadoDTO> lineasPorEstado(TipoAlistamiento tipoAlistamiento) {
        return kpiRepository.lineasPorEstado(tipoAlistamiento);
    }

    @Transactional(readOnly = true)
    public KpiTiempoCicloDTO calcularTiempoCiclo(Integer estadoOrigenId, Integer estadoDestinoId, LocalDate desde, LocalDate hasta) {
        if (estadoOrigenId.equals(estadoDestinoId)) {
            throw new SolicitudInvalidaException("La fase de origen y la de destino deben ser distintas.");
        }
        EstadoDetalle origen = estadoDetalleRepository.findById(estadoOrigenId)
                .orElseThrow(() -> new ResourceNotFoundException("El estado de origen " + estadoOrigenId + " no existe."));
        EstadoDetalle destino = estadoDetalleRepository.findById(estadoDestinoId)
                .orElseThrow(() -> new ResourceNotFoundException("El estado de destino " + estadoDestinoId + " no existe."));

        ResumenTiempos t = kpiRepository.tiempoCiclo(estadoOrigenId, estadoDestinoId, RangoFechas.deDiasColombia(desde, hasta));
        return new KpiTiempoCicloDTO(origen.getNombre(), destino.getNombre(), t.cantidad(), t.lineas(), t.piezas(),
                t.promedio(), t.minimo(), t.maximo());
    }

    @Transactional(readOnly = true)
    public KpiTiempoEntregaDTO calcularTiempoEntrega(LocalDate desde, LocalDate hasta) {
        return kpiRepository.tiempoEntrega(RangoFechas.deDiasColombia(desde, hasta));
    }

    @Transactional(readOnly = true)
    public List<KpiProduccionOperadorDTO> produccionPorOperador(LocalDate desde, LocalDate hasta, Integer usuarioId) {
        return kpiRepository.produccionPorOperador(RangoFechas.deDiasColombia(desde, hasta), usuarioId);
    }

    @Transactional(readOnly = true)
    public List<KpiUbicacionDTO> pedidosPorUbicacion(NivelUbicacion nivel, LocalDate desde, LocalDate hasta) {
        return kpiRepository.pedidosPorUbicacion(nivel, RangoFechas.deDiasColombia(desde, hasta));
    }

    private static Pageable conOrdenValido(Pageable pageable) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), ORDEN_POR_DEFECTO);
        }
        for (Sort.Order orden : pageable.getSort()) {
            if (!ORDEN_PERMITIDO.contains(orden.getProperty())) {
                throw new SolicitudInvalidaException("No se puede ordenar por '" + orden.getProperty()
                        + "'. Campos permitidos: " + String.join(", ", ORDEN_PERMITIDO.stream().sorted().toList()) + ".");
            }
        }
        // Las fechas vacías van al final en ambos sentidos: primero lo que tiene fecha (lo urgente).
        // Desempate estable para que la paginación no repita ni salte pedidos.
        Sort orden = Sort.by(pageable.getSort().stream()
                .map(o -> FECHAS_OPCIONALES.contains(o.getProperty()) ? o.nullsLast() : o)
                .toList());
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), orden.and(Sort.by("id")));
    }

    private PedidoResumenDTO resumen(Pedido p, TotalesLineasPedido totales, LocalDate hoy) {
        return new PedidoResumenDTO(
                p.getId(),
                p.getNumeroPedido(),
                p.getCliente().getRazonSocial(),
                p.getCliente().getVendedor(),
                p.getCiudadEnvio() != null ? p.getCiudadEnvio().getNombre() : null,
                p.getCiudadEnvio() != null ? p.getCiudadEnvio().getPais().getNombre() : null,
                pedidoMapper.toEstadoResponseDTO(p.getEstadoActual()),
                p.getFechaCreacionFm(),
                p.getFechaModificacionFm(),
                p.getFechaEstimadaEntrega(),
                p.getFechaCierre(),
                totales != null ? totales.lineas() : 0,
                totales != null ? totales.piezasSolicitadas() : 0,
                totales != null ? totales.piezasProcesadas() : 0,
                p.estaAtrasado(hoy));
    }
}
