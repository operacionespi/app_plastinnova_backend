package co.com.plast_innova.app_plastinnova_backend.modules.catalog.infrastructure.controllers;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.application.services.GestionEstadosPedidoService;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/catalogos/estados-pedido")
@Tag(name = "Catálogo: estados de pedido", description = "Estados del pedido y transiciones permitidas entre ellos.")
public class EstadosPedidoController extends EstadosControllerBase {

    public EstadosPedidoController(GestionEstadosPedidoService service) {
        super(service);
    }
}
