package co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.ReservaInventario;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ReservaInventarioRepository extends JpaRepository<ReservaInventario, Long> {

    Optional<ReservaInventario> findByPedidoDetalleId(Long pedidoDetalleId);

    List<ReservaInventario> findByPedidoDetallePedidoId(Long pedidoId);

    /** Lo reservado de un ítem en una bodega por todas las líneas de pedido, salvo la excluida (null = ninguna). */
    @Query("""
            SELECT COALESCE(SUM(r.cantidad), 0) FROM ReservaInventario r
            WHERE r.item.id = :itemId AND r.bodega.id = :bodegaId
              AND (:excluida IS NULL OR r.pedidoDetalle.id <> :excluida)""")
    BigDecimal totalReservado(@Param("itemId") Integer itemId, @Param("bodegaId") Integer bodegaId,
                              @Param("excluida") Long excluida);

    /** Total reservado de cada ítem por bodega, para el requerimiento neto. */
    @Query("""
            SELECT r.item.id AS itemId, r.bodega.codigo AS bodega, SUM(r.cantidad) AS cantidad
            FROM ReservaInventario r
            WHERE r.item.id IN :itemIds
            GROUP BY r.item.id, r.bodega.codigo""")
    List<ExistenciaRepository.ExistenciaPorBodega> totalizarPorBodega(@Param("itemIds") Collection<Integer> itemIds);

    /** Reservas filtradas por texto del ítem (código o descripción) o número de pedido, de la más reciente. */
    @EntityGraph(attributePaths = {"item", "bodega", "pedidoDetalle", "pedidoDetalle.pedido",
            "pedidoDetalle.estadoActual"})
    @Query("""
            SELECT r FROM ReservaInventario r JOIN r.item i JOIN r.pedidoDetalle d JOIN d.pedido p
            WHERE :texto = '' OR i.codigo LIKE CONCAT('%', :texto, '%') OR i.descripcion LIKE CONCAT('%', :texto, '%')
               OR p.numeroPedido LIKE CONCAT('%', :texto, '%')
            ORDER BY r.fecha DESC, r.id DESC""")
    Page<ReservaInventario> buscar(@Param("texto") String texto, Pageable pageable);

    /** Sincronización: las líneas que desaparecieron de FileMaker dejan de reservar. */
    @Modifying
    @Query("DELETE FROM ReservaInventario r WHERE r.pedidoDetalle.id IN "
            + "(SELECT d.id FROM PedidoDetalle d WHERE d.eliminadoEnFm = true)")
    int liberarLineasEliminadas();
}
