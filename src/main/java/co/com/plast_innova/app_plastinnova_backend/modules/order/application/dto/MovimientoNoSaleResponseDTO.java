package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

/**
 * Resultado de marcar o devolver una línea en No sale.
 *
 * @param estadoPedido       estado del pedido después del movimiento
 * @param cambioEstadoPedido true si el pedido se anuló (todas sus líneas en No sale) o se reabrió al devolver la línea
 */
public record MovimientoNoSaleResponseDTO(
        PedidoDetalleResponseDTO linea,
        EstadoResponseDTO estadoPedido,
        boolean cambioEstadoPedido
) {
}
