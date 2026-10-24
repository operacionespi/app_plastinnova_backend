package co.com.plast_innova.app_plastinnova_backend.modules.material.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Item;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface ItemRepository extends JpaRepository<Item, Integer> {

    /** Ítems cuyo código o descripción contienen el texto (la intercalación de SQL Server ignora mayúsculas). */
    @Query("""
            SELECT i FROM Item i
            WHERE i.codigo LIKE CONCAT('%', :texto, '%') OR i.descripcion LIKE CONCAT('%', :texto, '%')
            ORDER BY i.codigo""")
    Page<Item> buscar(@Param("texto") String texto, Pageable pageable);
}
