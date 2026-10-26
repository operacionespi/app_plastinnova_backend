package co.com.plast_innova.app_plastinnova_backend.modules.inventory.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.MovimientoInventario;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.Slot;
import co.com.plast_innova.app_plastinnova_backend.modules.inventory.domain.TipoMovimientoInventario;

import co.com.plast_innova.app_plastinnova_backend.shared.domain.Decimales;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record MovimientoDTO(
        Long id,
        TipoMovimientoInventario tipo,
        String itemCodigo,
        String itemDescripcion,
        String unidad,
        /** "BODEGA / SLOT"; null si el movimiento no resta de ningún slot. */
        String origen,
        /** "BODEGA / SLOT"; null si el movimiento no suma a ningún slot. */
        String destino,
        BigDecimal cantidad,
        BigDecimal cantidadContada,
        String motivo,
        String observaciones,
        String usuario,
        LocalDateTime fecha,
        /** Movimientos de una línea de pedido (despacho, entrega, producción): su pedido. */
        Long pedidoId,
        String numeroPedido,
        /** Entrada de lo producido o consumo de componentes de una orden de producción. */
        Long ordenProduccionId,
        String numeroOrdenProduccion,
        /** Entrega con remisión, o devolución al anularla. */
        Long remisionId,
        String numeroRemision
) {
    public static MovimientoDTO de(MovimientoInventario m) {
        return new MovimientoDTO(m.getId(), m.getTipo(), m.getItem().getCodigo(), m.getItem().getDescripcion(),
                m.getItem().getUnidad(), ubicacion(m.getSlotOrigen()), ubicacion(m.getSlotDestino()),
                Decimales.paraMostrar(m.getCantidad()), Decimales.paraMostrar(m.getCantidadContada()), m.getMotivo(),
                m.getObservaciones(), m.getUsuario(), m.getFecha(),
                m.getPedidoDetalle() == null ? null : m.getPedidoDetalle().getPedido().getId(),
                m.getPedidoDetalle() == null ? null : m.getPedidoDetalle().getPedido().getNumeroPedido(),
                m.getOrdenProduccion() == null ? null : m.getOrdenProduccion().getId(),
                m.getOrdenProduccion() == null ? null : m.getOrdenProduccion().getNumero(),
                m.getRemision() == null ? null : m.getRemision().getId(),
                m.getRemision() == null ? null : m.getRemision().getNumero());
    }

    private static String ubicacion(Slot slot) {
        return slot == null ? null : slot.getBodega().getCodigo() + " / " + slot.getCodigo();
    }
}
