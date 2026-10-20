package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

/** Usuario nuevo: la contraseña temporal la genera el sistema. */
public record UsuarioCreacionRequestDTO(
        @NotBlank(message = "El correo es obligatorio")
        @Email(message = "El correo no es válido")
        @Size(max = 150, message = "El correo no puede pasar de 150 caracteres")
        String email,

        @NotBlank(message = "El nombre es obligatorio")
        @Size(max = 150, message = "El nombre no puede pasar de 150 caracteres")
        String nombreCompleto,

        @NotEmpty(message = "Asigne al menos un rol")
        List<String> roles
) {}
