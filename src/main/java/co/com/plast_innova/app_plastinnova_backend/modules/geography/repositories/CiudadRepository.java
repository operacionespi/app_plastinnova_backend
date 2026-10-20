package co.com.plast_innova.app_plastinnova_backend.modules.geography.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.geography.domain.Ciudad;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface CiudadRepository extends JpaRepository<Ciudad, Integer> {

    Optional<Ciudad> findByPaisIdAndNombre(Integer paisId, String nombre);
}
