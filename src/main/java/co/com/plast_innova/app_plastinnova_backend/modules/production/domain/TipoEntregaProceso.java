package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

public enum TipoEntregaProceso {
    /** Al proceso siguiente de la ruta (o al almacén desde el último). */
    AVANCE,
    /** A un proceso anterior: reproceso de piezas rechazadas. */
    DEVOLUCION,
    /** Desecho: las piezas salen de la orden. */
    BAJA
}
