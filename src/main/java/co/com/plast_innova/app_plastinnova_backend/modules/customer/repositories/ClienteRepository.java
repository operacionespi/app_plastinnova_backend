package co.com.plast_innova.app_plastinnova_backend.modules.customer.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.customer.domain.Cliente;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface ClienteRepository extends JpaRepository<Cliente, Long> {

    Optional<Cliente> findByFilemakerId(String filemakerId);
}
