package co.com.plast_innova.app_plastinnova_backend.modules.material.application.dto;

import java.math.BigDecimal;

public record LineaMaterialesDTO(
        Long detalleId,
        Integer posicion,
        String productoSku,
        String productoNombre,
        Integer cantidad,
        /** Producto terminado comprometido para la línea: solo lo que falta se explota en los totales. */
        BigDecimal reservada,
        /** null: el SKU no está en el maestro de ítems ni en la lista de materiales. */
        NodoMaterialDTO estructura
) {}
