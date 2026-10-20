package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

@Repository
public interface PedidoDetalleRepository extends JpaRepository<PedidoDetalle, Long>, JpaSpecificationExecutor<PedidoDetalle> {

    // Cola de trabajo: cada línea con su pedido, cliente, producto y estado en el mismo SELECT (sin N+1)
    @Override
    @EntityGraph(attributePaths = {"pedido", "pedido.cliente", "producto", "estadoActual"})
    Page<PedidoDetalle> findAll(Specification<PedidoDetalle> spec, Pageable pageable);

    // Consulta optimizada para cargar la línea del pedido con su estado y producto sin N+1
    @EntityGraph(attributePaths = {"producto", "estadoActual"})
    List<PedidoDetalle> findByPedidoIdAndEliminadoEnFmFalse(Long pedidoId);

    /** Líneas vigentes del pedido que no están en el estado indicado (0 = todas están en él). */
    long countByPedidoIdAndEliminadoEnFmFalseAndEstadoActualIdNot(Long pedidoId, Integer estadoId);

    // Listado: totales de las líneas activas de una página de pedidos en una sola consulta
    @Query("""
        SELECT new co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.TotalesLineasPedido(
            d.pedido.id, COUNT(d), COALESCE(SUM(d.cantidadSolicitada), 0), COALESCE(SUM(d.cantidadProcesada), 0))
        FROM PedidoDetalle d
        WHERE d.pedido.id IN :pedidoIds AND d.eliminadoEnFm = false
        GROUP BY d.pedido.id
    """)
    List<TotalesLineasPedido> totalizarPorPedido(@Param("pedidoIds") Collection<Long> pedidoIds);

    // Sincronización: líneas de un pedido por su recordId de FileMaker (incluye eliminadas)
    List<PedidoDetalle> findByFilemakerIdIn(Collection<String> filemakerIds);

    // Sincronización: huellas de las líneas de FileMaker dentro del rango sincronizado, sin cargar entidades
    @Query("""
        SELECT new co.com.plast_innova.app_plastinnova_backend.modules.order.repositories.ResumenLineaFm(
            d.filemakerId, d.pedido.filemakerId, d.huellaFm, d.eliminadoEnFm)
        FROM PedidoDetalle d
        WHERE d.filemakerId IS NOT NULL AND d.fechaCreacionFm >= :desde
    """)
    List<ResumenLineaFm> findResumenesSincronizacion(@Param("desde") LocalDateTime desde);

    // Sincronización: líneas que ya no existen en FileMaker (máx. ~2000 ids por llamada: límite de parámetros de SQL Server)
    @Modifying
    @Query("""
        UPDATE PedidoDetalle d SET d.eliminadoEnFm = true, d.fechaActualizacion = :ahora
        WHERE d.filemakerId IN :filemakerIds AND d.eliminadoEnFm = false
    """)
    int marcarEliminadas(@Param("filemakerIds") Collection<String> filemakerIds, @Param("ahora") LocalDateTime ahora);
}
