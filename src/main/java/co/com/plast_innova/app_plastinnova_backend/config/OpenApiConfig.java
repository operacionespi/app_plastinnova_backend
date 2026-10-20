package co.com.plast_innova.app_plastinnova_backend.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Contact;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "API de Gestión y Trazabilidad de Pedidos",
                version = "1.0.0",
                description = "Backend empresarial modular para la orquestación de operaciones de planta, sincronización asíncrona con FileMaker y control de calidad interactivo.",
                contact = @Contact(name = "Arquitectura y Desarrollo Backend", email = "arquitectura@empresa.com")
        ),
        // Relativo: el cliente generado para el frontend llama al mismo origen desde donde se sirve
        servers = @Server(url = "/"),
        security = @SecurityRequirement(name = "Bearer Authentication")
)
@SecurityScheme(
        name = "Bearer Authentication",
        type = SecuritySchemeType.HTTP,
        bearerFormat = "JWT",
        scheme = "bearer",
        description = "Ingrese el token JWT obtenido en el endpoint /api/v1/auth/login. No incluya el prefijo 'Bearer '."
)
public class OpenApiConfig {

    /**
     * operationId = controlador + método, ej. "estadosPedidoListar". Sin esto springdoc numera los repetidos
     * (listar_1, listar_2) según el orden de carga, y el cliente generado del frontend cambiaría de nombres.
     */
    @Bean
    public OperationCustomizer operationIdPorControlador() {
        return (operation, handlerMethod) -> {
            String controlador = handlerMethod.getBeanType().getSimpleName().replaceFirst("Controller$", "");
            String metodo = handlerMethod.getMethod().getName();
            operation.setOperationId(Character.toLowerCase(controlador.charAt(0)) + controlador.substring(1)
                    + Character.toUpperCase(metodo.charAt(0)) + metodo.substring(1));
            return operation;
        };
    }
}