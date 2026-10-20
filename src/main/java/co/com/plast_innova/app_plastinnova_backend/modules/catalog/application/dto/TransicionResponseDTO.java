package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.TransicionEstado;

public record TransicionResponseDTO(
        Integer id,
        EstadoCatalogoResponseDTO estadoOrigen,
        EstadoCatalogoResponseDTO estadoDestino
) {
    public static TransicionResponseDTO de(TransicionEstado<?> transicion) {
        return new TransicionResponseDTO(transicion.getId(),
                EstadoCatalogoResponseDTO.de(transicion.getEstadoOrigen()),
                EstadoCatalogoResponseDTO.de(transicion.getEstadoDestino()));
    }
}
