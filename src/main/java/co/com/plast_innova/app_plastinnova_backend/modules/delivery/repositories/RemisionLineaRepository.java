package co.com.plast_innova.app_plastinnova_backend.modules.delivery.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.RemisionLinea;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RemisionLineaRepository extends JpaRepository<RemisionLinea, Long> {

    @EntityGraph(attributePaths = {"pedidoDetalle", "pedidoDetalle.producto"})
    List<RemisionLinea> findByRemisionIdInOrderById(Collection<Long> remisionIds);
}
