package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;

public record SlotDTO(Integer id, Integer bodegaId, String bodegaCodigo, String codigo, String descripcion, Boolean activo) {
    public static SlotDTO de(Slot s) {
        return new SlotDTO(s.getId(), s.getBodega().getId(), s.getBodega().getCodigo(), s.getCodigo(), s.getDescripcion(),
                s.getActivo());
    }
}
