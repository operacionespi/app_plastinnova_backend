package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import java.util.List;

/** Producto terminado comprometido por un pedido, línea por línea (sin las eliminadas en FileMaker ni las que no salen). */
public record ReservasPedidoDTO(
        Long pedidoId,
        String numeroPedido,
        /** Código de la bodega de producto terminado. */
        String bodega,
        EstadoCompromiso estado,
        List<ReservaLineaDTO> lineas
) {}
