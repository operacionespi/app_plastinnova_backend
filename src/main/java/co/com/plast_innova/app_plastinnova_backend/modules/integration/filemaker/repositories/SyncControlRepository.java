package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain.SyncControl;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncControlRepository extends JpaRepository<SyncControl, String> {
}
