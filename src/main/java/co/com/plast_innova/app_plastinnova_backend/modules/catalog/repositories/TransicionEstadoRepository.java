package co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.TransicionEstado;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

import java.util.List;

/**
 * Consultas comunes a las transiciones de pedido y de línea.
 */
@NoRepositoryBean
public interface TransicionEstadoRepository<T extends TransicionEstado<?>> extends JpaRepository<T, Integer> {

    @EntityGraph(attributePaths = {"estadoOrigen", "estadoDestino"})
    List<T> findAllByOrderByEstadoOrigenOrdenLogicoAscEstadoDestinoOrdenLogicoAsc();

    @EntityGraph(attributePaths = {"estadoDestino"})
    List<T> findByEstadoOrigenIdOrderByEstadoDestinoOrdenLogicoAsc(Integer estadoOrigenId);

    boolean existsByEstadoOrigenIdAndEstadoDestinoId(Integer estadoOrigenId, Integer estadoDestinoId);

    List<T> findByEstadoOrigenIdOrEstadoDestinoId(Integer estadoOrigenId, Integer estadoDestinoId);
}
