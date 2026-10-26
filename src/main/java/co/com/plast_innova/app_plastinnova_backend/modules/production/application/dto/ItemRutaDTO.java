package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

public record ItemRutaDTO(
        String codigo,
        String descripcion,
        TipoItem tipo
) {}
