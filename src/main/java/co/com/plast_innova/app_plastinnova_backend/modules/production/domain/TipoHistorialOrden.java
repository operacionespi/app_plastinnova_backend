package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

public enum TipoHistorialOrden {
    CREACION,
    /** Solo antes de V18: ahora los avances son entregas entre procesos. */
    CAMBIO_ETAPA,
    /** Cambio de fecha u observaciones, de sus productos o de las líneas que atiende. */
    EDICION,
    ANULACION
}
