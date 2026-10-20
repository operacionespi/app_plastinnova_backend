package co.com.plast_innova.app_plastinnova_backend.modules.order.repositories;

/**
 * Totales de las líneas activas de un pedido, para el listado sin cargar las líneas.
 */
public record TotalesLineasPedido(Long pedidoId, Long lineas, Long piezasSolicitadas, Long piezasProcesadas) {
}
