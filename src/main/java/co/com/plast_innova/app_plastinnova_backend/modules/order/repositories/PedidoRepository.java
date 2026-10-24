package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.Pedido;
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
import java.util.List;
import java.util.Optional;

@Repository
public interface PedidoRepository extends JpaRepository<Pedido, Long>, JpaSpecificationExecutor<Pedido>,
        PedidoConteoRepository {

    // 1. Evitamos N+1 al cargar un pedido con su cliente, estado y ubicación de forma ansiosa en una sola query
    @EntityGraph(attributePaths = {"cliente", "estadoActual", "ciudadEnvio", "ciudadEnvio.pais"})
    Optional<Pedido> findById(Long id);

    // 2. Consulta optimizada para buscar por ID de FileMaker (Soft Delete aplicado por defecto en lógica)
    @EntityGraph(attributePaths = {"cliente", "estadoActual"})
    Optional<Pedido> findByFilemakerIdAndEliminadoEnFmFalse(String filemakerId);

    // 3. Paginación de pedidos activos usando el índice parcial DDL: IX_Pedido_Activos
    @EntityGraph(attributePaths = {"estadoActual", "cliente"})
    Page<Pedido> findByEliminadoEnFmFalse(Pageable pageable);

    // 3b. Listado filtrado: cliente, estado y ubicación en el mismo SELECT (sin N+1 al armar cada fila)
    @Override
    @EntityGraph(attributePaths = {"cliente", "estadoActual", "ciudadEnvio", "ciudadEnvio.pais"})
    Page<Pedido> findAll(Specification<Pedido> spec, Pageable pageable);

    // 4. Query directa para obtener el ID real de un estado basado en un pedido sin cargar relaciones pesadas
    @Query("SELECT p.estadoActual.id FROM Pedido p WHERE p.id = :pedidoId")
    Optional<Integer> findEstadoIdByPedidoId(@Param("pedidoId") Long pedidoId);

    // 5. Sincronización: busca también los pedidos marcados como eliminados (pueden reaparecer en FileMaker)
    Optional<Pedido> findByFilemakerId(String filemakerId);

    // 6. Sincronización: un pedido sin líneas activas desapareció de FileMaker
    @Modifying
    @Query("""
        UPDATE Pedido p SET p.eliminadoEnFm = true, p.fechaActualizacion = :ahora
        WHERE p.eliminadoEnFm = false
          AND NOT EXISTS (SELECT 1 FROM PedidoDetalle d WHERE d.pedido = p AND d.eliminadoEnFm = false)
    """)
    int marcarEliminadosSinLineasActivas(@Param("ahora") LocalDateTime ahora);

    // 7. Sincronización: pedidos que la app sigue controlando (no eliminados en FileMaker y sin estado final)
    @Query("""
        SELECT p.filemakerId FROM Pedido p
        WHERE p.filemakerId IS NOT NULL AND p.eliminadoEnFm = false AND p.estadoActual.esFinal = false
    """)
    List<String> findFilemakerIdsAbiertos();

    // 8. Opciones del filtro por vendedor: los de clientes con pedidos activos
    @Query("""
        SELECT DISTINCT c.vendedor FROM Pedido p JOIN p.cliente c
        WHERE p.eliminadoEnFm = false AND c.vendedor IS NOT NULL
        ORDER BY c.vendedor
    """)
    List<String> findVendedores();
}
