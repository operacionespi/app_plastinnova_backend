package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto;

import java.util.List;

/**
 * Usuario autenticado: el frontend arma el menú y oculta acciones según sus permisos.
 *
 * @param roles    códigos de rol, ej. OPERADOR
 * @param permisos códigos de permiso de todos sus roles, ej. detalle:cambiar-estado (vacío mientras deba cambiar
 *                 la contraseña)
 * @param debeCambiarContrasena entró con una contraseña temporal: debe cambiarla antes de usar la app
 */
public record UsuarioActualDTO(
        Integer id,
        String email,
        String nombreCompleto,
        List<String> roles,
        List<String> permisos,
        boolean debeCambiarContrasena
) {}
