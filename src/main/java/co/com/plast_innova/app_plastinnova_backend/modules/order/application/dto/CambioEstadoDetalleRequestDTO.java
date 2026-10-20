package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoDetalleRequestDTO(
        @NotNull(message = "El ID del nuevo estado es obligatorio")
        Integer nuevoEstadoId,

        /** Piezas procesadas en esta fase; no puede superar la cantidad solicitada de la línea. */
        @NotNull(message = "Debe indicar la cantidad procesada en esta fase")
        @Min(value = 0, message = "La cantidad procesada no puede ser negativa")
        Integer cantidadProcesada,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones
) {
}
