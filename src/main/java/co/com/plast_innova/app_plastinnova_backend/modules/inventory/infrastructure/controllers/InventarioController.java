package co.com.plast_innova.app_plastinnova_backend.modules.inventory.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.BodegaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.CargaExistenciasDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.ExistenciaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.MovimientoRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto.SlotRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.CargaExistenciasService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.InventarioService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services.SlotService;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
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
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedModel;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1/inventario")
@RequiredArgsConstructor
@Tag(name = "Inventario", description = "Existencias por bodega y slot, movimientos (entradas, salidas, traslados, ajustes) y slots.")
public class InventarioController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final InventarioService inventarioService;
    private final SlotService slotService;
    private final CargaExistenciasService cargaService;

    @Operation(summary = "Bodegas", description = "Bodegas del ERP, ordenadas por código.")
    @GetMapping("/bodegas")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public List<BodegaDTO> bodegas() {
        return slotService.bodegas();
    }

    @Operation(summary = "Slots", description = "Slots de una bodega (o de todas), activos e inactivos, ordenados por código.")
    @GetMapping("/slots")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public List<SlotDTO> slots(@RequestParam(required = false) Integer bodegaId) {
        return slotService.listar(bodegaId);
    }

    @Operation(summary = "Crear slot", description = "El código se guarda en mayúsculas y debe ser único en su bodega.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Slot creado"),
            @ApiResponse(responseCode = "409", description = "La bodega ya tiene un slot con ese código",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/slots")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventario:gestionar')")
    public SlotDTO crearSlot(@Valid @RequestBody SlotRequestDTO request) {
        return slotService.crear(request);
    }

    @Operation(summary = "Modificar slot", description = "Un slot con existencias no se puede desactivar ni cambiar de bodega.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Slot modificado"),
            @ApiResponse(responseCode = "404", description = "El slot o la bodega no existen",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Código repetido o el slot tiene existencias",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PutMapping("/slots/{id}")
    @PreAuthorize("hasAuthority('inventario:gestionar')")
    public SlotDTO actualizarSlot(@PathVariable Integer id, @Valid @RequestBody SlotRequestDTO request) {
        return slotService.actualizar(id, request);
    }

    @Operation(summary = "Existencias", description = "Saldos mayores que cero por ítem y slot, ordenados por ítem, bodega y slot.")
    @GetMapping("/existencias")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public PagedModel<ExistenciaDTO> existencias(@RequestParam(defaultValue = "") String texto,
                                                 @RequestParam(required = false) Integer bodegaId,
                                                 @RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "25") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA),
                Sort.by("item.codigo", "slot.bodega.codigo", "slot.codigo"));
        return new PagedModel<>(inventarioService.existencias(texto, bodegaId, pageable));
    }

    @Operation(summary = "Movimientos", description = "Bitácora del más reciente al más antiguo; la bodega filtra por origen o destino.")
    @GetMapping("/movimientos")
    @PreAuthorize("hasAuthority('inventario:ver')")
    public PagedModel<MovimientoDTO> movimientos(@RequestParam(defaultValue = "") String itemCodigo,
                                                 @RequestParam(required = false) Integer bodegaId,
                                                 @RequestParam(required = false) TipoMovimientoInventario tipo,
                                                 @RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "25") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(inventarioService.movimientos(itemCodigo, bodegaId, tipo, pageable));
    }

    @Operation(summary = "Registrar movimiento",
            description = "ENTRADA usa slotDestinoId; SALIDA slotOrigenId; TRASLADO ambos; AJUSTE el slot contado en "
                    + "slotDestinoId y la cantidad contada (se registra la diferencia con el saldo).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Movimiento registrado"),
            @ApiResponse(responseCode = "400", description = "Datos incompletos o inválidos para el tipo",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "El ítem o un slot no existen",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "409", description = "Saldo insuficiente, slot inactivo o ajuste sin diferencia",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/movimientos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('inventario:mover')")
    public MovimientoDTO registrar(@Valid @RequestBody MovimientoRequestDTO request,
                                   @Parameter(hidden = true) Principal principal) {
        return inventarioService.registrar(request, principal.getName());
    }

    @Operation(summary = "Carga inicial de existencias",
            description = "CSV con columnas Bodega, Slot, Item, Cantidad. Cada fila deja ese saldo (como un conteo); los "
                    + "slots que no existen se crean. Si alguna fila tiene errores no se carga nada.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Existencias cargadas"),
            @ApiResponse(responseCode = "400", description = "El archivo tiene errores; no se cargó nada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/cargas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('inventario:gestionar')")
    public CargaExistenciasDTO cargar(@RequestPart MultipartFile existencias,
                                      @Parameter(hidden = true) Principal principal) throws IOException {
        return cargaService.cargar(existencias.getBytes(), principal.getName());
    }
}
