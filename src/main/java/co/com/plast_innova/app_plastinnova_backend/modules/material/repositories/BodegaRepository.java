package co.com.plast_innova.app_plastinnova_backend.modules.material.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface BodegaRepository extends JpaRepository<Bodega, Integer> {

    List<Bodega> findAllByOrderByCodigoAsc();

    Optional<Bodega> findByCodigo(String codigo);
}
