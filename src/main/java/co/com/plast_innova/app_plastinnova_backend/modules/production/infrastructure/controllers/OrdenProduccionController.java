package co.com.plast_innova.app_plastinnova_backend.modules.production.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.AvanceOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EdicionOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.LineaOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.VinculoLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.OrdenProduccionService;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
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
@RequestMapping("/api/v1/ordenes-produccion")
@RequiredArgsConstructor
@Tag(name = "Órdenes de producción",
        description = "Producción planificada de un ítem: etapas, líneas de pedido que atiende y consumo de materiales.")
public class OrdenProduccionController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final OrdenProduccionService ordenService;

    @Operation(summary = "Órdenes de producción",
            description = "De la más reciente. El texto filtra por número, ítem o número de pedido atendido.")
    @GetMapping
    @PreAuthorize("hasAuthority('produccion:ver')")
    public PagedModel<OrdenProduccionResumenDTO> listar(@RequestParam(defaultValue = "") String texto,
                                                        @RequestParam(required = false) EstadoOrdenProduccion estado,
                                                        @RequestParam(required = false) Integer etapaId,
                                                        @RequestParam(defaultValue = "0") int pagina,
                                                        @RequestParam(defaultValue = "25") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(ordenService.listar(texto, estado, etapaId, pageable));
    }

    @Operation(summary = "Detalle de la orden",
            description = "Con las líneas que atiende, los materiales que consume, su historial y sus movimientos.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "La orden"),
            @ApiResponse(responseCode = "404", description = "La orden no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('produccion:ver')")
    public OrdenProduccionDTO obtener(@PathVariable Long id) {
        return ordenService.obtener(id);
    }

    @Operation(summary = "Líneas que puede atender",
            description = "Líneas sin terminar de pedidos abiertos que piden el ítem, sin otra orden activa, con lo que "
                    + "les falta (sin lo comprometido ni lo entregado).")
    @GetMapping("/{id}/candidatas")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public List<LineaOrdenDTO> candidatas(@PathVariable Long id) {
        return ordenService.candidatas(id);
    }

    @Operation(summary = "Crear una orden", description = "Empieza en la etapa inicial del catálogo de líneas.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Orden creada"),
            @ApiResponse(responseCode = "404", description = "El ítem o la bodega no existen",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO crear(@Valid @RequestBody OrdenProduccionRequestDTO request,
                                    @Parameter(hidden = true) Principal principal) {
        return ordenService.crear(request, principal.getName());
    }

    @Operation(summary = "Editar cantidad, fecha requerida u observaciones")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO editar(@PathVariable Long id, @Valid @RequestBody EdicionOrdenRequestDTO request,
                                     @Parameter(hidden = true) Principal principal) {
        return ordenService.editar(id, request, principal.getName());
    }

    @Operation(summary = "Atender una línea de pedido",
            description = "La línea pasa a la etapa de la orden y desde entonces avanza con ella.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Línea vinculada"),
            @ApiResponse(responseCode = "409", description = "Otro producto, ya atendida por otra orden, cantidad mayor "
                    + "a lo que falta o a lo que queda sin asignar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/lineas")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO vincular(@PathVariable Long id, @Valid @RequestBody VinculoLineaRequestDTO request,
                                       @Parameter(hidden = true) Principal principal) {
        return ordenService.vincular(id, request, principal.getName());
    }

    @Operation(summary = "Dejar de atender una línea", description = "La línea queda en su etapa y se reporta por separado.")
    @DeleteMapping("/{id}/lineas/{detalleId}")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO desvincular(@PathVariable Long id, @PathVariable Long detalleId,
                                          @Parameter(hidden = true) Principal principal) {
        return ordenService.desvincular(id, detalleId, principal.getName());
    }

    @Operation(summary = "Reportar avance de etapa",
            description = "Las líneas que atiende pasan con ella. En la etapa de almacén se consumen los componentes "
                    + "(si alguno no alcanza se rechaza), lo producido entra a la bodega y la orden termina.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Avance registrado"),
            @ApiResponse(responseCode = "409", description = "Transición no permitida, más piezas que la etapa anterior, "
                    + "materiales insuficientes o la orden está cerrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/avance")
    @PreAuthorize("hasAuthority('detalle:cambiar-estado')")
    public OrdenProduccionDTO avanzar(@PathVariable Long id, @Valid @RequestBody AvanceOrdenRequestDTO request,
                                      @Parameter(hidden = true) Principal principal) {
        return ordenService.avanzar(id, request, principal.getName());
    }

    @Operation(summary = "Anular la orden", description = "Las líneas que atendía quedan en su etapa.")
    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO anular(@PathVariable Long id, @Valid @RequestBody JustificacionRequestDTO request,
                                     @Parameter(hidden = true) Principal principal) {
        return ordenService.anular(id, request.justificacion(), principal.getName());
    }
}
