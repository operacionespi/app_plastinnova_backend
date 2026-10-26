package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;

import java.time.LocalDate;

/** Línea de pedido que atiende una orden (o que puede atender, en las candidatas). */
public record LineaOrdenDTO(
        Long detalleId,
        Long pedidoId,
        String numeroPedido,
        String cliente,
        Integer posicion,
        EstadoResponseDTO estadoLinea,
        Integer cantidadSolicitada,
        /** Piezas de la orden para la línea; en las candidatas, lo que le falta (sin lo comprometido ni lo entregado). */
        Integer cantidad,
        /** false: se desvinculó, pasó a No sale o la orden se cerró. */
        boolean activa,
        LocalDate fechaEstimadaEntrega
) {}
