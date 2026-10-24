package co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain;

/** Ver V14__inventario.sql: de qué slot resta y a cuál suma cada tipo. */
public enum TipoMovimientoInventario {
    ENTRADA,
    SALIDA,
    TRASLADO,
    /** Conteo físico: la cantidad es la diferencia con el saldo. */
    AJUSTE,
    /** Igual que el ajuste, desde el CSV de existencias. */
    CARGA_INICIAL
}
