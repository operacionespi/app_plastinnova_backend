package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.List;
import java.util.Map;

/**
 * Respuesta de GET /layouts/{layout}/records de la Data API de FileMaker.
 * Los valores de fieldData llegan como texto o número según el tipo del campo ("" cuando está vacío).
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record FileMakerRegistrosResponseDTO(Response response) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(DataInfo dataInfo, List<Registro> data) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record DataInfo(Integer foundCount, Integer returnedCount, Integer totalRecordCount) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Registro(Map<String, Object> fieldData, String recordId, String modId) {}
}
