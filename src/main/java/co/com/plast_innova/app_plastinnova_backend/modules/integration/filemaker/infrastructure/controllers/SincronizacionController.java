package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto.SyncLogResponseDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.services.FileMakerSyncService;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.ModoSync;
import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.repositories.SyncLogRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.ResourceNotFoundException;
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
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.security.Principal;

@RestController
@RequestMapping("/api/v1/sincronizacion/filemaker")
@RequiredArgsConstructor
@PreAuthorize("hasAuthority('sync:ejecutar')")
@Tag(name = "Sincronización FileMaker", description = "Ejecución manual y bitácora de la sincronización de pedidos con FileMaker.")
public class SincronizacionController {

    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final FileMakerSyncService syncService;
    private final SyncLogRepository syncLogRepository;

    @Operation(summary = "Ejecutar la sincronización ahora",
            description = "Inicia la sincronización en segundo plano y retorna su bitácora (estado EN_PROCESO). "
                    + "Consulte GET /ejecuciones/{id} para ver el resultado. Modos: INCREMENTAL (por defecto; lo "
                    + "modificado desde la última ejecución, o COMPLETA si aún no hay ninguna), RECONCILIACION (relee "
                    + "los pedidos abiertos para detectar líneas borradas en FileMaker) y COMPLETA (todo desde la fecha "
                    + "de corte, tarda varios minutos).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "202", description = "Sincronización iniciada"),
            @ApiResponse(responseCode = "409", description = "Ya hay una sincronización en curso",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @PostMapping("/ejecuciones")
    public ResponseEntity<SyncLogResponseDTO> ejecutar(@RequestParam(defaultValue = "INCREMENTAL") ModoSync modo,
                                                       @Parameter(hidden = true) Principal principal) {
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(SyncLogResponseDTO.de(syncService.iniciarManual(principal.getName(), modo)));
    }

    @Operation(summary = "Consultar las ejecuciones", description = "Bitácora de ejecuciones, de la más reciente a la más antigua.")
    @GetMapping("/ejecuciones")
    public PagedModel<SyncLogResponseDTO> listar(@RequestParam(defaultValue = "0") int pagina,
                                                 @RequestParam(defaultValue = "20") int tamano) {
        PageRequest pageable = PageRequest.of(Math.max(pagina, 0), Math.clamp(tamano, 1, TAMANO_MAXIMO_PAGINA));
        return new PagedModel<>(syncLogRepository.findByEntidadOrderByFechaInicioDesc(FileMakerSyncService.ENTIDAD, pageable).map(SyncLogResponseDTO::de));
    }

    @Operation(summary = "Consultar una ejecución")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Ejecución encontrada"),
            @ApiResponse(responseCode = "404", description = "La ejecución no existe",
                    content = @Content(schema = @Schema(implementation = ProblemDetail.class)))
    })
    @GetMapping("/ejecuciones/{id}")
    public SyncLogResponseDTO obtener(@PathVariable Long id) {
        return syncLogRepository.findById(id)
                .map(SyncLogResponseDTO::de)
                .orElseThrow(() -> new ResourceNotFoundException("La ejecución de sincronización " + id + " no existe."));
    }
}
