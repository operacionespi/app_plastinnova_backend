package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

public enum EstadoOrdenProduccion {
    /** En proceso: recorre las etapas. */
    ABIERTA,
    /** Llegó a la etapa de almacén: lo producido entró al inventario. */
    TERMINADA,
    ANULADA
}
