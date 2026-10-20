package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi;

/** Carga actual de producción: líneas activas en cada estado y sus piezas. */
public record KpiLineasPorEstadoDTO(
        Integer estadoId,
        String codigo,
        String nombre,
        String color,
        long lineas,
        long piezasSolicitadas,
        long piezasProcesadas
) {}
