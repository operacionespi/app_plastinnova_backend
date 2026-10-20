package co.com.plast_innova.app_plastinnova_backend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.web.config.EnableSpringDataWebSupport;

import static org.springframework.data.web.config.EnableSpringDataWebSupport.PageSerializationMode.VIA_DTO;

/**
 * Las páginas se serializan siempre como PagedModel: {"content": [...], "page": {size, number, totalElements,
 * totalPages}}. Es el formato estable que consume el frontend (el JSON de PageImpl puede cambiar entre versiones).
 * Los controladores retornan PagedModel explícitamente para que OpenAPI documente esa misma forma.
 */
@Configuration
@EnableSpringDataWebSupport(pageSerializationMode = VIA_DTO)
public class WebConfig {
}
