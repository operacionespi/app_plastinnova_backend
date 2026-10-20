package co.com.plast_innova.app_plastinnova_backend.modules.catalog.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosDetalleService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogos/estados-detalle")
@Tag(name = "Catálogo: estados de línea", description = "Estados de las líneas de producción y transiciones permitidas entre ellos.")
public class EstadosDetalleController extends EstadosControllerBase {

    public EstadosDetalleController(GestionEstadosDetalleService service) {
        super(service);
    }
}
