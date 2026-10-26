package co.com.plast_innova.app_plastinnova_backend.modules.material.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ItemRepository extends JpaRepository<Item, Integer> {

    Optional<Item> findByCodigo(String codigo);

    /**
     * Bloquea el ítem hasta el fin de la transacción. Ordena las operaciones que dependen de lo libre del ítem en una
     * bodega (reservar, despachar, sacar de la bodega de producto terminado) para que no tomen dos veces lo mismo.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM Item i WHERE i.id = :id")
    Optional<Item> bloquear(@Param("id") Integer id);

    /** Ítems cuyo código o descripción contienen el texto (la intercalación de SQL Server ignora mayúsculas). */
    @Query("""
            SELECT i FROM Item i
            WHERE i.codigo LIKE CONCAT('%', :texto, '%') OR i.descripcion LIKE CONCAT('%', :texto, '%')
            ORDER BY i.codigo""")
    Page<Item> buscar(@Param("texto") String texto, Pageable pageable);
}
