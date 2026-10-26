package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.math.BigDecimal;

/** Componente del primer nivel de la lista de materiales de un ítem: lo que consume producir una unidad. */
public record ComponenteDirectoDTO(
        Integer itemId,
        String codigo,
        String descripcion,
        TipoItem tipo,
        String unidad,
        /** Bodega de donde se consume, según la lista; null si no la indica. */
        String bodega,
        BigDecimal cantidadPorUnidad
) {}
