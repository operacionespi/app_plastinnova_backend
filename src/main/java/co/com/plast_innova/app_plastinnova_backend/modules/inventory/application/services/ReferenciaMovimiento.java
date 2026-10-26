package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.services;

import co.com.plast_innova.app_plastinnova_backend.modules.delivery.domain.Remision;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.modules.production.domain.OrdenProduccion;

import java.util.ArrayList;
import java.util.List;

/**
 * Documento que origina un movimiento automático: la línea de pedido, la orden de producción y/o la remisión. Queda
 * enlazado al movimiento y resumido en sus observaciones.
 */
public record ReferenciaMovimiento(PedidoDetalle detalle, OrdenProduccion orden, Remision remision) {

    public static ReferenciaMovimiento deLinea(PedidoDetalle detalle) {
        return new ReferenciaMovimiento(detalle, null, null);
    }

    public static ReferenciaMovimiento deOrden(OrdenProduccion orden) {
        return new ReferenciaMovimiento(null, orden, null);
    }

    public static ReferenciaMovimiento deRemision(Remision remision, PedidoDetalle detalle) {
        return new ReferenciaMovimiento(detalle, null, remision);
    }

    /**
     * Sale lo que una línea tenía comprometido (quien llama ya redujo su reserva o verificó lo libre): no pasa por la
     * regla que protege lo comprometido de la bodega.
     */
    boolean esDeLinea() {
        return detalle != null;
    }

    String observaciones() {
        List<String> partes = new ArrayList<>();
        if (orden != null) {
            partes.add(orden.getNumero());
        }
        if (remision != null) {
            partes.add(remision.getNumero());
        }
        if (detalle != null) {
            partes.add("Pedido " + detalle.getPedido().getNumeroPedido() + ", línea " + detalle.getPosicion());
        }
        return String.join(" · ", partes);
    }
}
