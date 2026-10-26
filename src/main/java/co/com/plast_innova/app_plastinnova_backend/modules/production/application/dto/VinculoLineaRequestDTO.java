package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/** Línea de pedido que atenderá la orden y cuántas de sus piezas. */
public record VinculoLineaRequestDTO(
        @NotNull(message = "Indique la línea de pedido")
        Long detalleId,

        @NotNull(message = "Indique cuántas piezas de la orden son para la línea")
        @Positive(message = "La cantidad debe ser mayor que cero")
        Integer cantidad
) {}
