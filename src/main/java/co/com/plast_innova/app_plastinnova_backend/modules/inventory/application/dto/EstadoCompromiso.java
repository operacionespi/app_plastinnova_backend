package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

/** Cuánto de lo pendiente de un pedido (líneas que aún pueden reservar) tiene producto terminado comprometido. */
public enum EstadoCompromiso {
    SIN_COMPROMETER,
    PARCIAL,
    COMPROMETIDO,
    /** Ninguna línea puede reservar (todas entregadas, sin ítem, o el pedido está cerrado). */
    NO_APLICA
}
