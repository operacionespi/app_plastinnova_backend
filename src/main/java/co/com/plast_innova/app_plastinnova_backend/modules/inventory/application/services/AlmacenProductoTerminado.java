package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories.SlotRepository;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import co.com.plast_innova.app_plastinnova_backend.modules.material.repositories.BodegaRepository;
import co.com.plast_innova.app_plastinnova_backend.shared.exception.BusinessRuleException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Bodega de producto terminado: de ella reservan los pedidos y salen los despachos. Lo producido para un pedido entra
 * a su slot de recepción cuando la línea llega a la etapa de almacén (app.inventario.*).
 */
@Component
public class AlmacenProductoTerminado {

    private final BodegaRepository bodegaRepository;
    private final SlotRepository slotRepository;
    private final String codigo;
    private final String estadoRecepcion;
    private final String slotRecepcion;

    public AlmacenProductoTerminado(BodegaRepository bodegaRepository, SlotRepository slotRepository,
                                    @Value("${app.inventario.bodega-producto-terminado}") String codigo,
                                    @Value("${app.inventario.estado-linea-recepcion}") String estadoRecepcion,
                                    @Value("${app.inventario.slot-recepcion}") String slotRecepcion) {
        this.bodegaRepository = bodegaRepository;
        this.slotRepository = slotRepository;
        this.codigo = codigo.strip();
        this.estadoRecepcion = estadoRecepcion.strip();
        this.slotRecepcion = slotRecepcion.strip().toUpperCase();
    }

    public String codigo() {
        return codigo;
    }

    /** La línea entra a la etapa en que lo producido llega a la bodega (Almacén 1). */
    public boolean esRecepcion(String codigoEstado) {
        return estadoRecepcion.equalsIgnoreCase(codigoEstado);
    }

    /** Las bodegas llegan con la carga de la lista de materiales. */
    public Bodega obtener() {
        return bodegaRepository.findByCodigo(codigo).orElseThrow(() -> new BusinessRuleException(
                "La bodega de producto terminado " + codigo + " no existe: cargue la lista de materiales."));
    }

    /** Slot donde entra lo producido; se crea la primera vez que se necesita. */
    Slot slotRecepcion(Bodega bodega) {
        return slotRepository.findByBodegaIdAndCodigo(bodega.getId(), slotRecepcion).orElseGet(() -> {
            Slot nuevo = new Slot();
            nuevo.setBodega(bodega);
            nuevo.setCodigo(slotRecepcion);
            nuevo.setDescripcion("Recepción de producción");
            return slotRepository.save(nuevo);
        });
    }
}
