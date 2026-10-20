package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Corrige las piezas reportadas en la etapa anterior de una línea. */
public record CorreccionCantidadRequestDTO(
        /** Piezas correctas de la etapa anterior; no puede superar la cantidad solicitada de la línea. */
        @NotNull(message = "Debe indicar la cantidad correcta")
        @Min(value = 0, message = "La cantidad no puede ser negativa")
        Integer cantidadProcesada,

        @NotBlank(message = "Debe justificar la corrección")
        @Size(min = 10, max = 500, message = "La justificación debe tener entre 10 y 500 caracteres")
        String justificacion
) {
}
