package co.com.plast_innova.app_plastinnova_backend.modules.inventory.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Existencia;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface ExistenciaRepository extends JpaRepository<Existencia, Long> {

    /** Bloquea el saldo hasta el fin de la transacción: dos movimientos simultáneos del mismo slot se hacen en fila. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT e FROM Existencia e WHERE e.item.id = :itemId AND e.slot.id = :slotId")
    Optional<Existencia> bloquear(@Param("itemId") Integer itemId, @Param("slotId") Integer slotId);

    /** Saldos mayores que cero, filtrados por texto del ítem (código o descripción) y bodega. */
    @Query(value = """
            SELECT e FROM Existencia e JOIN FETCH e.item i JOIN FETCH e.slot s JOIN FETCH s.bodega b
            WHERE e.cantidad > 0
              AND (:texto = '' OR i.codigo LIKE CONCAT('%', :texto, '%') OR i.descripcion LIKE CONCAT('%', :texto, '%'))
              AND (:bodegaId IS NULL OR b.id = :bodegaId)""",
            countQuery = """
            SELECT COUNT(e) FROM Existencia e JOIN e.item i JOIN e.slot s
            WHERE e.cantidad > 0
              AND (:texto = '' OR i.codigo LIKE CONCAT('%', :texto, '%') OR i.descripcion LIKE CONCAT('%', :texto, '%'))
              AND (:bodegaId IS NULL OR s.bodega.id = :bodegaId)""")
    Page<Existencia> buscar(@Param("texto") String texto, @Param("bodegaId") Integer bodegaId, Pageable pageable);

    boolean existsBySlotIdAndCantidadGreaterThan(Integer slotId, BigDecimal cantidad);

    /** Existencia total del ítem en la bodega (todos sus slots). */
    @Query("SELECT COALESCE(SUM(e.cantidad), 0) FROM Existencia e WHERE e.item.id = :itemId AND e.slot.bodega.id = :bodegaId")
    BigDecimal totalEnBodega(@Param("itemId") Integer itemId, @Param("bodegaId") Integer bodegaId);

    /** Saldos del ítem en los slots de la bodega, bloqueados, del mayor al menor: de ellos sale un despacho. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT e FROM Existencia e JOIN FETCH e.slot s JOIN FETCH s.bodega
            WHERE e.item.id = :itemId AND s.bodega.id = :bodegaId AND e.cantidad > 0
            ORDER BY e.cantidad DESC, s.codigo""")
    List<Existencia> bloquearEnBodega(@Param("itemId") Integer itemId, @Param("bodegaId") Integer bodegaId);

    /** Total de cada ítem por bodega (sumando sus slots), para el requerimiento neto. */
    @Query("""
            SELECT e.item.id AS itemId, s.bodega.codigo AS bodega, SUM(e.cantidad) AS cantidad
            FROM Existencia e JOIN e.slot s
            WHERE e.item.id IN :itemIds AND e.cantidad > 0
            GROUP BY e.item.id, s.bodega.codigo""")
    List<ExistenciaPorBodega> totalizarPorBodega(@Param("itemIds") Collection<Integer> itemIds);

    interface ExistenciaPorBodega {
        Integer getItemId();

        String getBodega();

        BigDecimal getCantidad();
    }
}
