package co.com.plast_innova.app_plastinnova_backend.modules.auth.repositories;

import co.com.plast_innova.app_plastinnova_backend.modules.auth.domain.Usuario;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface UsuarioRepository extends JpaRepository<Usuario, Integer> {

    // JOIN FETCH anidado para cargar roles y permisos en una sola consulta al autenticar
    @Query("SELECT u FROM Usuario u LEFT JOIN FETCH u.roles r LEFT JOIN FETCH r.permisos WHERE u.email = :email AND u.activo = true")
    Optional<Usuario> findByEmailWithRolesAndPermissions(@Param("email") String email);

    Optional<Usuario> findByEmailAndActivoTrue(String email);

    // ------------------------------------------------------------------ gestión de usuarios (administrador)

    @Query("SELECT DISTINCT u FROM Usuario u LEFT JOIN FETCH u.roles ORDER BY u.nombreCompleto, u.id")
    List<Usuario> findAllConRoles();

    @Query("SELECT u FROM Usuario u LEFT JOIN FETCH u.roles WHERE u.id = :id")
    Optional<Usuario> findByIdConRoles(@Param("id") Integer id);

    boolean existsByEmailIgnoreCase(String email);

    /** Administradores activos distintos del indicado: siempre debe quedar al menos uno. */
    @Query("SELECT COUNT(DISTINCT u) FROM Usuario u JOIN u.roles r WHERE r.codigo = 'ADMIN' AND u.activo = true AND u.id <> :id")
    long contarOtrosAdministradoresActivos(@Param("id") Integer id);
}