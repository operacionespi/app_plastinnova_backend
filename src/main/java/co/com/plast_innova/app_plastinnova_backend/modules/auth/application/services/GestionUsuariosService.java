package co.com.plast_innova.app_plastinnova_backend.modules.auth.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.ContrasenaTemporalResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.RolResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioCreacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioEdicionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Rol;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.RolRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories.UsuarioRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.AuditoriaLog;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.FechaUtc;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Texto;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.SolicitudInvalidaException;
import co.com.plast_innova.app_plastinnova_backend.shared.repositories.AuditoriaLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.text.Normalizer;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

/**
 * Administración de usuarios (solo el rol Administrador, permiso usuario:gestionar).
 * <ul>
 *   <li>Los usuarios no se eliminan (están en el historial): se desactivan.</li>
 *   <li>El sistema genera la contraseña temporal; el usuario no tiene permisos hasta que la cambia.</li>
 *   <li>Restablecer la contraseña o desactivar cierra las sesiones abiertas del usuario.</li>
 *   <li>El administrador no se desactiva ni se quita su propio rol, y siempre queda al menos un administrador activo.</li>
 *   <li>Cada cambio queda en Auditoria_Log (sin contraseñas).</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class GestionUsuariosService {

    static final String ROL_ADMIN = "ADMIN";
    /** Sin caracteres que se confunden al dictarlos o leerlos (0/O, 1/l/I). */
    private static final String ALFABETO = "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnpqrstuvwxyz23456789";
    private static final SecureRandom ALEATORIO = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final RolRepository rolRepository;
    private final AuditoriaLogRepository auditoriaRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<UsuarioResponseDTO> listar(String texto, boolean incluirInactivos) {
        String buscado = sinTildes(Texto.limpiar(texto));
        return usuarioRepository.findAllConRoles().stream()
                .filter(u -> incluirInactivos || Boolean.TRUE.equals(u.getActivo()))
                .filter(u -> buscado == null
                        || sinTildes(u.getNombreCompleto()).contains(buscado)
                        || sinTildes(u.getEmail()).contains(buscado))
                .map(GestionUsuariosService::dto)
                .toList();
    }

    @Transactional(readOnly = true)
    public UsuarioResponseDTO obtener(Integer id) {
        return dto(buscar(id));
    }

    @Transactional(readOnly = true)
    public List<RolResponseDTO> listarRoles() {
        return rolRepository.findAllByOrderByIdAsc().stream()
                .map(r -> new RolResponseDTO(r.getCodigo(), r.getDescripcion()))
                .toList();
    }

    @Transactional
    public ContrasenaTemporalResponseDTO crear(UsuarioCreacionRequestDTO request, String emailAdministrador) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (usuarioRepository.existsByEmailIgnoreCase(email)) {
            throw new BusinessRuleException("Ya existe un usuario con el correo " + email + ".");
        }
        String temporal = contrasenaTemporal();
        Usuario usuario = new Usuario();
        usuario.setEmail(email);
        usuario.setNombreCompleto(Texto.limpiar(request.nombreCompleto()));
        usuario.setPasswordHash(passwordEncoder.encode(temporal));
        usuario.setDebeCambiarContrasena(true);
        usuario.setRoles(roles(request.roles()));
        usuarioRepository.save(usuario);

        auditar(usuario, "creacion", null, email + " · " + codigos(usuario.getRoles()), emailAdministrador);
        return new ContrasenaTemporalResponseDTO(dto(usuario), temporal);
    }

    @Transactional
    public UsuarioResponseDTO actualizar(Integer id, UsuarioEdicionRequestDTO request, String emailAdministrador) {
        Usuario usuario = buscar(id);
        Set<Rol> nuevos = roles(request.roles());
        boolean pierdeAdmin = tieneRol(usuario.getRoles(), ROL_ADMIN) && !tieneRol(nuevos, ROL_ADMIN);
        if (pierdeAdmin && esElMismo(usuario, emailAdministrador)) {
            throw new BusinessRuleException("No puede quitarse su propio rol de Administrador.");
        }
        if (pierdeAdmin && Boolean.TRUE.equals(usuario.getActivo())) {
            validarOtroAdministrador(usuario);
        }

        String nombre = Texto.limpiar(request.nombreCompleto());
        if (!nombre.equals(usuario.getNombreCompleto())) {
            auditar(usuario, "nombre_completo", usuario.getNombreCompleto(), nombre, emailAdministrador);
            usuario.setNombreCompleto(nombre);
        }
        String rolesAntes = codigos(usuario.getRoles());
        String rolesDespues = codigos(nuevos);
        if (!rolesAntes.equals(rolesDespues)) {
            auditar(usuario, "roles", rolesAntes, rolesDespues, emailAdministrador);
            usuario.setRoles(nuevos);
        }
        return dto(usuario);
    }

    @Transactional
    public UsuarioResponseDTO activar(Integer id, String emailAdministrador) {
        Usuario usuario = buscar(id);
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            usuario.setActivo(true);
            auditar(usuario, "activo", "false", "true", emailAdministrador);
        }
        return dto(usuario);
    }

    /** Deja de poder ingresar de inmediato: sus tokens se rechazan desde ahora (y siguen inválidos si se reactiva). */
    @Transactional
    public UsuarioResponseDTO desactivar(Integer id, String emailAdministrador) {
        Usuario usuario = buscar(id);
        if (esElMismo(usuario, emailAdministrador)) {
            throw new BusinessRuleException("No puede desactivar su propio usuario.");
        }
        if (Boolean.TRUE.equals(usuario.getActivo())) {
            if (tieneRol(usuario.getRoles(), ROL_ADMIN)) {
                validarOtroAdministrador(usuario);
            }
            usuario.setActivo(false);
            usuario.setSesionesValidasDesde(FechaUtc.ahora());
            auditar(usuario, "activo", "true", "false", emailAdministrador);
        }
        return dto(usuario);
    }

    /** Nueva contraseña temporal: cierra las sesiones abiertas y obliga a cambiarla al ingresar. */
    @Transactional
    public ContrasenaTemporalResponseDTO restablecerContrasena(Integer id, String emailAdministrador) {
        Usuario usuario = buscar(id);
        if (esElMismo(usuario, emailAdministrador)) {
            throw new BusinessRuleException("Para su propia contraseña use \"Cambiar contraseña\" en su cuenta.");
        }
        if (!Boolean.TRUE.equals(usuario.getActivo())) {
            throw new BusinessRuleException("El usuario está inactivo: actívelo antes de restablecer su contraseña.");
        }
        String temporal = contrasenaTemporal();
        usuario.setPasswordHash(passwordEncoder.encode(temporal));
        usuario.setDebeCambiarContrasena(true);
        usuario.setSesionesValidasDesde(FechaUtc.ahora());
        auditar(usuario, "contrasena", null, "restablecida", emailAdministrador);
        return new ContrasenaTemporalResponseDTO(dto(usuario), temporal);
    }

    // ------------------------------------------------------------------ apoyo

    private Usuario buscar(Integer id) {
        return usuarioRepository.findByIdConRoles(id)
                .orElseThrow(() -> new ResourceNotFoundException("El usuario " + id + " no existe."));
    }

    private Set<Rol> roles(List<String> codigos) {
        Set<String> pedidos = codigos.stream().filter(Objects::nonNull).map(String::trim).collect(Collectors.toSet());
        List<Rol> encontrados = rolRepository.findByCodigoIn(pedidos);
        if (encontrados.size() != pedidos.size()) {
            Set<String> existentes = encontrados.stream().map(Rol::getCodigo).collect(Collectors.toSet());
            throw new SolicitudInvalidaException("Roles inexistentes: "
                    + pedidos.stream().filter(c -> !existentes.contains(c)).sorted().collect(Collectors.joining(", ")) + ".");
        }
        return new HashSet<>(encontrados);
    }

    private void validarOtroAdministrador(Usuario usuario) {
        if (usuarioRepository.contarOtrosAdministradoresActivos(usuario.getId()) == 0) {
            throw new BusinessRuleException(usuario.getNombreCompleto()
                    + " es el único administrador activo: asigne el rol a otro usuario primero.");
        }
    }

    private void auditar(Usuario usuario, String campo, String anterior, String nuevo, String emailAdministrador) {
        auditoriaRepository.save(AuditoriaLog.builder()
                .tablaAfectada("Usuario")
                .registroId(usuario.getId().toString())
                .campoModificado(campo)
                .valorAnterior(anterior)
                .valorNuevo(nuevo)
                .usuario(usuarioRepository.findByEmailAndActivoTrue(emailAdministrador).orElse(null))
                .build());
    }

    /** 4 grupos de 5 (ej. JXnCh-XwUN7-X9xeA-VP2jn): ~114 bits, fácil de dictar. */
    static String contrasenaTemporal() {
        StringBuilder texto = new StringBuilder();
        for (int grupo = 0; grupo < 4; grupo++) {
            if (grupo > 0) {
                texto.append('-');
            }
            for (int i = 0; i < 5; i++) {
                texto.append(ALFABETO.charAt(ALEATORIO.nextInt(ALFABETO.length())));
            }
        }
        return texto.toString();
    }

    private static boolean esElMismo(Usuario usuario, String email) {
        return usuario.getEmail().equalsIgnoreCase(email);
    }

    private static boolean tieneRol(Set<Rol> roles, String codigo) {
        return roles.stream().anyMatch(r -> codigo.equals(r.getCodigo()));
    }

    private static String codigos(Set<Rol> roles) {
        return String.join(", ", roles.stream().map(Rol::getCodigo).collect(Collectors.toCollection(TreeSet::new)));
    }

    private static String sinTildes(String texto) {
        return texto == null ? null : Normalizer.normalize(texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT);
    }

    static UsuarioResponseDTO dto(Usuario u) {
        return new UsuarioResponseDTO(u.getId(), u.getEmail(), u.getNombreCompleto(), Boolean.TRUE.equals(u.getActivo()),
                u.getRoles().stream().map(Rol::getCodigo).sorted().toList(),
                Boolean.TRUE.equals(u.getDebeCambiarContrasena()), u.getFechaCreacion());
    }
}
