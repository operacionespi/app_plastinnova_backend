package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain;

public enum EstadoSync {
    EN_PROCESO,
    EXITO,
    /** Terminó, pero algunos registros fallaron (ver registros_con_error). */
    PARCIAL,
    ERROR
}
