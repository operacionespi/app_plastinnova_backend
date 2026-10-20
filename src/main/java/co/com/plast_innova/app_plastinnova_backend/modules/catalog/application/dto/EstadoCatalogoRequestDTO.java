package co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Datos para crear o modificar un estado. El código se normaliza (sin tildes, mayúsculas, "_"):
 * "En ensamble" → "EN_ENSAMBLE". La sincronización compara los estados de FileMaker contra el código y el nombre.
 */
public record EstadoCatalogoRequestDTO(
        @NotBlank(message = "El código es obligatorio")
        @Size(max = 50, message = "El código no puede exceder 50 caracteres")
        String codigo,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 100, message = "El nombre no puede exceder 100 caracteres")
        String nombre,

        @Size(max = 255, message = "La descripción no puede exceder 255 caracteres")
        String descripcion,

        @NotNull(message = "El orden lógico es obligatorio")
        @Min(value = 0, message = "El orden lógico no puede ser negativo")
        Integer ordenLogico,

        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "El color debe tener el formato #RRGGBB")
        String color,

        /** Si es true, este pasa a ser el estado inicial y el anterior deja de serlo. Opcional: por defecto false. */
        Boolean esInicial,

        /** Opcional: por defecto false. */
        Boolean esFinal
) {
    public EstadoCatalogoRequestDTO {
        esInicial = Boolean.TRUE.equals(esInicial);
        esFinal = Boolean.TRUE.equals(esFinal);
    }
}
