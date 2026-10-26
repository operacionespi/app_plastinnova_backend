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

    @EntityGraph(attributePaths = {"productos", "productos.item", "productos.bodegaDestino", "productos.ruta"})
    Optional<OrdenProduccion> findDetalleById(Long id);

    /**
     * De la más reciente. El texto filtra por número, ítem (código o descripción) de alguno de sus productos o número
     * de pedido atendido; la etapa, por las órdenes que tienen piezas en ese proceso.
     */
    @Query(value = """
            SELECT o FROM OrdenProduccion o
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:etapaId IS NULL OR EXISTS (SELECT 1 FROM OrdenProduccionEtapa e
                                               WHERE e.producto.orden = o AND e.etapa.id = :etapaId AND e.cantidad > 0))
              AND (:texto = '' OR o.numero LIKE CONCAT('%', :texto, '%')
                   OR EXISTS (SELECT 1 FROM OrdenProduccionProducto p WHERE p.orden = o
                              AND (p.item.codigo LIKE CONCAT('%', :texto, '%')
                                   OR p.item.descripcion LIKE CONCAT('%', :texto, '%')))
                   OR EXISTS (SELECT 1 FROM OrdenProduccionLinea l
                              WHERE l.orden = o AND l.pedidoDetalle.pedido.numeroPedido LIKE CONCAT('%', :texto, '%')))
            ORDER BY o.fechaCreacion DESC, o.id DESC""",
            countQuery = """
            SELECT COUNT(o) FROM OrdenProduccion o
            WHERE (:estado IS NULL OR o.estado = :estado)
              AND (:etapaId IS NULL OR EXISTS (SELECT 1 FROM OrdenProduccionEtapa e
                                               WHERE e.producto.orden = o AND e.etapa.id = :etapaId AND e.cantidad > 0))
              AND (:texto = '' OR o.numero LIKE CONCAT('%', :texto, '%')
                   OR EXISTS (SELECT 1 FROM OrdenProduccionProducto p WHERE p.orden = o
                              AND (p.item.codigo LIKE CONCAT('%', :texto, '%')
                                   OR p.item.descripcion LIKE CONCAT('%', :texto, '%')))
                   OR EXISTS (SELECT 1 FROM OrdenProduccionLinea l
                              WHERE l.orden = o AND l.pedidoDetalle.pedido.numeroPedido LIKE CONCAT('%', :texto, '%')))""")
    Page<OrdenProduccion> buscar(@Param("texto") String texto, @Param("estado") EstadoOrdenProduccion estado,
                                 @Param("etapaId") Integer etapaId, Pageable pageable);
}
