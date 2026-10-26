package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.EstadoOrdenProduccion;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccion;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface OrdenProduccionRepository extends JpaRepository<OrdenProduccion, Long> {

    @Query(value = "SELECT NEXT VALUE FOR Seq_Orden_Produccion", nativeQuery = true)
    long siguienteNumero();

    @EntityGraph(attributePaths = {"item", "bodegaDestino", "etapa"})
    Optional<OrdenProduccion> findWithItemById(Long id);

    /** De la más reciente; el texto filtra por número, ítem (código o descripción) o número de pedido atendido. */
    @EntityGraph(attributePaths = {"item", "bodegaDestino", "etapa"})
    @Query(value = """
            SELECT o FROM OrdenProduccion o
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:etapaId IS NULL OR o.etapa.id = :etapaId)
              AND (:texto = '' OR o.numero LIKE CONCAT('%', :texto, '%')
                   OR o.item.codigo LIKE CONCAT('%', :texto, '%') OR o.item.descripcion LIKE CONCAT('%', :texto, '%')
                   OR EXISTS (SELECT 1 FROM OrdenProduccionLinea l
                              WHERE l.orden = o AND l.pedidoDetalle.pedido.numeroPedido LIKE CONCAT('%', :texto, '%')))
            ORDER BY o.fechaCreacion DESC, o.id DESC""",
            countQuery = """
            SELECT COUNT(o) FROM OrdenProduccion o
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:etapaId IS NULL OR o.etapa.id = :etapaId)
              AND (:texto = '' OR o.numero LIKE CONCAT('%', :texto, '%')
                   OR o.item.codigo LIKE CONCAT('%', :texto, '%') OR o.item.descripcion LIKE CONCAT('%', :texto, '%')
                   OR EXISTS (SELECT 1 FROM OrdenProduccionLinea l
                              WHERE l.orden = o AND l.pedidoDetalle.pedido.numeroPedido LIKE CONCAT('%', :texto, '%')))""")
    Page<OrdenProduccion> buscar(@Param("texto") String texto, @Param("estado") EstadoOrdenProduccion estado,
                                 @Param("etapaId") Integer etapaId, Pageable pageable);
}
