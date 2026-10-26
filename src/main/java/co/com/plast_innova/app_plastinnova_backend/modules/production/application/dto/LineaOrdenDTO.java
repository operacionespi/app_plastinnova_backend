package co.com.plast_innova.app_plastinnova_backend.modules.production.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.EstadoResponseDTO;

import java.time.LocalDate;

/** Línea de pedido que atiende una orden (o que puede atender, en las candidatas y las pendientes). */
public record LineaOrdenDTO(
        Long detalleId,
        Long pedidoId,
        String numeroPedido,
        String cliente,
        Integer posicion,
        String itemCodigo,
        String itemDescripcion,
        EstadoResponseDTO estadoLinea,
        Integer cantidadSolicitada,
        /** Piezas de la orden para la línea; en las candidatas y pendientes, lo que le falta (sin lo comprometido ni lo entregado). */
        Integer cantidad,
        /** Piezas que ya llegaron al almacén para la línea. */
        Integer cantidadRecibida,
        /** false: se desvinculó, recibió todo, pasó a No sale o el producto terminó. */
        boolean activa,
        LocalDate fechaEstimadaEntrega,
        /** En las pendientes: por qué no se puede incluir en una orden (null si se puede). */
        String motivo
) {}
