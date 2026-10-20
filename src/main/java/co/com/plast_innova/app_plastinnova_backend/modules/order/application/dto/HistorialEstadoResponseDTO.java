package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoMovimientoDetalle;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.OrigenCambio;

import java.time.LocalDateTime;

/**
 * Un cambio de estado de un pedido o de una línea. Fechas en UTC.
 *
 * @param estadoAnterior    null en el registro de creación
 * @param usuarioNombre     null cuando el cambio lo hizo la sincronización (origen SYNC)
 * @param cantidadProcesada solo aplica a las líneas
 * @param tipo              solo líneas: cambio de estado o corrección de piezas (null en pedidos)
 * @param cantidadAnterior  solo en correcciones: las piezas que había antes de corregir
 */
public record HistorialEstadoResponseDTO(
        Long id,
        EstadoResponseDTO estadoAnterior,
        EstadoResponseDTO estado,
        OrigenCambio origen,
        String usuarioNombre,
        String usuarioEmail,
        Integer cantidadProcesada,
        LocalDateTime fechaCambio,
        String observaciones,
        TipoMovimientoDetalle tipo,
        Integer cantidadAnterior
) {}
