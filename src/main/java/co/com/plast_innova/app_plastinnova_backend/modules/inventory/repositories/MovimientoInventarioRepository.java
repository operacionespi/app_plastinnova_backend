package co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.MovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface MovimientoInventarioRepository extends JpaRepository<MovimientoInventario, Long> {

    /** Del más reciente al más antiguo; la bodega filtra por origen o destino. */
    @EntityGraph(attributePaths = {"item", "slotOrigen", "slotOrigen.bodega", "slotDestino", "slotDestino.bodega"})
    @Query("""
            SELECT m FROM MovimientoInventario m LEFT JOIN m.slotOrigen o LEFT JOIN m.slotDestino d
            WHERE (:itemCodigo = '' OR m.item.codigo = :itemCodigo)
              AND (:bodegaId IS NULL OR o.bodega.id = :bodegaId OR d.bodega.id = :bodegaId)
              AND (:tipo IS NULL OR m.tipo = :tipo)
            ORDER BY m.fecha DESC, m.id DESC""")
    Page<MovimientoInventario> buscar(@Param("itemCodigo") String itemCodigo, @Param("bodegaId") Integer bodegaId,
                                      @Param("tipo") TipoMovimientoInventario tipo, Pageable pageable);

    boolean existsBySlotOrigenIdOrSlotDestinoId(Integer slotOrigenId, Integer slotDestinoId);
}
