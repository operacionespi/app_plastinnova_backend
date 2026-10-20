package co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.Producto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface ProductoRepository extends JpaRepository<Producto, Integer> {

    List<Producto> findByFilemakerIdIn(Collection<String> filemakerIds);
}
