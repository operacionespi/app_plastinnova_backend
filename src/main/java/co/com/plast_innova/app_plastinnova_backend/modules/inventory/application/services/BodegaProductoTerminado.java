package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/** Bodega de la que reservan los pedidos y salen los despachos (app.inventario.bodega-producto-terminado). */
@Component
public class BodegaProductoTerminado {

    private final BodegaRepository bodegaRepository;
    private final String codigo;

    public BodegaProductoTerminado(BodegaRepository bodegaRepository,
                                   @Value("${app.inventario.bodega-producto-terminado}") String codigo) {
        this.bodegaRepository = bodegaRepository;
        this.codigo = codigo.strip();
    }

    public String codigo() {
        return codigo;
    }

    public boolean es(Bodega bodega) {
        return bodega != null && codigo.equalsIgnoreCase(bodega.getCodigo());
    }

    /** Las bodegas llegan con la carga de la lista de materiales. */
    public Bodega obtener() {
        return bodegaRepository.findByCodigo(codigo).orElseThrow(() -> new BusinessRuleException(
                "La bodega de producto terminado " + codigo + " no existe: cargue la lista de materiales."));
    }
}
