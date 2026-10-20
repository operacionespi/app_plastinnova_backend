package co.com.plast_innova.app_plastinnova_backend.modules.integration.filemaker.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;

import java.time.LocalDateTime;

/**
 * Registro del layout "PedidosLineas-Api" ya validado: una línea de pedido con los datos
 * de su pedido y de su cliente.
 *
 * @param recordId        llave de la línea en FileMaker
 * @param fechaCreacionUtc LinPed_FechaCreacion convertida de hora Colombia a UTC
 * @param fechaModificacionUtc zMarcaFechaYHoraModificación en UTC; null si FileMaker no la envía
 * @param esAnexo         LinPed_Anexo: la línea es un anexo agregado al pedido
 * @param huella          SHA-256 de todos los campos del registro (detección de cambios)
 */
public record FilaPedidoFm(
        String recordId,
        Integer modId,
        String numeroPedido,
        Integer posicion,
        String codigoProducto,
        int cantidad,
        LocalDateTime fechaCreacionUtc,
        LocalDateTime fechaModificacionUtc,
        TipoAlistamiento tipoAlistamiento,
        boolean esAnexo,
        String estadoLinea,
        String estadoPedido,
        String cliente,
        String vendedor,
        String ciudad,
        String pais,
        String huella
) {}
