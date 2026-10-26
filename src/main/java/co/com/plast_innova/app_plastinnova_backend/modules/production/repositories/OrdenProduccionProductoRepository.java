package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionProducto;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrdenProduccionProductoRepository extends JpaRepository<OrdenProduccionProducto, Long> {

    /** Las entregas de un mismo producto se registran en fila (antes de bloquear ítems del inventario). */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM OrdenProduccionProducto p WHERE p.id = :id")
    Optional<OrdenProduccionProducto> bloquear(@Param("id") Long id);
}
