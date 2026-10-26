package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;

import java.math.BigDecimal;

/** Una línea del pedido vista desde las entregas: lo que falta y lo que hay en la bodega para ella. */
public record LineaEntregaDTO(
        Long detalleId,
        Integer posicion,
        String productoSku,
        String descripcion,
        String unidad,
        EstadoResponseDTO estadoLinea,
        Integer cantidadSolicitada,
        Integer cantidadEntregada,
        Integer cantidadPorEntregar,
        /** Comprometido para la línea en la bodega de producto terminado. */
        BigDecimal reservada,
        /** Libre en la bodega (sin lo comprometido por todos los pedidos). */
        BigDecimal libre,
        /** Lo máximo que puede entregar ahora una remisión. */
        Integer maximoEntregable,
        /** El producto no está en el maestro de ítems: se entrega sin mover inventario. */
        boolean sinInventario,
        /** Orden de producción que la atiende ahora; null si no tiene. */
        String ordenProduccion,
        /** Por qué no se puede entregar; null si se puede. */
        String motivoNoEntregable
) {}
