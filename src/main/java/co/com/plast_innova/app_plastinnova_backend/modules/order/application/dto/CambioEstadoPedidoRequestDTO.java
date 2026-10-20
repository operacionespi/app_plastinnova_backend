package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CambioEstadoPedidoRequestDTO(
        @NotNull(message = "El ID del nuevo estado es obligatorio")
        Integer nuevoEstadoId,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones
) {
}
