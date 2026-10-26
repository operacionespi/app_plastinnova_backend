package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.HistorialOrdenProduccion;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface HistorialOrdenProduccionRepository extends JpaRepository<HistorialOrdenProduccion, Long> {

    @EntityGraph(attributePaths = {"etapa", "etapaAnterior"})
    List<HistorialOrdenProduccion> findByOrdenIdOrderByFechaDescIdDesc(Long ordenId);
}
