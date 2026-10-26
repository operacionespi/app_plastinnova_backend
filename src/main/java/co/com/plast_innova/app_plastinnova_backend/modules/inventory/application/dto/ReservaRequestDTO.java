package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;

/** Cantidad que queda comprometida para la línea (reemplaza la anterior); 0 la libera. */
public record ReservaRequestDTO(
        @NotNull(message = "La cantidad es obligatoria")
        @PositiveOrZero(message = "La cantidad no puede ser negativa")
        @Digits(integer = 12, fraction = 6, message = "La cantidad admite hasta 6 decimales")
        BigDecimal cantidad
) {}
