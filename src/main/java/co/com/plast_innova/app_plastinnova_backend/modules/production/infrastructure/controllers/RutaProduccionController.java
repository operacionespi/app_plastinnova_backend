package co.com.plast_innova.app_plastinnova_backend.modules.production.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.AsignacionItemsRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ItemRutaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.ProcesosDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.RutaProduccionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto.RutaProduccionRequestDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.production.application.services.RutaProduccionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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

import java.util.List;

@RestController
@RequestMapping("/api/v1/rutas-produccion")
@RequiredArgsConstructor
@Tag(name = "Rutas de producción",
        description = "Procesos que recorre cada ítem al producirse. Los ítems sin ruta recorren todos los procesos.")
public class RutaProduccionController {

    private final RutaProduccionService rutaService;

    @Operation(summary = "Rutas", description = "Las activas primero, por nombre.")
    @GetMapping
    @PreAuthorize("hasAuthority('produccion:ver')")
    public List<RutaProduccionDTO> listar() {
        return rutaService.listar();
    }

    @Operation(summary = "Procesos disponibles",
            description = "Etapas que pueden formar una ruta (también la ruta de los ítems sin ruta) y la de almacén.")
    @GetMapping("/procesos")
    @PreAuthorize("hasAuthority('produccion:ver')")
    public ProcesosDTO procesos() {
        return rutaService.procesosDisponibles();
    }

    @Operation(summary = "Crear una ruta")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public RutaProduccionDTO crear(@Valid @RequestBody RutaProduccionRequestDTO request) {
        return rutaService.crear(request);
    }

    @Operation(summary = "Editar una ruta", description = "No cambia los productos de órdenes ya creadas.")
    @PutMapping("/{id}")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public RutaProduccionDTO actualizar(@PathVariable Integer id, @Valid @RequestBody RutaProduccionRequestDTO request) {
        return rutaService.actualizar(id, request);
    }

    @Operation(summary = "Eliminar una ruta", description = "Solo si ninguna orden la usó; si no, se desactiva.")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public void eliminar(@PathVariable Integer id) {
        rutaService.eliminar(id);
    }

    @Operation(summary = "Ítems que siguen la ruta")
    @GetMapping("/{id}/items")
    @PreAuthorize("hasAuthority('produccion:ver')")
    public List<ItemRutaDTO> items(@PathVariable Integer id) {
        return rutaService.items(id);
    }

    @Operation(summary = "Asignar ítems a la ruta", description = "Si un ítem tenía otra ruta, la cambia.")
    @PostMapping("/{id}/items")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public List<ItemRutaDTO> asignar(@PathVariable Integer id, @Valid @RequestBody AsignacionItemsRequestDTO request) {
        return rutaService.asignar(id, request);
    }

    @Operation(summary = "Quitar un ítem de la ruta", description = "Pasa a recorrer todos los procesos.")
    @DeleteMapping("/{id}/items")
    @PreAuthorize("hasAuthority('produccion:gestionar')")
    public List<ItemRutaDTO> quitar(@PathVariable Integer id, @RequestParam String codigo) {
        return rutaService.quitar(id, codigo);
    }
}
