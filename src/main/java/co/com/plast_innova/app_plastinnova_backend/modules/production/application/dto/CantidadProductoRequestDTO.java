package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Nueva cantidad del producto: la diferencia entra o sale del primer proceso. */
public record CantidadProductoRequestDTO(
        @NotNull(message = "Indique la cantidad a producir")
        @Positive(message = "La cantidad debe ser mayor que cero")
        @Max(value = 1_000_000, message = "La cantidad no puede pasar de 1.000.000")
        Integer cantidad
) {}
