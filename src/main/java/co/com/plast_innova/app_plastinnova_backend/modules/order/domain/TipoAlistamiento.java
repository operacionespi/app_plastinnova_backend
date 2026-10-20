package co.com.plast_innova.app_plastinnova_backend.modules.order.domain;

/**
 * Tipo de alistamiento de una línea según LinPed_AlistamArea en FileMaker:
 * "Internacionales" → INTERNACIONAL; "Nacionales" y cualquier otra área (PuntoDeVenta, TapaTuerca...) → NACIONAL.
 */
public enum TipoAlistamiento {
    NACIONAL,
    INTERNACIONAL
}
