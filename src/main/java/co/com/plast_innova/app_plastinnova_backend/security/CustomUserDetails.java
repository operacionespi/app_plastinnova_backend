package co.com.plast_innova.app_plastinnova_backend.security;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

@RequiredArgsConstructor
public class CustomUserDetails implements UserDetails {

    private final Usuario usuario;

    /** Para validar el token contra sesionesValidasDesde (ver JwtService). */
    public Usuario getUsuario() {
        return usuario;
    }

    /**
     * Con contraseña temporal no tiene permisos: solo puede consultar su cuenta y cambiarla (los endpoints de
     * /api/v1/auth no exigen permisos; todos los demás sí).
     */
    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        Set<GrantedAuthority> authorities = new HashSet<>();
        if (Boolean.TRUE.equals(usuario.getDebeCambiarContrasena())) {
            return authorities;
        }
        usuario.getRoles().forEach(rol -> {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + rol.getCodigo()));
            rol.getPermisos().forEach(permiso ->
                    authorities.add(new SimpleGrantedAuthority(permiso.getCodigo()))
            );
        });
        return authorities;
    }

    @Override
    public String getPassword() {
        return usuario.getPasswordHash();
    }

    @Override
    public String getUsername() {
        return usuario.getEmail(); // El email es nuestro identificador principal
    }

    @Override
    public boolean isAccountNonExpired() { return true; }
    @Override
    public boolean isAccountNonLocked() { return true; }
    @Override
    public boolean isCredentialsNonExpired() { return true; }
    @Override
    public boolean isEnabled() { return usuario.getActivo(); }
}