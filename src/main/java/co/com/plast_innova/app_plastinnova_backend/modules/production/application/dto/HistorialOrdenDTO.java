package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.TipoHistorialOrden;

import java.time.LocalDateTime;

public record HistorialOrdenDTO(
        Long id,
        TipoHistorialOrden tipo,
        EstadoResponseDTO etapaAnterior,
        EstadoResponseDTO etapa,
        Integer cantidad,
        String usuario,
        LocalDateTime fecha,
        String observaciones
) {}
