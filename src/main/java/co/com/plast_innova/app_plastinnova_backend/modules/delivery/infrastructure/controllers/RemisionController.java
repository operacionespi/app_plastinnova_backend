package co.com.plast_innova.app_plastinnova_backend.modules.delivery.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.EntregasPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto.RemisionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.services.RemisionService;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Entregas", description = "Remisiones: entregas de producto terminado de los pedidos.")
public class RemisionController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final RemisionService remisionService;

    @Operation(summary = "Entregas del pedido",
            description = "Por línea: lo entregado, lo que falta y lo disponible en la bodega; y las remisiones del pedido.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entregas del pedido"),
            @ApiResponse(responseCode = "404", description = "El pedido no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/pedidos/{id}/entregas")
    @PreAuthorize("hasAuthority('entrega:ver')")
    public EntregasPedidoDTO entregasPedido(@PathVariable Long id) {
        return remisionService.entregasPedido(id);
    }

    @Operation(summary = "Emitir una remisión",
            description = "Sale de la bodega lo entregado (primero lo comprometido para cada línea). Las líneas que "
                    + "completan lo solicitado pasan a Entregado.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Remisión emitida"),
            @ApiResponse(responseCode = "409", description = "Pedido cerrado, línea entregada o en No sale, o no hay "
                    + "producto suficiente", content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/pedidos/{id}/remisiones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('entrega:gestionar')")
    public RemisionDTO emitir(@PathVariable Long id, @Valid @RequestBody RemisionRequestDTO request,
                              @Parameter(hidden = true) Principal principal) {
        return remisionService.emitir(id, request, principal.getName());
    }

    @Operation(summary = "Remisiones", description = "De la más reciente; el texto filtra por remisión, pedido o cliente.")
    @GetMapping("/remisiones")
    @PreAuthorize("hasAuthority('entrega:ver')")
    public PagedModel<RemisionDTO> listar(@RequestParam(defaultValue = "") String texto,
                                          @RequestParam(defaultValue = "0") int pagina,
                                          @RequestParam(defaultValue = "25") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(remisionService.listar(texto, pageable));
    }

    @Operation(summary = "Detalle de la remisión")
    @GetMapping("/remisiones/{id}")
    @PreAuthorize("hasAuthority('entrega:ver')")
    public RemisionDTO obtener(@PathVariable Long id) {
        return remisionService.obtener(id);
    }

    @Operation(summary = "Anular la remisión",
            description = "El producto vuelve a los slots de donde salió, queda comprometido otra vez para sus líneas y "
                    + "las que estaban en Entregado regresan a su etapa.")
    @PostMapping("/remisiones/{id}/anulacion")
    @PreAuthorize("hasAuthority('entrega:gestionar')")
    public RemisionDTO anular(@PathVariable Long id, @Valid @RequestBody JustificacionRequestDTO request,
                              @Parameter(hidden = true) Principal principal) {
        return remisionService.anular(id, request.justificacion(), principal.getName());
    }
}
