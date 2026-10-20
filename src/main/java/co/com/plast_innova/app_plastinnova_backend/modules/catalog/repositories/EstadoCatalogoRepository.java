package co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoCatalogo;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;
import java.util.Optional;

/**
 * Consultas comunes a los catálogos de estados de pedido y de línea.
 */
@NoRepositoryBean
public interface EstadoCatalogoRepository<E extends EstadoCatalogo> extends JpaRepository<E, Integer> {

    List<E> findByActivoTrue();

    List<E> findAllByOrderByOrdenLogicoAscIdAsc();

    List<E> findByActivoTrueOrderByOrdenLogicoAscIdAsc();

    Optional<E> findByEsInicialTrueAndActivoTrue();

    boolean existsByCodigo(String codigo);

    Optional<E> findByCodigo(String codigo);

    boolean existsByCodigoAndIdNot(String codigo, Integer id);

    /** True si el estado aparece en algún registro operativo o en el historial (no se puede borrar físicamente). */
    boolean estaEnUso(Integer estadoId);
}
