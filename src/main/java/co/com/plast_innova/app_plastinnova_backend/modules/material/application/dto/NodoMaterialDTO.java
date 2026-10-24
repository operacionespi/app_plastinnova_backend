package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.math.BigDecimal;
import java.util.List;

/** Un ítem dentro del árbol de la lista de materiales, con la cantidad requerida para la cantidad pedida de la raíz. */
public record NodoMaterialDTO(
        String codigo,
        String descripcion,
        TipoItem tipo,
        String unidad,
        /** false: el código no está en el maestro de ítems (sin descripción, tipo ni unidad). */
        Boolean enMaestro,
        /** Orden del componente dentro de su padre; null en la raíz. */
        Integer secuencia,
        /** Bodega de la que se toma el componente; null en la raíz. */
        String bodega,
        /** Unidades por cada unidad del padre; null en la raíz. */
        BigDecimal cantidadPorPadre,
        /** Unidades requeridas en total para la cantidad de la raíz. */
        BigDecimal cantidad,
        List<NodoMaterialDTO> componentes
) {}
