package co.com.plast_innova.app_plastinnova_backend.modules.delivery.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.Remision;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RemisionRepository extends JpaRepository<Remision, Long> {

    @Query(value = "SELECT NEXT VALUE FOR Seq_Remision", nativeQuery = true)
    long siguienteNumero();

    @EntityGraph(attributePaths = {"pedido", "pedido.cliente"})
    List<Remision> findByPedidoIdOrderByFechaDescIdDesc(Long pedidoId);

    /** De la más reciente; el texto filtra por número de remisión o de pedido, o por cliente. */
    @EntityGraph(attributePaths = {"pedido", "pedido.cliente"})
    @Query(value = """
            SELECT r FROM Remision r
            WHERE :texto = '' OR r.numero LIKE CONCAT('%', :texto, '%')
               OR r.pedido.numeroPedido LIKE CONCAT('%', :texto, '%')
               OR r.pedido.cliente.razonSocial LIKE CONCAT('%', :texto, '%')
            ORDER BY r.fecha DESC, r.id DESC""",
            countQuery = """
            SELECT COUNT(r) FROM Remision r
            WHERE :texto = '' OR r.numero LIKE CONCAT('%', :texto, '%')
               OR r.pedido.numeroPedido LIKE CONCAT('%', :texto, '%')
               OR r.pedido.cliente.razonSocial LIKE CONCAT('%', :texto, '%')""")
    Page<Remision> buscar(@Param("texto") String texto, Pageable pageable);
}
