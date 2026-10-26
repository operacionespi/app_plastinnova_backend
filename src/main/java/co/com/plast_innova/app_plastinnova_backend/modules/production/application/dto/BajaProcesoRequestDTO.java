package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Piezas de un producto que se desechan en un proceso. */
public record BajaProcesoRequestDTO(
        @NotNull(message = "Indique el producto")
        Long productoId,

        @NotNull(message = "Indique el proceso donde están las piezas")
        Integer etapaId,

        @NotNull(message = "Indique las piezas que se dan de baja")
        @Positive(message = "Las piezas deben ser más de cero")
        Integer cantidad,

        @NotBlank(message = "Indique el motivo de la baja")
        @Size(max = 500, message = "El motivo no puede exceder los 500 caracteres")
        String motivo
) {}
