package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RemisionRequestDTO(
        @Size(max = 150, message = "El transportador no puede exceder los 150 caracteres")
        String transportador,

        @Size(max = 20, message = "La placa no puede exceder los 20 caracteres")
        String placa,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones,

        @NotEmpty(message = "La remisión debe entregar al menos una línea")
        List<@Valid LineaRemisionRequestDTO> lineas
) {}
