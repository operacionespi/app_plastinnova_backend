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
        LocalDateTime fecha
) {
    public static MovimientoDTO de(MovimientoInventario m) {
        return new MovimientoDTO(m.getId(), m.getTipo(), m.getItem().getCodigo(), m.getItem().getDescripcion(),
                m.getItem().getUnidad(), ubicacion(m.getSlotOrigen()), ubicacion(m.getSlotDestino()),
                Decimales.paraMostrar(m.getCantidad()), Decimales.paraMostrar(m.getCantidadContada()), m.getMotivo(),
                m.getObservaciones(), m.getUsuario(), m.getFecha());
    }

    private static String ubicacion(Slot slot) {
        return slot == null ? null : slot.getBodega().getCodigo() + " / " + slot.getCodigo();
    }
}
