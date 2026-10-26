package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record LineaRemisionRequestDTO(
        @NotNull(message = "Indique la línea de pedido")
        Long detalleId,

        @NotNull(message = "Indique las piezas que se entregan")
        @Positive(message = "Las piezas deben ser más de cero")
        Integer cantidad
) {}
