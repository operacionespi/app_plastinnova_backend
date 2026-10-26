package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

public record ProductoResumenDTO(
        String itemCodigo,
        String itemDescripcion,
        int cantidad,
        int cantidadRecibida,
        int cantidadBaja
) {}
