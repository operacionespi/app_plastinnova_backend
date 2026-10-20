package co.com.plast_innova.app_plastinnova_backend.modules.geography.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.geography.domain.Pais;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface PaisRepository extends JpaRepository<Pais, Integer> {

    // La intercalación de la BD ignora mayúsculas: "COLOMBIA" encuentra "Colombia"
    Optional<Pais> findByNombre(String nombre);
}
