package co.com.plast_innova.app_plastinnova_backend.support;

import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.TestPropertySource;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Base de las pruebas de integración: un SQL Server 2022 desechable (Testcontainers) con el esquema de Flyway.
 *
 * <p>El contenedor se inicia una sola vez y lo comparten todas las clases de prueba, aunque usen contextos de Spring
 * distintos; Testcontainers lo elimina al terminar. {@code @ServiceConnection} reemplaza el datasource del .env, así
 * que las pruebas nunca tocan la base de desarrollo. Requiere Docker o Podman (ver ~/.testcontainers.properties).
 *
 * <p>Las pruebas pueden borrar tablas completas: cada clase limpia los datos que crea.
 */
@TestPropertySource(properties = {
        // Valores fijos para no depender del .env
        "app.security.jwt.secret=cHJ1ZWJhcy1pbnRlZ3JhY2lvbi1wbGFzdGlubm92YS1zZWNyZXRvLWp3dC0yNTYtYml0cw==",
        "app.filemaker.api.base-url=http://localhost:1/fmi/data/v1/databases",
        "app.filemaker.api.username=it",
        "app.filemaker.api.password=it",
        "app.filemaker.sync.enabled=false",
        // El perfil dev registra cada consulta y sus parámetros
        "spring.jpa.show-sql=false",
        "logging.level.org.hibernate.SQL=INFO",
        "logging.level.org.hibernate.orm.jdbc.bind=INFO"
})
public abstract class PruebaIntegracion {

    @ServiceConnection
    protected static final MSSQLServerContainer SQL_SERVER =
            new MSSQLServerContainer(DockerImageName.parse("mcr.microsoft.com/mssql/server:2022-latest")).acceptLicense();

    static {
        SQL_SERVER.start();
    }
}
