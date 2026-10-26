package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una orden con sus líneas, sus materiales, su historial y lo que movió en el inventario. */
public record OrdenProduccionDTO(
        Long id,
        String numero,
        String itemCodigo,
        String itemDescripcion,
        String unidad,
        Integer cantidad,
        Integer cantidadProcesada,
        /** Lo máximo que puede reportar la etapa actual. */
        Integer cantidadMaximaReportable,
        /** Suma de lo asignado a las líneas activas: lo demás queda libre para inventario. */
        Integer cantidadAsignada,
        EstadoOrdenProduccion estado,
        EstadoResponseDTO etapa,
        /** Etapas a las que puede pasar (transiciones del catálogo, sin estados finales); vacío si está cerrada. */
        List<EstadoResponseDTO> etapasSiguientes,
        /** Código de la etapa en que lo producido entra al inventario y la orden termina. */
        String etapaRecepcion,
        String bodegaDestino,
        LocalDate fechaRequerida,
        String observaciones,
        String usuarioCreacion,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCierre,
        List<LineaOrdenDTO> lineas,
        List<ComponenteOrdenDTO> componentes,
        List<HistorialOrdenDTO> historial,
        List<MovimientoDTO> movimientos
) {}
