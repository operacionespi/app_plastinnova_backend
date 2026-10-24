package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Tuple;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Path;
import jakarta.persistence.criteria.Root;
import lombok.RequiredArgsConstructor;
import org.springframework.data.jpa.domain.Specification;

import java.util.Map;
import java.util.stream.Collectors;

/** Implementación del fragmento: Spring Data la une a PedidoRepository por el sufijo Impl. */
@RequiredArgsConstructor
class PedidoConteoRepositoryImpl implements PedidoConteoRepository {

    private final EntityManager entityManager;

    @Override
    public Map<Integer, Long> contarPorEstado(Specification<Pedido> filtro) {
        CriteriaBuilder cb = entityManager.getCriteriaBuilder();
        CriteriaQuery<Tuple> query = cb.createTupleQuery();
        Root<Pedido> root = query.from(Pedido.class);
        Path<Integer> estadoId = root.get("estadoActual").get("id");

        // Una sola consulta agrupada: SELECT estado_actual_id, COUNT(*) ... WHERE <filtro> GROUP BY estado_actual_id
        query.multiselect(estadoId, cb.count(root))
                .where(filtro.toPredicate(root, query, cb))
                .groupBy(estadoId);

        return entityManager.createQuery(query).getResultStream()
                .collect(Collectors.toMap(fila -> fila.get(0, Integer.class), fila -> fila.get(1, Long.class)));
    }
}
