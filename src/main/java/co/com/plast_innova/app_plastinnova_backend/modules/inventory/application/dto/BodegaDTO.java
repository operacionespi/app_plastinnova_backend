package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.Bodega;

public record BodegaDTO(Integer id, String codigo, String descripcion) {
    public static BodegaDTO de(Bodega b) {
        return new BodegaDTO(b.getId(), b.getCodigo(), b.getDescripcion());
    }
}
