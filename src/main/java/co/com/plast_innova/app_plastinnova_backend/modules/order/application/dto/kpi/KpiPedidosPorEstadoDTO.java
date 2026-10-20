package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi;

/** Pedidos activos (no eliminados en FileMaker) que están hoy en cada estado. */
public record KpiPedidosPorEstadoDTO(
        Integer estadoId,
        String codigo,
        String nombre,
        String color,
        Boolean esFinal,
        long pedidos
) {}
