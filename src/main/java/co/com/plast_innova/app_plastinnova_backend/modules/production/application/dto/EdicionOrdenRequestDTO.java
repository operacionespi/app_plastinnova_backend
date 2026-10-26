package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import jakarta.validation.constraints.Size;

import java.time.LocalDate;

public record EdicionOrdenRequestDTO(
        LocalDate fechaRequerida,

        @Size(max = 500, message = "Las observaciones no pueden exceder los 500 caracteres")
        String observaciones
) {}
