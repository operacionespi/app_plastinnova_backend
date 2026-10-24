package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;

/**
 * Un movimiento registrado a mano. Según el tipo: ENTRADA usa slotDestinoId; SALIDA slotOrigenId; TRASLADO ambos;
 * AJUSTE solo slotDestinoId y la cantidad es lo contado (la app calcula la diferencia). CARGA_INICIAL no se acepta
 * aquí: va por el CSV.
 */
public record MovimientoRequestDTO(
        @NotNull(message = "El tipo es obligatorio")
        TipoMovimientoInventario tipo,
        @NotBlank(message = "El ítem es obligatorio")
        String itemCodigo,
        Integer slotOrigenId,
        Integer slotDestinoId,
        @NotNull(message = "La cantidad es obligatoria")
        @PositiveOrZero(message = "La cantidad no puede ser negativa")
        @Digits(integer = 12, fraction = 6, message = "La cantidad admite hasta 6 decimales")
        BigDecimal cantidad,
        @Size(max = 100, message = "El motivo admite hasta 100 caracteres")
        String motivo,
        @Size(max = 500, message = "Las observaciones admiten hasta 500 caracteres")
        String observaciones
) {}
