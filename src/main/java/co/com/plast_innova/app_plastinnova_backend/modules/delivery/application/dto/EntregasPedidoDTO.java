package co.com.plast_innova.app_plastinnova_backend.modules.delivery.application.dto;

import java.util.List;

public record EntregasPedidoDTO(
        Long pedidoId,
        String numeroPedido,
        String cliente,
        /** Bodega de donde salen las entregas. */
        String bodega,
        /** Por qué el pedido no admite remisiones (cerrado o eliminado); null si las admite. */
        String motivoCerrado,
        List<LineaEntregaDTO> lineas,
        List<RemisionDTO> remisiones
) {}
