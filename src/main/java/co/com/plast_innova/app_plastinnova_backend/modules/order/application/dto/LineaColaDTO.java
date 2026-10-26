package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Línea de la cola de trabajo, con los datos de su pedido que el operador necesita para ubicarla.
 *
 * @param cantidadMaximaReporte tope de piezas para el próximo reporte: lo que reportó la etapa anterior (o lo solicitado)
 * @param avanceReportado       ya hay piezas reportadas por un usuario (se pueden corregir con detalle:corregir-cantidad)
 * @param atrasado              el pedido tiene fecha estimada de entrega y ese día ya pasó (Colombia)
 */
public record LineaColaDTO(
        Long id,
        Long pedidoId,
        String numeroPedido,
        String clienteRazonSocial,
        Integer posicion,
        String productoSku,
        String productoNombre,
        EstadoResponseDTO estadoActual,
        Integer cantidadSolicitada,
        Integer cantidadProcesada,
        int cantidadMaximaReporte,
        boolean avanceReportado,
        TipoAlistamiento tipoAlistamiento,
        Boolean esAnexo,
        LocalDateTime fechaCreacionFm,
        LocalDate fechaEstimadaEntrega,
        boolean atrasado,
        /** Orden de producción que la línea sigue: su avance se reporta en la orden. null si no tiene. */
        String ordenProduccion,
        Long ordenProduccionId
) {}
