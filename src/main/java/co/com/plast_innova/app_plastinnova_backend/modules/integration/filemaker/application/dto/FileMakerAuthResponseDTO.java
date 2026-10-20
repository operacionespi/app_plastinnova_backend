package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record FileMakerAuthResponseDTO(Response response) {
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Response(String token) {}
}
