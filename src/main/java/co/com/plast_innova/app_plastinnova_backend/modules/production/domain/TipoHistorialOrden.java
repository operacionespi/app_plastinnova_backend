package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

public enum TipoHistorialOrden {
    CREACION,
    CAMBIO_ETAPA,
    /** Cambio de cantidad, fecha u observaciones, o de las líneas que atiende. */
    EDICION,
    ANULACION
}
