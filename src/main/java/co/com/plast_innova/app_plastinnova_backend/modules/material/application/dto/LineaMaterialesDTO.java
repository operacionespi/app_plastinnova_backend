package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

public record LineaMaterialesDTO(
        Long detalleId,
        Integer posicion,
        String productoSku,
        String productoNombre,
        Integer cantidad,
        /** null: el SKU no está en el maestro de ítems ni en la lista de materiales. */
        NodoMaterialDTO estructura
) {}
