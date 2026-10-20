package co.com.plast_innova.app_plastinnova_backend.shared.repositories;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.AuditoriaLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AuditoriaLogRepository extends JpaRepository<AuditoriaLog, Long> {
}
