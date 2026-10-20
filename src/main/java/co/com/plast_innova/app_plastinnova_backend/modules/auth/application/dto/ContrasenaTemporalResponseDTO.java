package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto;

/**
 * Resultado de crear un usuario o restablecer su contraseña. La contraseña temporal solo se entrega aquí (no se
 * guarda en claro): el administrador se la comunica al usuario, que debe cambiarla al ingresar.
 */
public record ContrasenaTemporalResponseDTO(
        UsuarioResponseDTO usuario,
        String contrasenaTemporal
) {}
