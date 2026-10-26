package co.com.plast_innova.app_plastinnova_backend.modules.production.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.ItemRuta;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ItemRutaRepository extends JpaRepository<ItemRuta, Integer> {

    @EntityGraph(attributePaths = "item")
    List<ItemRuta> findByRutaIdOrderByItemCodigo(Integer rutaId);

    /** Ítems asignados por ruta, para el catálogo. */
    @Query("SELECT r.ruta.id AS rutaId, COUNT(r) AS items FROM ItemRuta r GROUP BY r.ruta.id")
    List<ItemsPorRuta> contarPorRuta();

    boolean existsByRutaId(Integer rutaId);

    interface ItemsPorRuta {
        Integer getRutaId();

        long getItems();
    }
}
