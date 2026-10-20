package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi;

import java.math.BigDecimal;

/**
 * Días entre la creación del pedido en FileMaker y su cierre (estado final), para los pedidos cerrados en el rango.
 * Los anulados (todas sus líneas en No sale) no cuentan.
 *
 * @param pedidosConFechaEntrega cerrados que tenían fecha estimada de entrega (los únicos que se pueden medir a tiempo)
 * @param pedidosATiempo         de esos, los cerrados a más tardar el día de la fecha estimada (hora Colombia)
 * @param pedidosCompletos       cerrados con todas sus líneas en una etapa final (Entregado o No sale)
 * @param pedidosOtif            con fecha estimada, a tiempo y completos (OTIF = pedidosOtif / pedidosConFechaEntrega)
 */
public record KpiTiempoEntregaDTO(
        long pedidosCerrados,
        long pedidosConFechaEntrega,
        long pedidosATiempo,
        long pedidosCompletos,
        long pedidosOtif,
        BigDecimal promedioDias,
        BigDecimal minimoDias,
        BigDecimal maximoDias
) {}
