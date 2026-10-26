package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.TipoEntregaProceso;

import java.time.LocalDateTime;

public record EntregaProcesoDTO(
        Long id,
        Long productoId,
        String itemCodigo,
        TipoEntregaProceso tipo,
        EstadoResponseDTO origen,
        /** null en las bajas. */
        EstadoResponseDTO destino,
        int cantidad,
        String usuario,
        LocalDateTime fecha,
        String observaciones
) {}
