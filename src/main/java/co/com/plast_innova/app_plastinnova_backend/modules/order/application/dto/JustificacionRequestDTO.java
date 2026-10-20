package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Acciones del supervisor sobre una línea que siempre exigen explicar el porqué (No sale, devolver). */
public record JustificacionRequestDTO(
        @NotBlank(message = "Debe escribir la justificación")
        @Size(min = 10, max = 500, message = "La justificación debe tener entre 10 y 500 caracteres")
        String justificacion
) {
}
