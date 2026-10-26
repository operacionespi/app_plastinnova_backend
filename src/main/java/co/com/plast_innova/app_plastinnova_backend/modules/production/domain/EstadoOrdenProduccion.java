package co.com.plast_innova.app_plastinnova_backend.modules.production.domain;

public enum EstadoOrdenProduccion {
    /** Sus productos recorren los procesos de su ruta. */
    ABIERTA,
    /** Cada producto llegó al almacén o se dio de baja. */
    TERMINADA,
    ANULADA
}
