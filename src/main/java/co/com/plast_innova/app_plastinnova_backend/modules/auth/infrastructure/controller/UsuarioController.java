package co.com.plast_innova.app_plastinnova_backend.modules.auth.infrastructure.controller;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.ContrasenaTemporalResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.RolResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioCreacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioEdicionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.services.GestionUsuariosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/usuarios")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('usuario:gestionar')")
@Tag(name = "Usuarios", description = "Gestión de usuarios y roles (solo el Administrador).")
public class UsuarioController {

    private final GestionUsuariosService gestionUsuarios;

    @Operation(summary = "Usuarios", description = "Ordenados por nombre. El texto busca en el nombre y el correo, sin distinguir tildes.")
    @GetMapping
    public List<UsuarioResponseDTO> listar(@RequestParam(required = false) String texto,
                                           @RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return gestionUsuarios.listar(texto, incluirInactivos);
    }

    @Operation(summary = "Roles que se pueden asignar")
    @GetMapping("/roles")
    public List<RolResponseDTO> roles() {
        return gestionUsuarios.listarRoles();
    }

    @GetMapping("/{id}")
    public UsuarioResponseDTO obtener(@PathVariable Integer id) {
        return gestionUsuarios.obtener(id);
    }

    @Operation(summary = "Crear un usuario",
            description = "Devuelve una contraseña temporal (solo en esta respuesta). El usuario no tiene permisos hasta cambiarla.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Usuario creado con su contraseña temporal"),
            @ApiResponse(responseCode = "400", description = "Datos inválidos o roles inexistentes", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "El correo ya existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContrasenaTemporalResponseDTO crear(@Valid @RequestBody UsuarioCreacionRequestDTO request,
                                               @Parameter(hidden = true) Principal principal) {
        return gestionUsuarios.crear(request, principal.getName());
    }

    @Operation(summary = "Cambiar nombre y roles", description = "El correo no cambia. Los roles nuevos aplican desde la siguiente petición del usuario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario actualizado"),
            @ApiResponse(responseCode = "409", description = "Quitarse el propio rol de Administrador o dejar la app sin administradores", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}")
    public UsuarioResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody UsuarioEdicionRequestDTO request,
                                         @Parameter(hidden = true) Principal principal) {
        return gestionUsuarios.actualizar(id, request, principal.getName());
    }

    @PatchMapping("/{id}/activar")
    public UsuarioResponseDTO activar(@PathVariable Integer id, @Parameter(hidden = true) Principal principal) {
        return gestionUsuarios.activar(id, principal.getName());
    }

    @Operation(summary = "Desactivar un usuario", description = "Deja de poder ingresar de inmediato y se cierran sus sesiones. No se elimina: sigue en el historial.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Usuario desactivado"),
            @ApiResponse(responseCode = "409", description = "Desactivarse a sí mismo o al último administrador", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PatchMapping("/{id}/desactivar")
    public UsuarioResponseDTO desactivar(@PathVariable Integer id, @Parameter(hidden = true) Principal principal) {
        return gestionUsuarios.desactivar(id, principal.getName());
    }

    @Operation(summary = "Restablecer la contraseña",
            description = "Genera una contraseña temporal (solo en esta respuesta), cierra las sesiones del usuario y lo obliga a cambiarla al ingresar.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Contraseña temporal"),
            @ApiResponse(responseCode = "409", description = "Es la propia cuenta o el usuario está inactivo", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/contrasena-temporal")
    public ContrasenaTemporalResponseDTO restablecerContrasena(@PathVariable Integer id,
                                                               @Parameter(hidden = true) Principal principal) {
        return gestionUsuarios.restablecerContrasena(id, principal.getName());
    }
}
