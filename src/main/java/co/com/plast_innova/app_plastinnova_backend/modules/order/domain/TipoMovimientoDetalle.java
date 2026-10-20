package co.com.plast_innova.app_plastinnova_backend.modules.order.domain;

/** Movimiento registrado en el historial de una línea. */
public enum TipoMovimientoDetalle {
    /** La línea pasó a otra etapa reportando las piezas procesadas en la que dejó. */
    CAMBIO_ESTADO,
    /** Se corrigieron las piezas reportadas en la etapa anterior, sin cambiar de etapa. */
    CORRECCION_CANTIDAD,
    /** El supervisor marcó la línea como No sale (no disponibilidad del producto). */
    NO_SALE,
    /** El supervisor devolvió una línea en No sale a la etapa en que estaba. */
    DEVOLUCION
}
