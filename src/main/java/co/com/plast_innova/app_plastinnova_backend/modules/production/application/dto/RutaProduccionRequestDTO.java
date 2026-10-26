package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record RutaProduccionRequestDTO(
        @NotBlank(message = "Indique el nombre de la ruta")
        @Size(max = 100, message = "El nombre no puede exceder los 100 caracteres")
        String nombre,

        @Size(max = 255, message = "La descripción no puede exceder los 255 caracteres")
        String descripcion,

        /** Procesos en el orden en que se recorren (sin la etapa de almacén, que siempre va al final). */
        @NotEmpty(message = "La ruta debe tener al menos un proceso")
        List<@NotNull Integer> etapaIds,

        Boolean activa
) {}
