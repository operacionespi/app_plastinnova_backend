package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PedidoDetalleResponseDTO(
        Long id,
        String filemakerId,
        /** Posición de la línea dentro del pedido (LinPed_PosPed). */
        Integer posicion,
        TipoAlistamiento tipoAlistamiento,
        /** La línea es un anexo agregado al pedido (LinPed_Anexo en FileMaker). */
        Boolean esAnexo,
        String productoSku,
        String productoNombre,
        EstadoResponseDTO estadoActual,
        Integer cantidadSolicitada,
        Integer cantidadProcesada,
        /** Tope de piezas para el próximo reporte: lo que reportó la etapa anterior (o lo solicitado si nadie ha reportado). */
        Integer cantidadMaximaReporte,
        /** Ya hay piezas reportadas por un usuario: se pueden corregir con detalle:corregir-cantidad. */
        Boolean avanceReportado,
        BigDecimal precioUnitario,
        LocalDateTime fechaCreacionFm,
        LocalDateTime fechaModificacionFm,
        /** La línea ya no existe en FileMaker: se muestra marcada y no admite cambios de estado. */
        Boolean eliminadoEnFm
) {}