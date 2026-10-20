package co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoDetalleRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CorreccionCantidadRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FiltroLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.HistorialEstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.LineaColaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.MovimientoNoSaleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoDetalleResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.impl.ColaTrabajoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.data.web.PagedModel;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos/detalles")
@RequiredArgsConstructor
@Tag(name = "Líneas de pedido", description = "Avance de las líneas de producción (productos del pedido).")
public class PedidoDetalleController {

    private final PedidoService pedidoService;
    private final ColaTrabajoService colaTrabajoService;

    @Operation(summary = "Cola de trabajo de líneas",
            description = "Líneas pendientes de varios pedidos (sin las de estado final ni las de pedidos cerrados), "
                    + "para la etapa elegida en la tablet. Orden por defecto: las más antiguas primero. Se puede ordenar "
                    + "por fechaCreacionFm, fechaEstimadaEntrega, numeroPedido o posicion.")
    @PostMapping("/busqueda")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public PagedModel<LineaColaDTO> buscarLineas(@RequestBody FiltroLineaRequestDTO filtro,
                                                 @PageableDefault(size = 20) Pageable pageable) {
        return new PagedModel<>(colaTrabajoService.buscar(filtro, pageable));
    }

    @Operation(summary = "Historial de estados de la línea", description = "Cambios del más reciente al más antiguo, con la cantidad reportada en cada fase.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial de la línea"),
            @ApiResponse(responseCode = "404", description = "La línea no existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public List<HistorialEstadoResponseDTO> obtenerHistorial(@PathVariable Long id) {
        return pedidoService.obtenerHistorialDetalle(id);
    }

    @Operation(summary = "Cambiar el estado de una línea y reportar la cantidad procesada",
            description = "Solo se permiten las transiciones configuradas (ver GET /api/v1/catalogos/estados-detalle/{id}/destinos).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado modificado"),
            @ApiResponse(responseCode = "400", description = "Errores de validación en el payload", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La línea o el estado no existen", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transición no permitida, cantidad mayor a la solicitada, línea eliminada en FileMaker o modificada al mismo tiempo por otro usuario", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('detalle:cambiar-estado')")
    public ResponseEntity<PedidoDetalleResponseDTO> cambiarEstadoDetalle(
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoDetalleRequestDTO request,
            @Parameter(hidden = true) Principal principal) {

        // Extracción del usuario autenticado (JWT) para mantener la trazabilidad obligatoria
        String emailOperador = principal.getName();

        PedidoDetalleResponseDTO response = pedidoService.transicionarEstadoDetalle(id, request, emailOperador);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Corregir las piezas reportadas en la etapa anterior",
            description = "Para cuando una etapa reportó menos (o más) piezas de las reales: la siguiente etapa no puede "
                    + "reportar más de lo que reportó la anterior. Exige justificación y queda en el historial de la línea "
                    + "como una corrección (no cambia la etapa).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Cantidad corregida"),
            @ApiResponse(responseCode = "400", description = "Errores de validación en el payload (justificación obligatoria)", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La línea no existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Sin piezas reportadas, cantidad igual a la actual o mayor a la solicitada, línea eliminada en FileMaker o modificada al mismo tiempo por otro usuario", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}/cantidad")
    @PreAuthorize("hasAuthority('detalle:corregir-cantidad')")
    public PedidoDetalleResponseDTO corregirCantidad(
            @PathVariable Long id,
            @Valid @RequestBody CorreccionCantidadRequestDTO request,
            @Parameter(hidden = true) Principal principal) {
        return pedidoService.corregirCantidadDetalle(id, request, principal.getName());
    }

    @Operation(summary = "Marcar una línea como No sale (supervisor)",
            description = "La línea no se despacha por no disponibilidad del producto; no cambia sus piezas. Si todas las "
                    + "líneas del pedido quedan en No sale, el pedido pasa a Anulado. Exige justificación.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Línea en No sale (y el estado del pedido)"),
            @ApiResponse(responseCode = "400", description = "Justificación faltante o fuera de 10 a 500 caracteres", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La línea no existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Ya está en No sale o en otra etapa final, pedido cerrado o línea eliminada en FileMaker", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}/no-sale")
    @PreAuthorize("hasAuthority('detalle:no-sale')")
    public MovimientoNoSaleResponseDTO marcarNoSale(
            @PathVariable Long id,
            @Valid @RequestBody JustificacionRequestDTO request,
            @Parameter(hidden = true) Principal principal) {
        return pedidoService.marcarNoSale(id, request, principal.getName());
    }

    @Operation(summary = "Devolver una línea en No sale a su etapa (supervisor)",
            description = "La línea vuelve a la etapa en que estaba al marcarse No sale. Si el pedido estaba anulado, "
                    + "se reabre en el estado que tenía. Exige justificación.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Línea devuelta (y el estado del pedido)"),
            @ApiResponse(responseCode = "400", description = "Justificación faltante o fuera de 10 a 500 caracteres", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "La línea no existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "La línea no está en No sale, el pedido está cerrado (no anulado) o la línea se eliminó en FileMaker", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}/devolucion")
    @PreAuthorize("hasAuthority('detalle:no-sale')")
    public MovimientoNoSaleResponseDTO devolverLinea(
            @PathVariable Long id,
            @Valid @RequestBody JustificacionRequestDTO request,
            @Parameter(hidden = true) Principal principal) {
        return pedidoService.devolverLinea(id, request, principal.getName());
    }
}
