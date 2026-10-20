package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

/**
 * Lo mínimo de cada línea sincronizada para decidir, sin cargar entidades, si cambió en FileMaker.
 */
public record ResumenLineaFm(String filemakerId, String numeroPedidoFm, String huellaFm, Boolean eliminadoEnFm) {
}
