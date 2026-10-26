package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * Un proceso entrega piezas de un producto: al siguiente de la ruta (o al almacén desde el último) o, como
 * devolución, a uno anterior.
 */
public record EntregaProcesoRequestDTO(
        @NotNull(message = "Indique el producto")
        Long productoId,

        @NotNull(message = "Indique el proceso que entrega")
        Integer etapaOrigenId,

        @NotNull(message = "Indique el proceso que recibe")
        Integer etapaDestinoId,

        @NotNull(message = "Indique las piezas que entrega")
        @Positive(message = "Las piezas deben ser más de cero")
        Integer cantidad,

        /** Obligatorias en las devoluciones: el motivo del rechazo. */
        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones
) {}
