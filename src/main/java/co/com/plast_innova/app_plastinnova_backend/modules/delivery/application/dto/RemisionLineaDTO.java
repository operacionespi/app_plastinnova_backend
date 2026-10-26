package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto;

public record RemisionLineaDTO(
        Long detalleId,
        Integer posicion,
        String productoSku,
        String descripcion,
        Integer cantidad
) {}
