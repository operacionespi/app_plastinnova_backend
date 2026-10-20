package co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.LineaColaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper.PedidoMapper;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.specification.PedidoDetalleSpecification;
import co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.PedidoDetalleRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

/** Cola de trabajo del operador: líneas pendientes de varios pedidos, filtradas por la etapa elegida en la tablet. */
@Service
@RequiredArgsConstructor
public class ColaTrabajoService {

    /** Nombre expuesto en la API → propiedad JPA (evita ordenar por relaciones o datos internos). */
    private static final Map<String, String> ORDEN_PERMITIDO = Map.of(
            "fechaCreacionFm", "fechaCreacionFm",
            "fechaEstimadaEntrega", "pedido.fechaEstimadaEntrega",
            "numeroPedido", "pedido.numeroPedido",
            "posicion", "posicion");
    /** Lo más antiguo primero; dentro del pedido, en el orden de sus posiciones. */
    private static final Sort ORDEN_POR_DEFECTO = Sort.by("fechaCreacionFm", "pedido.numeroPedido", "posicion");

    private final PedidoDetalleRepository detalleRepository;
    private final PedidoMapper pedidoMapper;

    /** Una consulta por página (más el conteo): línea con su pedido, cliente, producto y estado. */
    @Transactional(readOnly = true)
    public Page<LineaColaDTO> buscar(FiltroLineaRequestDTO filtro, Pageable pageable) {
        LocalDate hoy = LocalDate.now(FechaUtc.ZONA_COLOMBIA);
        return detalleRepository.findAll(PedidoDetalleSpecification.colaDeTrabajo(filtro), conOrdenValido(pageable))
                .map(d -> aDto(d, hoy));
    }

    private LineaColaDTO aDto(PedidoDetalle d, LocalDate hoy) {
        Pedido p = d.getPedido();
        return new LineaColaDTO(
                d.getId(),
                p.getId(),
                p.getNumeroPedido(),
                p.getCliente().getRazonSocial(),
                d.getPosicion(),
                d.getProducto().getSku(),
                d.getProducto().getNombre(),
                pedidoMapper.toEstadoResponseDTO(d.getEstadoActual()),
                d.getCantidadSolicitada(),
                d.getCantidadProcesada(),
                d.cantidadMaximaReportable(),
                Boolean.TRUE.equals(d.getAvanceReportado()),
                d.getTipoAlistamiento(),
                d.getEsAnexo(),
                d.getFechaCreacionFm(),
                p.getFechaEstimadaEntrega(),
                p.estaAtrasado(hoy));
    }

    private static Pageable conOrdenValido(Pageable pageable) {
        Sort orden = ORDEN_POR_DEFECTO;
        if (pageable.getSort().isSorted()) {
            orden = Sort.unsorted();
            for (Sort.Order o : pageable.getSort()) {
                String propiedad = ORDEN_PERMITIDO.get(o.getProperty());
                if (propiedad == null) {
                    throw new SolicitudInvalidaException("No se puede ordenar por '" + o.getProperty()
                            + "'. Campos permitidos: " + String.join(", ", ORDEN_PERMITIDO.keySet().stream().sorted().toList()) + ".");
                }
                Sort.Order porPropiedad = new Sort.Order(o.getDirection(), propiedad);
                // Los pedidos sin fecha de entrega van al final en ambos sentidos
                orden = orden.and(Sort.by(propiedad.equals("pedido.fechaEstimadaEntrega") ? porPropiedad.nullsLast() : porPropiedad));
            }
        }
        // Desempate estable para que la paginación no repita ni salte líneas
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), orden.and(Sort.by("id")));
    }
}
