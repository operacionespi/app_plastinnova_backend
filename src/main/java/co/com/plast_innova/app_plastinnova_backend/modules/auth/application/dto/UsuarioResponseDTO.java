package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Usuario visto por el administrador.
 *
 * @param roles                 códigos de rol, ej. SUPERVISOR
 * @param debeCambiarContrasena tiene una contraseña temporal que aún no cambia
 * @param fechaCreacion         UTC
 */
public record UsuarioResponseDTO(
        Integer id,
        String email,
        String nombreCompleto,
        boolean activo,
        List<String> roles,
        boolean debeCambiarContrasena,
        LocalDateTime fechaCreacion
) {}
