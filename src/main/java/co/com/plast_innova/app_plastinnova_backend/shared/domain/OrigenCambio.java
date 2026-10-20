package co.com.plast_innova.app_plastinnova_backend.shared.domain;

/**
 * Origen de un cambio de estado registrado en el historial.
 */
public enum OrigenCambio {
    /** Cambio manual realizado por un usuario desde la aplicación. */
    USUARIO,
    /** Creación o cambio aplicado por la sincronización con FileMaker. */
    SYNC
}
