package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto;

import jakarta.validation.constraints.NotNull;

public record TransicionRequestDTO(
        @NotNull(message = "El estado de origen es obligatorio")
        Integer estadoOrigenId,

        @NotNull(message = "El estado de destino es obligatorio")
        Integer estadoDestinoId
) {
}
