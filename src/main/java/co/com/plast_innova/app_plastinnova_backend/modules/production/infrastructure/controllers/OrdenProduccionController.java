package co.com.plast_innova.app_plastinnova_backend.modules.production.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.JustificacionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.BajaProcesoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.CantidadProductoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EdicionOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.EntregaProcesoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.LineaOrdenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.OrdenProduccionResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProductoOrdenRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.VinculoLineaRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.EntregaProcesoService;
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
        description = "Producción planificada de uno o más productos: entregas entre procesos, líneas de pedido que "
                + "atiende y consumo de materiales.")
public class OrdenProduccionController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final OrdenProduccionService ordenService;
    private final EntregaProcesoService entregaService;

    @Operation(summary = "Órdenes de producción",
            description = "De la más reciente. El texto filtra por número, ítem o número de pedido atendido; la etapa, "
                    + "por las órdenes con piezas en ese proceso.")
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
            description = "Con sus productos (piezas por proceso, líneas que atienden y materiales), sus entregas, su "
                    + "historial y sus movimientos.")
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

    @Operation(summary = "Líneas pendientes para crear órdenes",
            description = "Líneas de pedidos abiertos con piezas por producir (sin lo comprometido ni lo entregado) y "
                    + "sin orden. Las que no se pueden incluir traen el motivo. El texto filtra por pedido, cliente o "
                    + "producto.")
    @GetMapping("/lineas-pendientes")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public PagedModel<LineaOrdenDTO> lineasPendientes(@RequestParam(defaultValue = "") String texto,
                                                      @RequestParam(defaultValue = "0") int pagina,
                                                      @RequestParam(defaultValue = "25") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(ordenService.lineasPendientes(texto, pageable));
    }

    @Operation(summary = "Líneas que puede atender",
            description = "Líneas sin terminar de pedidos abiertos que piden alguno de sus productos en proceso, sin "
                    + "otra orden activa, con lo que les falta (sin lo comprometido ni lo entregado).")
    @GetMapping("/{id}/candidatas")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public List<LineaOrdenDTO> candidatas(@PathVariable Long id) {
        return ordenService.candidatas(id);
    }

    @Operation(summary = "Crear una orden",
            description = "Con uno o más productos, cada uno con las líneas de pedido que atiende. Cada producto sigue "
                    + "la ruta de su ítem y sus piezas empiezan en el primer proceso.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Orden creada"),
            @ApiResponse(responseCode = "404", description = "Un ítem, una bodega o una línea no existen",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO crear(@Valid @RequestBody OrdenProduccionRequestDTO request,
                                    @Parameter(hidden = true) Principal principal) {
        return ordenService.crear(request, principal.getName());
    }

    @Operation(summary = "Editar fecha requerida u observaciones")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO editar(@PathVariable Long id, @Valid @RequestBody EdicionOrdenRequestDTO request,
                                     @Parameter(hidden = true) Principal principal) {
        return ordenService.editar(id, request, principal.getName());
    }

    @Operation(summary = "Agregar un producto", description = "Con las líneas de pedido que atiende.")
    @PostMapping("/{id}/productos")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO agregarProducto(@PathVariable Long id, @Valid @RequestBody ProductoOrdenRequestDTO request,
                                              @Parameter(hidden = true) Principal principal) {
        return ordenService.agregarProducto(id, request, principal.getName());
    }

    @Operation(summary = "Cambiar la cantidad de un producto",
            description = "La diferencia entra o sale del primer proceso de su ruta.")
    @PutMapping("/{id}/productos/{productoId}")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO cambiarCantidad(@PathVariable Long id, @PathVariable Long productoId,
                                              @Valid @RequestBody CantidadProductoRequestDTO request,
                                              @Parameter(hidden = true) Principal principal) {
        return ordenService.cambiarCantidad(id, productoId, request, principal.getName());
    }

    @Operation(summary = "Quitar un producto", description = "Solo si no tiene entregas ni líneas de pedido.")
    @DeleteMapping("/{id}/productos/{productoId}")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO quitarProducto(@PathVariable Long id, @PathVariable Long productoId,
                                             @Parameter(hidden = true) Principal principal) {
        return ordenService.quitarProducto(id, productoId, principal.getName());
    }

    @Operation(summary = "Atender una línea de pedido",
            description = "La línea sigue al producto de su ítem: queda en la etapa donde están sus piezas.")
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

    @Operation(summary = "Entregar piezas a otro proceso",
            description = "Al proceso siguiente de la ruta, al almacén desde el último (se consumen los componentes, lo "
                    + "producido entra a la bodega y queda comprometido para las líneas) o, con el motivo, a uno "
                    + "anterior. Las líneas que atiende el producto siguen a sus piezas.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Entrega registrada"),
            @ApiResponse(responseCode = "409", description = "Proceso fuera de la ruta o salteado, más piezas de las que "
                    + "hay, materiales insuficientes o la orden está cerrada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/{id}/entregas")
    @PreAuthorize("hasAuthority('detalle:cambiar-estado')")
    public OrdenProduccionDTO entregar(@PathVariable Long id, @Valid @RequestBody EntregaProcesoRequestDTO request,
                                       @Parameter(hidden = true) Principal principal) {
        return entregaService.entregar(id, request, principal.getName());
    }

    @Operation(summary = "Dar de baja piezas",
            description = "Desecho: las piezas salen del proceso y de la orden; sus componentes se consumen.")
    @PostMapping("/{id}/bajas")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO darDeBaja(@PathVariable Long id, @Valid @RequestBody BajaProcesoRequestDTO request,
                                        @Parameter(hidden = true) Principal principal) {
        return entregaService.darDeBaja(id, request, principal.getName());
    }

    @Operation(summary = "Anular la orden",
            description = "Las líneas que atendía quedan en su etapa; lo que llegó al almacén sigue en el inventario.")
    @PostMapping("/{id}/anulacion")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public OrdenProduccionDTO anular(@PathVariable Long id, @Valid @RequestBody JustificacionRequestDTO request,
                                     @Parameter(hidden = true) Principal principal) {
        return ordenService.anular(id, request.justificacion(), principal.getName());
    }
}
