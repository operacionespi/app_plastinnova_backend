package co.com.plast_innova.app_plastinnova_backend.modules.catalog.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain.EstadoDetalle;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface EstadoDetalleRepository extends EstadoCatalogoRepository<EstadoDetalle> {

    @Override
    @Query(value = """
        SELECT CAST(CASE WHEN EXISTS (SELECT 1 FROM Pedido_Detalle WHERE estado_actual_id = :id)
                           OR EXISTS (SELECT 1 FROM Historial_Estado_Detalle
                                      WHERE estado_id = :id OR estado_anterior_id = :id)
                           OR EXISTS (SELECT 1 FROM Orden_Produccion WHERE etapa_id = :id)
                           OR EXISTS (SELECT 1 FROM Historial_Orden_Produccion
                                      WHERE etapa_id = :id OR etapa_anterior_id = :id)
                    THEN 1 ELSE 0 END AS BIT)
    """, nativeQuery = true)
    boolean estaEnUso(@Param("id") Integer estadoId);
}
