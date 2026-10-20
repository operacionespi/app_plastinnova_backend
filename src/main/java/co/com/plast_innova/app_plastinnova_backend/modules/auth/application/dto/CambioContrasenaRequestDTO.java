package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CambioContrasenaRequestDTO(
        @NotBlank(message = "La contraseña actual es obligatoria")
        String contrasenaActual,

        // BCrypt solo usa los primeros 72 bytes
        @NotBlank(message = "La contraseña nueva es obligatoria")
        @Size(min = 8, max = 72, message = "La contraseña nueva debe tener entre 8 y 72 caracteres")
        String contrasenaNueva
) {}
