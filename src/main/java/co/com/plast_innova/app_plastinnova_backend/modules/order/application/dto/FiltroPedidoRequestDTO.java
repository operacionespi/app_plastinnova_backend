package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * Filtros del listado de pedidos; todos opcionales. Solo se listan pedidos activos en FileMaker.
 *
 * @param numeroPedido  coincide desde el inicio del número
 * @param ciudad        parte del nombre de la ciudad de envío
 * @param vendedores    vendedores del cliente, nombres completos (cualquiera de ellos)
 * @param fechaDesde    fecha de creación en FileMaker, primer día incluido (hora Colombia)
 * @param fechaHasta    fecha de creación en FileMaker, último día incluido (hora Colombia)
 * @param soloAbiertos  excluye los pedidos que ya llegaron a un estado final
 * @param soloAtrasados pedidos abiertos con fecha estimada de entrega vencida
 */
public record FiltroPedidoRequestDTO(
        String numeroPedido,
        Long clienteId,
        String ciudad,
        List<String> vendedores,
        List<Integer> estadosIds,
        LocalDate fechaDesde,
        LocalDate fechaHasta,
        Boolean soloAbiertos,
        Boolean soloAtrasados
) {}
