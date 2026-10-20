package co.com.plast_innova.app_plastinnova_backend.modules.catalog.domain;

/**
 * Transición permitida entre dos estados de un mismo catálogo (origen -> destino).
 */
public interface TransicionEstado<E extends EstadoCatalogo> {

    Integer getId();

    E getEstadoOrigen();

    E getEstadoDestino();
}
