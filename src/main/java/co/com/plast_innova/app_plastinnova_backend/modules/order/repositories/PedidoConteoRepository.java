package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;

/**
 * Consultas agregadas sobre los pedidos que aceptan los mismos filtros del listado.
 */
public interface PedidoConteoRepository {

    /** Pedidos que cumplen el filtro, por id del estado actual; los estados sin pedidos no aparecen. */
    Map<Integer, Long> contarPorEstado(Specification<Pedido> filtro);
}
