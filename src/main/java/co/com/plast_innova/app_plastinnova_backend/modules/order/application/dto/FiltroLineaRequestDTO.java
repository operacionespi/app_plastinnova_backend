package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;

import java.util.List;

/**
 * Filtros de la cola de trabajo de las líneas; todos opcionales. Siempre se excluyen las líneas eliminadas en
 * FileMaker, las que ya están en un estado final y las de pedidos cerrados o eliminados.
 *
 * @param estadosIds   etapas que se quieren ver (la pestaña elegida en la tablet)
 * @param numeroPedido coincide desde el inicio del número
 */
public record FiltroLineaRequestDTO(
        List<Integer> estadosIds,
        TipoAlistamiento tipoAlistamiento,
        String numeroPedido,
        Long clienteId,
        Boolean esAnexo
) {}
