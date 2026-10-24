package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

public record ItemResumenDTO(
        String codigo,
        String descripcion,
        TipoItem tipo,
        String unidad,
        Boolean enMaestro,
        /** Tiene componentes en la lista de materiales. */
        Boolean tieneComponentes
) {}
