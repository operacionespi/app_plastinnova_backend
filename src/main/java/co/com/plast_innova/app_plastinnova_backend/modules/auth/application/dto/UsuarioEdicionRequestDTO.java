package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** El correo no cambia: es el identificador con el que el usuario ingresa. */
public record UsuarioEdicionRequestDTO(
        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres")
        String nombreCompleto,

        @NotEmpty(message = "Asigne al menos un rol")
        List<String> roles
) {}
