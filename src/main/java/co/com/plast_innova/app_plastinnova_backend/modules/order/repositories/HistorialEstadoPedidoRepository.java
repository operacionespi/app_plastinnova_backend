package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.HistorialEstadoPedido;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface HistorialEstadoPedidoRepository extends JpaRepository<HistorialEstadoPedido, Long> {

    // Línea de tiempo (más reciente primero) usando el índice IX_*_Fecha; el id desempata cambios en el mismo instante
    @EntityGraph(attributePaths = {"estadoAnterior", "estado", "usuario"})
    List<HistorialEstadoPedido> findByPedidoIdOrderByFechaCambioDescIdDesc(Long pedidoId);

    /** Última vez que el pedido entró a un estado (ej. a Anulado, para reabrirlo en el estado que tenía). */
    Optional<HistorialEstadoPedido> findFirstByPedidoIdAndEstadoIdOrderByFechaCambioDescIdDesc(Long pedidoId, Integer estadoId);
}