package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.RutaProduccion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RutaProduccionRepository extends JpaRepository<RutaProduccion, Integer> {

    List<RutaProduccion> findAllByOrderByActivaDescNombreAsc();

    boolean existsByNombre(String nombre);

    boolean existsByNombreAndIdNot(String nombre, Integer id);
}
