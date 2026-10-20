package co.com.plast_innova.app_plastinnova_backend.security.services;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.UsuarioRepository;
import co.com.plast_innova.app_plastinnova_backend.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Carga el usuario con sus roles y permisos para Spring Security.
 * Vive fuera de SecurityConfig para evitar el ciclo SecurityConfig -> JwtAuthenticationFilter -> UserDetailsService.
 */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final UsuarioRepository usuarioRepository;

    @Override
    public UserDetails loadUserByUsername(String username) {
        return usuarioRepository.findByEmailWithRolesAndPermissions(username)
                .map(CustomUserDetails::new)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado en el sistema"));
    }
}
