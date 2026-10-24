package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Existencia;
import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ExistenciaDTO(
        String itemCodigo,
        String itemDescripcion,
        TipoItem tipo,
        String unidad,
        String bodegaCodigo,
        Integer slotId,
        String slotCodigo,
        BigDecimal cantidad,
        LocalDateTime fechaActualizacion
) {
    public static ExistenciaDTO de(Existencia e) {
        return new ExistenciaDTO(e.getItem().getCodigo(), e.getItem().getDescripcion(), e.getItem().getTipo(),
                e.getItem().getUnidad(), e.getSlot().getBodega().getCodigo(), e.getSlot().getId(), e.getSlot().getCodigo(),
                Decimales.paraMostrar(e.getCantidad()),
                e.getFechaActualizacion());
    }
}
