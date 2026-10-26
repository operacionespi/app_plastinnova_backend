package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EntregaProceso;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EntregaProcesoRepository extends JpaRepository<EntregaProceso, Long> {

    @EntityGraph(attributePaths = {"producto", "producto.item", "etapaOrigen", "etapaDestino"})
    List<EntregaProceso> findByProductoOrdenIdOrderByFechaDescIdDesc(Long ordenId);

    boolean existsByProductoId(Long productoId);
}
