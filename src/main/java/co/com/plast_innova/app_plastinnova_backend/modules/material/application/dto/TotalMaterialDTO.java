package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.math.BigDecimal;

/** Cantidad total de un componente sumada en todas las líneas y niveles. */
public record TotalMaterialDTO(
        String codigo,
        String descripcion,
        TipoItem tipo,
        String unidad,
        Boolean enMaestro,
        BigDecimal cantidad,
        /** true: se fabrica a partir de otros componentes; false: no tiene lista de materiales (se compra o se toma de bodega). */
        Boolean tieneComponentes
) {}
