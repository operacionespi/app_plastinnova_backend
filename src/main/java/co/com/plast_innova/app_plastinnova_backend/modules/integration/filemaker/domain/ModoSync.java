package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.domain;

/** Qué registros de FileMaker lee una ejecución de la sincronización. */
public enum ModoSync {
    /** Todas las líneas creadas desde app.filemaker.sync.fecha-desde. Es la primera carga (aún no hay marca). */
    COMPLETA,
    /** Solo los pedidos con líneas modificadas desde la última marca (zMarcaFechaYHoraModificación). */
    INCREMENTAL,
    /** Los pedidos abiertos en la app: detecta las líneas borradas en FileMaker, que no cambian ninguna marca. */
    RECONCILIACION
}
