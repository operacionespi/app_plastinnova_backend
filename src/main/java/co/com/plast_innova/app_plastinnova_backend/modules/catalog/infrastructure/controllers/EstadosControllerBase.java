package co.com.plast_innova.app_plastinnova_backend.modules.catalog.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.EstadoCatalogoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.ResultadoEliminacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.TransicionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.dto.TransicionResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

/**
 * Endpoints comunes a los catálogos de estados. Cualquier usuario autenticado consulta;
 * crear, modificar y eliminar requiere el permiso estado:gestionar.
 */
public abstract class EstadosControllerBase {

    private final GestionEstadosService<?, ?> service;

    protected EstadosControllerBase(GestionEstadosService<?, ?> service) {
        this.service = service;
    }

    @Operation(summary = "Listar estados", description = "Ordenados por orden lógico. Por defecto solo los activos.")
    @GetMapping
    public List<EstadoCatalogoResponseDTO> listar(@RequestParam(defaultValue = "false") boolean incluirInactivos) {
        return service.listar(incluirInactivos);
    }

    @Operation(summary = "Consultar un estado")
    @GetMapping("/{id}")
    public EstadoCatalogoResponseDTO obtener(@PathVariable Integer id) {
        return service.obtener(id);
    }

    @Operation(summary = "Estados a los que se puede pasar desde este estado",
            description = "Solo los destinos activos de las transiciones configuradas.")
    @GetMapping("/{id}/destinos")
    public List<EstadoCatalogoResponseDTO> destinos(@PathVariable Integer id) {
        return service.destinosPermitidos(id);
    }

    @Operation(summary = "Crear un estado",
            description = "Si se marca como inicial, el estado inicial anterior deja de serlo.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Estado creado"),
            @ApiResponse(responseCode = "409", description = "Código duplicado",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @PreAuthorize("hasAuthority('estado:gestionar')")
    public ResponseEntity<EstadoCatalogoResponseDTO> crear(@Valid @RequestBody EstadoCatalogoRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(request));
    }

    @Operation(summary = "Modificar un estado",
            description = "Para cambiar el estado inicial, marque el nuevo como inicial (no se puede desmarcar el actual).")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('estado:gestionar')")
    public EstadoCatalogoResponseDTO actualizar(@PathVariable Integer id, @Valid @RequestBody EstadoCatalogoRequestDTO request) {
        return service.actualizar(id, request);
    }

    @Operation(summary = "Reactivar un estado desactivado")
    @PatchMapping("/{id}/activar")
    @PreAuthorize("hasAuthority('estado:gestionar')")
    public EstadoCatalogoResponseDTO activar(@PathVariable Integer id) {
        return service.activar(id);
    }

    @Operation(summary = "Eliminar un estado",
            description = "Si el estado nunca se usó se elimina con sus transiciones; si está en uso se desactiva. "
                    + "La respuesta indica cuál de las dos acciones se aplicó.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado eliminado o desactivado"),
            @ApiResponse(responseCode = "409", description = "Es el estado inicial",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAuthority('estado:gestionar')")
    public ResultadoEliminacionDTO eliminar(@PathVariable Integer id) {
        return service.eliminar(id);
    }

    @Operation(summary = "Listar las transiciones permitidas")
    @GetMapping("/transiciones")
    public List<TransicionResponseDTO> listarTransiciones() {
        return service.listarTransiciones();
    }

    @Operation(summary = "Permitir una transición (origen → destino)")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Transición creada"),
            @ApiResponse(responseCode = "409", description = "Ya existe, es el mismo estado o alguno está inactivo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/transiciones")
    @PreAuthorize("hasAuthority('estado:gestionar')")
    public ResponseEntity<TransicionResponseDTO> crearTransicion(@Valid @RequestBody TransicionRequestDTO request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crearTransicion(request));
    }

    @Operation(summary = "Eliminar una transición")
    @DeleteMapping("/transiciones/{id}")
    @PreAuthorize("hasAuthority('estado:gestionar')")
    public ResponseEntity<Void> eliminarTransicion(@PathVariable Integer id) {
        service.eliminarTransicion(id);
        return ResponseEntity.noContent().build();
    }
}
