package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EdicionOrdenRequestDTO(
        @NotNull(message = "Indique la cantidad a producir")
        @Positive(message = "La cantidad debe ser mayor que cero")
        @Max(value = 1_000_000, message = "La cantidad no puede pasar de 1.000.000")
        Integer cantidad,

        LocalDate fechaRequerida,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones
) {}
