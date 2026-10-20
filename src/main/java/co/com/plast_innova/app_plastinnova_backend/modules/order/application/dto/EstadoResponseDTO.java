package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

public record EstadoResponseDTO(
        Integer id,
        String codigo,
        String nombre,
        /** Color hexadecimal del catálogo (ej. #1E88E5) para pintar el estado en el frontend. */
        String color
) {}
