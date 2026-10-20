package co.com.plast_innova.app_plastinnova_backend.modules.auth.infrastructure.controller;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.CambioContrasenaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.dto.UsuarioActualDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.auth.application.services.CuentaService;
import co.com.plast_innova.app_plastinnova_backend.security.services.JwtService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthenticationManager authenticationManager;
    private final UserDetailsService userDetailsService;
    private final JwtService jwtService;
    private final CuentaService cuentaService;

    public record LoginRequestDTO(
            @NotBlank(message = "El email es obligatorio") @Email(message = "Formato de email inválido") String email,
            @NotBlank(message = "La contraseña es obligatoria") String password
    ) {}

    public record TokenResponseDTO(String accessToken, String tokenType) {}

    @PostMapping("/login")
    public ResponseEntity<TokenResponseDTO> authenticate(@Valid @RequestBody LoginRequestDTO request) {
        // Delega la validación de la contraseña encriptada (BCrypt) al AuthenticationManager
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.email(), request.password())
        );

        // Si la autenticación es exitosa, se genera el JWT
        UserDetails user = userDetailsService.loadUserByUsername(request.email());
        String jwtToken = jwtService.generateToken(user);

        return ResponseEntity.ok(new TokenResponseDTO(jwtToken, "Bearer"));
    }

    @Operation(summary = "Usuario autenticado", description = "Nombre, roles y permisos del dueño del token.")
    @GetMapping("/me")
    public UsuarioActualDTO usuarioActual(@Parameter(hidden = true) Principal principal) {
        return cuentaService.obtener(principal.getName());
    }

    @Operation(summary = "Cambiar la contraseña propia",
            description = "Exige la contraseña actual. Los tokens ya emitidos siguen vigentes hasta que expiran.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "204", description = "Contraseña cambiada"),
            @ApiResponse(responseCode = "400", description = "Contraseña actual incorrecta o nueva inválida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/contrasena")
    public ResponseEntity<Void> cambiarContrasena(@Valid @RequestBody CambioContrasenaRequestDTO request,
                                                  @Parameter(hidden = true) Principal principal) {
        cuentaService.cambiarContrasena(principal.getName(), request);
        return ResponseEntity.noContent().build();
    }
}