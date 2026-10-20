package co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi;

import java.math.BigDecimal;

/**
 * Tiempo entre la entrada a la fase de origen y la entrada a la fase de destino, para los ciclos que llegaron
 * al destino dentro del rango. Si una línea repite el ciclo (reproceso), cada vuelta cuenta por separado.
 *
 * @param ciclos            veces que una línea pasó de origen a destino
 * @param piezasProcesadas  piezas reportadas al llegar al destino
 */
public record KpiTiempoCicloDTO(
        String faseOrigen,
        String faseDestino,
        long ciclos,
        long lineas,
        long piezasProcesadas,
        BigDecimal promedioHoras,
        BigDecimal minimoHoras,
        BigDecimal maximoHoras
) {}
