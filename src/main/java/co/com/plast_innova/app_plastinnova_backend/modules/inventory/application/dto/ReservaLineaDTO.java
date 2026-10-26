package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import java.math.BigDecimal;

/** Producto terminado de una línea de pedido en la bodega de producto terminado. */
public record ReservaLineaDTO(
        Long detalleId,
        Integer posicion,
        String productoSku,
        /** null: el SKU no está en el maestro de ítems. */
        String itemCodigo,
        String descripcion,
        String unidad,
        String estadoLinea,
        Integer cantidadSolicitada,
        /** Ya entregado con remisiones: lo comprometido no pasa de lo que falta por entregar. */
        Integer cantidadEntregada,
        /** Comprometido para esta línea. */
        BigDecimal reservada,
        /** Existencia total del ítem en la bodega (todos sus slots). */
        BigDecimal existencia,
        /** Existencia menos lo comprometido por todos los pedidos; nunca negativo. */
        BigDecimal libre,
        /** Lo máximo que puede quedar comprometido para la línea: lo que falta por entregar, limitado por lo libre más lo suyo. */
        BigDecimal maximoReservable,
        boolean reservable,
        /** Por qué no puede reservar; null si puede. */
        String motivoNoReservable
) {}
