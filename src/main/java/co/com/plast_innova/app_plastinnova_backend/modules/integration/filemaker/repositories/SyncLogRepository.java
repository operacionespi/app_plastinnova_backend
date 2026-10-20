package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.SyncLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncLogRepository extends JpaRepository<SyncLog, Long> {

    // Usa el índice IX_SyncLog_Entidad_Fecha
    Page<SyncLog> findByEntidadOrderByFechaInicioDesc(String entidad, Pageable pageable);
}
