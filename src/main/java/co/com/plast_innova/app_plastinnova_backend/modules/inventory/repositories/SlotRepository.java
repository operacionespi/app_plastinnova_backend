package co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SlotRepository extends JpaRepository<Slot, Integer> {

    @EntityGraph(attributePaths = "bodega")
    List<Slot> findByBodegaIdOrderByCodigoAsc(Integer bodegaId);

    @EntityGraph(attributePaths = "bodega")
    List<Slot> findAllByOrderByBodegaCodigoAscCodigoAsc();

    @EntityGraph(attributePaths = "bodega")
    Optional<Slot> findWithBodegaById(Integer id);

    boolean existsByBodegaIdAndCodigo(Integer bodegaId, String codigo);

    boolean existsByBodegaIdAndCodigoAndIdNot(Integer bodegaId, String codigo, Integer id);
}
