package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.CambioContrasenaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioActualDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Permiso;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Rol;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.UsuarioRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** Datos y contraseña del propio usuario autenticado. */
@Service
@RequiredArgsConstructor
public class CuentaService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public UsuarioActualDTO obtener(String email) {
        Usuario usuario = buscar(email);
        boolean temporal = Boolean.TRUE.equals(usuario.getDebeCambiarContrasena());
        return new UsuarioActualDTO(
                usuario.getId(),
                usuario.getEmail(),
                usuario.getNombreCompleto(),
                usuario.getRoles().stream().map(Rol::getCodigo).sorted().toList(),
                // Igual que CustomUserDetails: con contraseña temporal no tiene permisos
                temporal ? List.of() : usuario.getRoles().stream().flatMap(r -> r.getPermisos().stream())
                        .map(Permiso::getCodigo).distinct().sorted().toList(),
                temporal);
    }

    /** Los tokens ya emitidos siguen vigentes hasta que expiran (JWT sin estado). */
    @Transactional
    public void cambiarContrasena(String email, CambioContrasenaRequestDTO request) {
        Usuario usuario = buscar(email);
        if (!passwordEncoder.matches(request.contrasenaActual(), usuario.getPasswordHash())) {
            throw new SolicitudInvalidaException("La contraseña actual no es correcta.");
        }
        if (request.contrasenaNueva().equals(request.contrasenaActual())) {
            throw new SolicitudInvalidaException("La contraseña nueva debe ser distinta de la actual.");
        }
        usuario.setPasswordHash(passwordEncoder.encode(request.contrasenaNueva()));
        // Con la contraseña propia recupera sus permisos (el token actual sigue sirviendo)
        usuario.setDebeCambiarContrasena(false);
    }

    private Usuario buscar(String email) {
        // El token puede seguir vigente aunque el usuario se haya desactivado después
        return usuarioRepository.findByEmailWithRolesAndPermissions(email)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario " + email + " no existe o está inactivo."));
    }
}
