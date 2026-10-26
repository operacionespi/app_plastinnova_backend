package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.material.domain.TipoItem;

import java.math.BigDecimal;

/** Lo que consume la orden de un componente al reportar lo producido. */
public record ComponenteOrdenDTO(
        String codigo,
        String descripcion,
        TipoItem tipo,
        String unidad,
        /** Bodega de donde se consume; null si la lista de materiales no la indica (no se consume). */
        String bodega,
        BigDecimal cantidadPorUnidad,
        /** Para las piezas en proceso (o lo consumido por lo recibido y dado de baja, si el producto terminó). */
        BigDecimal requerido,
        /** Existencia en la bodega menos lo comprometido por pedidos. */
        BigDecimal libre,
        boolean alcanza
) {}
