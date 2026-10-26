package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Ítems que pasan a seguir la ruta (si tenían otra, la cambian). */
public record AsignacionItemsRequestDTO(
        @NotEmpty(message = "Indique al menos un ítem")
        @Size(max = 500, message = "No se pueden asignar más de 500 ítems a la vez")
        List<@NotBlank String> codigos
) {}
