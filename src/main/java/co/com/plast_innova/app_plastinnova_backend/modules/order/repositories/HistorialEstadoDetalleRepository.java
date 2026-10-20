package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistorialEstadoDetalleRepository extends JpaRepository<HistorialEstadoDetalle, Long> {

    // Línea de tiempo (más reciente primero) usando el índice IX_*_Fecha; el id desempata cambios en el mismo instante
    @EntityGraph(attributePaths = {"estadoAnterior", "estado", "usuario"})
    List<HistorialEstadoDetalle> findByPedidoDetalleIdOrderByFechaCambioDescIdDesc(Long pedidoDetalleId);

    /** Último movimiento de un tipo (ej. la marca de No sale, para saber a qué etapa se devuelve). */
    Optional<HistorialEstadoDetalle> findFirstByPedidoDetalleIdAndTipoOrderByFechaCambioDescIdDesc(
            Long pedidoDetalleId, TipoMovimientoDetalle tipo);
}