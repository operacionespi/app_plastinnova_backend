package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

public record AvanceOrdenRequestDTO(
        @NotNull(message = "Indique la etapa a la que pasa la orden")
        Integer etapaId,

        /** Piezas que pasan a la etapa; no más de las que reportó la anterior. */
        @NotNull(message = "Indique las piezas que pasan a la etapa")
        @Positive(message = "Las piezas deben ser más de cero")
        Integer cantidad,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones
) {}
