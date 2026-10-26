package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.ReservaInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.PedidoDetalle;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Una reserva vigente, para el listado del inventario. */
public record ReservaDTO(
        Long id,
        Long pedidoId,
        String numeroPedido,
        Integer posicion,
        String itemCodigo,
        String itemDescripcion,
        String unidad,
        String bodega,
        BigDecimal cantidad,
        Integer cantidadSolicitada,
        String estadoLinea,
        String usuario,
        LocalDateTime fecha
) {
    public static ReservaDTO de(ReservaInventario r) {
        PedidoDetalle d = r.getPedidoDetalle();
        return new ReservaDTO(r.getId(), d.getPedido().getId(), d.getPedido().getNumeroPedido(), d.getPosicion(),
                r.getItem().getCodigo(), r.getItem().getDescripcion(), r.getItem().getUnidad(), r.getBodega().getCodigo(),
                Decimales.paraMostrar(r.getCantidad()), d.getCantidadSolicitada(), d.getEstadoActual().getNombre(),
                r.getUsuario(), r.getFecha());
    }
}
