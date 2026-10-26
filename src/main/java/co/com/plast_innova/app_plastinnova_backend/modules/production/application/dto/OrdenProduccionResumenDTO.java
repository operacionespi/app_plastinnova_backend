package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/** Una orden en el listado. */
public record OrdenProduccionResumenDTO(
        Long id,
        String numero,
        String itemCodigo,
        String itemDescripcion,
        String unidad,
        Integer cantidad,
        /** Piezas reportadas por la última etapa (0 si aún no reporta). */
        Integer cantidadProcesada,
        EstadoOrdenProduccion estado,
        EstadoResponseDTO etapa,
        String bodegaDestino,
        LocalDate fechaRequerida,
        LocalDateTime fechaCreacion,
        LocalDateTime fechaCierre,
        /** Números de los pedidos cuyas líneas atiende (vigentes). */
        List<String> pedidos
) {}
