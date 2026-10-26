package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

public record OrdenProduccionRequestDTO(
        LocalDate fechaRequerida,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones,

        @NotEmpty(message = "Indique al menos un producto")
        @Size(max = 100, message = "Una orden no puede tener más de 100 productos")
        List<@NotNull @Valid ProductoOrdenRequestDTO> productos
) {}
