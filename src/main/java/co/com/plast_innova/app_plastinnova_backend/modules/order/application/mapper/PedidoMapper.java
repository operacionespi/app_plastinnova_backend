package co.com.plast_innova.app_plastinnova_backend.modules.order.application.mapper;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoCatalogo;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.HistorialEstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoDetalleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoPedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.ReportingPolicy;

import java.util.List;

// unmappedTargetPolicy = IGNORE evita advertencias de compilación por campos de la entidad que no van al DTO
@Mapper(componentModel = "spring", unmappedTargetPolicy = ReportingPolicy.IGNORE)
public interface PedidoMapper {

    // 1. Mapeo del Agregado Raíz (Pedido)
    @Mapping(source = "cliente.razonSocial", target = "clienteRazonSocial")
    @Mapping(source = "cliente.documentoIdentidad", target = "clienteDocumento")
    @Mapping(source = "cliente.vendedor", target = "vendedor")
    @Mapping(source = "ciudadEnvio.nombre", target = "ciudad")
    @Mapping(source = "ciudadEnvio.pais.nombre", target = "pais")
    @Mapping(target = "atrasado", expression = "java(pedido.estaAtrasado(java.time.LocalDate.now(co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc.ZONA_COLOMBIA)))")
    PedidoResponseDTO toResponseDTO(Pedido pedido);

    List<PedidoResponseDTO> toResponseDTOList(List<Pedido> pedidos);

    // 2. Mapeo de los Detalles (Líneas de Producción)
    @Mapping(source = "producto.sku", target = "productoSku")
    @Mapping(source = "producto.nombre", target = "productoNombre")
    @Mapping(target = "cantidadMaximaReporte", expression = "java(detalle.cantidadMaximaReportable())")
    PedidoDetalleResponseDTO toDetalleResponseDTO(PedidoDetalle detalle);

    // 3. Mapeo de Catálogos de Estados (pedido y línea comparten las columnas)
    EstadoResponseDTO toEstadoResponseDTO(EstadoCatalogo estado);

    // 4. Historial de cambios de estado
    @Mapping(source = "usuario.nombreCompleto", target = "usuarioNombre")
    @Mapping(source = "usuario.email", target = "usuarioEmail")
    HistorialEstadoResponseDTO toHistorialDTO(HistorialEstadoPedido historial);

    @Mapping(source = "usuario.nombreCompleto", target = "usuarioNombre")
    @Mapping(source = "usuario.email", target = "usuarioEmail")
    HistorialEstadoResponseDTO toHistorialDTO(HistorialEstadoDetalle historial);

    List<HistorialEstadoResponseDTO> toHistorialPedidoDTOList(List<HistorialEstadoPedido> historial);

    List<HistorialEstadoResponseDTO> toHistorialDetalleDTOList(List<HistorialEstadoDetalle> historial);
}
