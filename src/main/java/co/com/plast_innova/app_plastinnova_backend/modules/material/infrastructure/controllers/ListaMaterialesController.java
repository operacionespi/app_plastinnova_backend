package co.com.plast_innova.app_plastinnova_backend.modules.material.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.CargaListaMaterialesDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.ItemResumenDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.MaterialesPedidoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto.NodoMaterialDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.CargaListaMaterialesService;
import co.com.plast_innova.app_plastinnova_backend.modules.material.application.services.ListaMaterialesService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.web.PagedModel;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.Principal;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Lista de materiales", description = "Estructura (BOM multinivel) de los productos, materiales que requiere un pedido y carga desde los CSV del ERP.")
public class ListaMaterialesController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ListaMaterialesService listaMaterialesService;
    private final CargaListaMaterialesService cargaService;

    @Operation(summary = "Materiales que requiere un pedido",
            description = "Estructura de cada línea vigente (sin las eliminadas en FileMaker ni las que no salen) para su "
                    + "cantidad solicitada y totales por componente. Solo calcula: no descuenta inventario.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Materiales del pedido"),
            @ApiResponse(responseCode = "404", description = "El pedido no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/pedidos/{id}/materiales")
    @PreAuthorize("hasAuthority('material:ver')")
    public MaterialesPedidoDTO materialesPedido(@PathVariable Long id) {
        return listaMaterialesService.materialesPedido(id);
    }

    @Operation(summary = "Buscar ítems", description = "Ítems cuyo código o descripción contienen el texto, ordenados por código.")
    @GetMapping("/materiales/items")
    @PreAuthorize("hasAuthority('material:ver')")
    public PagedModel<ItemResumenDTO> buscarItems(@RequestParam(defaultValue = "") String texto,
                                                  @RequestParam(defaultValue = "0") int pagina,
                                                  @RequestParam(defaultValue = "20") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(listaMaterialesService.buscarItems(texto, pageable));
    }

    @Operation(summary = "Estructura de un ítem",
            description = "Árbol completo de componentes para la cantidad indicada. El código va como parámetro porque "
                    + "puede contener '/'.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Estructura del ítem"),
            @ApiResponse(responseCode = "400", description = "Cantidad no válida",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class))),
            @ApiResponse(responseCode = "404", description = "El ítem no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/materiales/estructura")
    @PreAuthorize("hasAuthority('material:ver')")
    public NodoMaterialDTO estructura(@RequestParam String codigo,
                                      @RequestParam(defaultValue = "1") BigDecimal cantidad) {
        return listaMaterialesService.estructura(codigo, cantidad);
    }

    @Operation(summary = "Últimas cargas", description = "Las 10 cargas más recientes, de la más reciente a la más antigua.")
    @GetMapping("/materiales/cargas")
    @PreAuthorize("hasAuthority('material:ver')")
    public List<CargaListaMaterialesDTO> ultimasCargas() {
        return listaMaterialesService.ultimasCargas();
    }

    @Operation(summary = "Cargar bodegas, ítems y lista de materiales",
            description = "Recarga completa desde los CSV del ERP. Si algún archivo tiene errores no se carga nada y se "
                    + "responde 400 con el detalle. Los ítems y bodegas conservan su id; la lista se reemplaza entera.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Carga realizada, con sus avisos"),
            @ApiResponse(responseCode = "400", description = "Los archivos tienen errores; no se cargó nada",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping(value = "/materiales/cargas", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @PreAuthorize("hasAuthority('material:cargar')")
    public CargaListaMaterialesDTO cargar(@RequestPart MultipartFile bodegas,
                                          @RequestPart MultipartFile items,
                                          @RequestPart MultipartFile listaMateriales,
                                          @Parameter(hidden = true) Principal principal) throws IOException {
        return cargaService.cargar(bodegas.getBytes(), items.getBytes(), listaMateriales.getBytes(), principal.getName());
    }
}
