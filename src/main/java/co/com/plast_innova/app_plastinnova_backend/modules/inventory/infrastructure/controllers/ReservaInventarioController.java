package co.com.plast_innova.app_plastinnova_backend.modules.inventory.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ReservasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.ReservaInventarioService;
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
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Reservas de inventario",
        description = "Producto terminado de la bodega de producto terminado comprometido por los pedidos.")
public class ReservaInventarioController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ReservaInventarioService reservaService;

    @Operation(summary = "Reservas del pedido",
            description = "Por línea vigente: lo solicitado, lo comprometido, la existencia y lo libre en la bodega.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reservas del pedido"),
            @ApiResponse(responseCode = "404", description = "El pedido no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/pedidos/{id}/reservas")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public ReservasPedidoDTO reservasPedido(@PathVariable Long id) {
        return reservaService.reservasPedido(id);
    }

    @Operation(summary = "Comprometer el pedido",
            description = "Reserva para cada línea pendiente lo que alcance de lo libre, hasta lo solicitado. No reduce "
                    + "las reservas que ya existen.")
    @PostMapping("/pedidos/{id}/reservas/comprometer")
    @PreAuthorize("hasAuthority('inventario:reservar')")
    public ReservasPedidoDTO comprometer(@PathVariable Long id, @Parameter(hidden = true) Principal principal) {
        return reservaService.comprometer(id, principal.getName());
    }

    @Operation(summary = "Liberar el pedido", description = "Lo comprometido por todas sus líneas vuelve a quedar libre.")
    @DeleteMapping("/pedidos/{id}/reservas")
    @PreAuthorize("hasAuthority('inventario:reservar')")
    public ReservasPedidoDTO liberar(@PathVariable Long id) {
        return reservaService.liberar(id);
    }

    @Operation(summary = "Reservar para una línea",
            description = "Deja comprometida la cantidad indicada (reemplaza la anterior); 0 la libera.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Reserva actualizada"),
            @ApiResponse(responseCode = "404", description = "El pedido o la línea no existen",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Supera lo solicitado o lo libre, o la línea no puede reservar",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/pedidos/{id}/reservas/{detalleId}")
    @PreAuthorize("hasAuthority('inventario:reservar')")
    public ReservasPedidoDTO reservar(@PathVariable Long id, @PathVariable Long detalleId,
                                      @Valid @RequestBody ReservaRequestDTO request,
                                      @Parameter(hidden = true) Principal principal) {
        return reservaService.reservar(id, detalleId, request.cantidad(), principal.getName());
    }

    @Operation(summary = "Reservas vigentes",
            description = "De la más reciente; el texto filtra por ítem (código o descripción) o número de pedido.")
    @GetMapping("/inventario/reservas")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public PagedModel<ReservaDTO> listar(@RequestParam(defaultValue = "") String texto,
                                         @RequestParam(defaultValue = "0") int pagina,
                                         @RequestParam(defaultValue = "25") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(reservaService.listar(texto, pageable));
    }
}
