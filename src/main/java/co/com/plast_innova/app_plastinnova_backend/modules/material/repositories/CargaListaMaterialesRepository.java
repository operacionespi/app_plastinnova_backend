package co.com.plast_innova.app_plastinnova_backend.modules.material.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.CargaListaMateriales;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface CargaListaMaterialesRepository extends JpaRepository<CargaListaMateriales, Long> {

    Optional<CargaListaMateriales> findFirstByOrderByFechaDesc();

    List<CargaListaMateriales> findTop10ByOrderByFechaDesc();
}
