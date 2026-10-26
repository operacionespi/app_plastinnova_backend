package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccionLinea;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
public interface OrdenProduccionLineaRepository extends JpaRepository<OrdenProduccionLinea, Long> {

    /** La orden que la línea sigue ahora (a lo sumo una: índice UX_OPL_LineaActiva). */
    @EntityGraph(attributePaths = "orden")
    Optional<OrdenProduccionLinea> findByPedidoDetalleIdAndActivaTrue(Long detalleId);

    /** La última orden que atendió la línea, activa o no. */
    @EntityGraph(attributePaths = "orden")
    Optional<OrdenProduccionLinea> findFirstByPedidoDetalleIdOrderByIdDesc(Long detalleId);

    /** Número de la orden que sigue cada línea, para una página de la cola de trabajo o de un pedido. */
    @Query("""
            SELECT l.pedidoDetalle.id AS detalleId, l.orden.id AS ordenId, l.orden.numero AS numero
            FROM OrdenProduccionLinea l WHERE l.activa = true AND l.pedidoDetalle.id IN :detalleIds""")
    List<OrdenDeLinea> activasDe(@Param("detalleIds") Collection<Long> detalleIds);

    @EntityGraph(attributePaths = {"orden", "orden.item"})
    List<OrdenProduccionLinea> findByPedidoDetallePedidoIdOrderByIdDesc(Long pedidoId);

    interface OrdenDeLinea {
        Long getDetalleId();

        Long getOrdenId();

        String getNumero();
    }
}
