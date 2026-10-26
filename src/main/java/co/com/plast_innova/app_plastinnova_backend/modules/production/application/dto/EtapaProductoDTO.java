package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;

/** Piezas en un proceso. */
public record EtapaProductoDTO(
        EstadoResponseDTO etapa,
        int cantidad
) {}
