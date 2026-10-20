package co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.services.PedidoService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.CambioEstadoPedidoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.FechaEntregaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.HistorialEstadoResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.PedidoResponseDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/pedidos")
@RequiredArgsConstructor
@Tag(name = "Pedidos (Agregado Raíz)", description = "Endpoints transaccionales para la consulta y transición global de los pedidos operativos.")
public class PedidoController {

    private final PedidoService pedidoService;

    @Operation(summary = "Obtener detalle completo de un pedido", description = "Retorna el agregado raíz del pedido, incluyendo cliente, estado actual y sus líneas de producción (detalles).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Pedido encontrado y retornado exitosamente"),
            @ApiResponse(responseCode = "404", description = "El pedido no existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public ResponseEntity<PedidoResponseDTO> obtenerPedido(
            @Parameter(description = "ID interno (SQL Server) del pedido", required = true)
            @PathVariable Long id) {

        PedidoResponseDTO response = pedidoService.obtenerPedidoPorId(id);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Historial de estados del pedido", description = "Cambios de estado del más reciente al más antiguo, con el usuario que los hizo (o SYNC si vino de FileMaker).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Historial del pedido"),
            @ApiResponse(responseCode = "404", description = "El pedido no existe", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/{id}/historial")
    @PreAuthorize("hasAuthority('pedido:ver')")
    public List<HistorialEstadoResponseDTO> obtenerHistorial(@PathVariable Long id) {
        return pedidoService.obtenerHistorialPedido(id);
    }

    @Operation(summary = "Transicionar estado global del pedido",
            description = "Solo se permiten las transiciones configuradas en el catálogo (ver GET /api/v1/catalogos/estados-pedido/{id}/destinos). "
                    + "Al llegar a un estado final se registra la fecha de cierre.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estado modificado exitosamente. Retorna el snapshot actualizado del pedido."),
            @ApiResponse(responseCode = "400", description = "Errores de validación en el payload.", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "El pedido o el nuevo estado de catálogo no existen.", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Transición no permitida, estado inactivo, pedido eliminado en FileMaker o modificado al mismo tiempo por otro usuario.", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}/estado")
    @PreAuthorize("hasAuthority('pedido:cambiar-estado')")
    public ResponseEntity<PedidoResponseDTO> cambiarEstadoPedido(
            @Parameter(description = "ID interno (SQL Server) del pedido", required = true)
            @PathVariable Long id,
            @Valid @RequestBody CambioEstadoPedidoRequestDTO request,
            @Parameter(hidden = true) Principal principal) {

        // Excluimos Principal de la documentación de Swagger con @Parameter(hidden = true)
        // ya que este valor se inyecta en el servidor vía el token JWT, no por el cliente.
        // El endpoint exige autenticación, por lo que el principal nunca es nulo (sin usuario por defecto).
        String emailOperador = principal.getName();

        PedidoResponseDTO response = pedidoService.transicionarEstadoPedido(id, request, emailOperador);
        return ResponseEntity.ok(response);
    }

    @Operation(summary = "Asignar la fecha estimada de entrega",
            description = "Día comprometido de entrega (yyyy-MM-dd); null la quita. FileMaker no la envía: se administra aquí "
                    + "y cada cambio queda en la auditoría. El pedido se considera atrasado desde el día siguiente.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Fecha asignada. Retorna el snapshot actualizado del pedido."),
            @ApiResponse(responseCode = "400", description = "Fecha con formato inválido.", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "El pedido no existe.", content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Pedido cerrado, eliminado en FileMaker o modificado al mismo tiempo por otro usuario.", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/{id}/fecha-estimada-entrega")
    @PreAuthorize("hasAuthority('pedido:fecha-entrega')")
    public PedidoResponseDTO asignarFechaEstimadaEntrega(
            @Parameter(description = "ID interno (SQL Server) del pedido", required = true)
            @PathVariable Long id,
            @RequestBody FechaEntregaRequestDTO request,
            @Parameter(hidden = true) Principal principal) {
        return pedidoService.asignarFechaEstimadaEntrega(id, request, principal.getName());
    }
}
