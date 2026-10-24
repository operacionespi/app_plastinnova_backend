package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record SlotRequestDTO(
        @NotNull(message = "La bodega es obligatoria")
        Integer bodegaId,
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 30, message = "El código admite hasta 30 caracteres")
        String codigo,
        @Size(max = 100, message = "La descripción admite hasta 100 caracteres")
        String descripcion,
        /** null = activo. */
        Boolean activo
) {}
