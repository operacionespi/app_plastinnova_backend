package co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Rol;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

@Repository
public interface RolRepository extends JpaRepository<Rol, Integer> {

    List<Rol> findAllByOrderByIdAsc();

    List<Rol> findByCodigoIn(Collection<String> codigos);
}
