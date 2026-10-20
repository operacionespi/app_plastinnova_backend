package co.com.plast_innova.app_plastinnova_backend.modules.order.infrastructure.kpi;

import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiLineasPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiPedidosPorEstadoDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiProduccionOperadorDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiTiempoEntregaDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.KpiUbicacionDTO;
import co.com.plast_innova.app_plastinnova_backend.modules.order.application.dto.kpi.NivelUbicacion;
import co.com.plast_innova.app_plastinnova_backend.modules.order.domain.TipoAlistamiento;
import co.com.plast_innova.app_plastinnova_backend.shared.domain.RangoFechas;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.List;

/**
 * Indicadores calculados en SQL Server: la agregación se hace en la base y solo viaja el resultado.
 * Fechas en UTC; los rangos son [desde, hasta).
 */
@Repository
@RequiredArgsConstructor
public class KpiRepository {

    private final NamedParameterJdbcTemplate jdbc;

    /** Usa IX_Pedido_Activos_Estado_Entrega. Incluye los estados inactivos que todavía tienen pedidos. */
    public List<KpiPedidosPorEstadoDTO> pedidosPorEstado() {
        return jdbc.query("""
                SELECT e.id, e.codigo, e.nombre, e.color, e.es_final, COUNT(p.id) AS pedidos
                FROM Estado_Pedido e
                LEFT JOIN Pedido p ON p.estado_actual_id = e.id AND p.eliminado_en_fm = 0
                GROUP BY e.id, e.codigo, e.nombre, e.color, e.es_final, e.activo, e.orden_logico
                HAVING e.activo = 1 OR COUNT(p.id) > 0
                ORDER BY e.orden_logico, e.id
                """, new MapSqlParameterSource(),
                (rs, i) -> new KpiPedidosPorEstadoDTO(rs.getInt("id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("color"), rs.getBoolean("es_final"), rs.getLong("pedidos")));
    }

    /** Líneas activas de pedidos activos. Usa IX_PedidoDetalle_Estado (filtrado por eliminado_en_fm = 0). */
    public List<KpiLineasPorEstadoDTO> lineasPorEstado(TipoAlistamiento tipoAlistamiento) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("tipo", tipoAlistamiento == null ? null : tipoAlistamiento.name(), Types.NVARCHAR);
        return jdbc.query("""
                SELECT e.id, e.codigo, e.nombre, e.color,
                       COUNT(d.id) AS lineas,
                       COALESCE(SUM(CAST(d.cantidad_solicitada AS BIGINT)), 0) AS solicitadas,
                       COALESCE(SUM(CAST(d.cantidad_procesada AS BIGINT)), 0) AS procesadas
                FROM Estado_Detalle e
                LEFT JOIN (Pedido_Detalle d JOIN Pedido p ON p.id = d.pedido_id AND p.eliminado_en_fm = 0)
                       ON d.estado_actual_id = e.id AND d.eliminado_en_fm = 0
                      AND (:tipo IS NULL OR d.tipo_alistamiento = :tipo)
                GROUP BY e.id, e.codigo, e.nombre, e.color, e.activo, e.orden_logico
                HAVING e.activo = 1 OR COUNT(d.id) > 0
                ORDER BY e.orden_logico, e.id
                """, params,
                (rs, i) -> new KpiLineasPorEstadoDTO(rs.getInt("id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("color"), rs.getLong("lineas"), rs.getLong("solicitadas"), rs.getLong("procesadas")));
    }

    /**
     * Para cada entrada al estado destino dentro del rango, toma la última entrada al estado origen anterior a ella,
     * siempre que entre ambas no haya otra entrada al destino (así un reproceso no se cuenta dos veces).
     * Las correcciones de cantidad no son entradas a una etapa: se excluyen.
     * Usa IX_HED_Estado_Fecha para el destino e IX_HED_Detalle_Estado_Fecha para buscar el origen.
     */
    public ResumenTiempos tiempoCiclo(Integer estadoOrigenId, Integer estadoDestinoId, RangoFechas rango) {
        MapSqlParameterSource params = rango(rango)
                .addValue("origen", estadoOrigenId)
                .addValue("destino", estadoDestinoId);
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS ciclos,
                       COUNT(DISTINCT d.pedido_detalle_id) AS lineas,
                       COALESCE(SUM(CAST(d.cantidad_procesada AS BIGINT)), 0) AS piezas,
                       AVG(CAST(DATEDIFF_BIG(SECOND, o.fecha_cambio, d.fecha_cambio) AS FLOAT)) AS promedio,
                       MIN(DATEDIFF_BIG(SECOND, o.fecha_cambio, d.fecha_cambio)) AS minimo,
                       MAX(DATEDIFF_BIG(SECOND, o.fecha_cambio, d.fecha_cambio)) AS maximo
                FROM Historial_Estado_Detalle d
                CROSS APPLY (
                    SELECT TOP 1 h.fecha_cambio
                    FROM Historial_Estado_Detalle h
                    WHERE h.pedido_detalle_id = d.pedido_detalle_id
                      AND h.estado_id = :origen
                      AND h.tipo = 'CAMBIO_ESTADO'
                      AND h.fecha_cambio <= d.fecha_cambio
                    ORDER BY h.fecha_cambio DESC, h.id DESC
                ) o
                WHERE d.estado_id = :destino
                  AND d.tipo = 'CAMBIO_ESTADO'
                  AND d.fecha_cambio >= :desde AND d.fecha_cambio < :hasta
                  AND NOT EXISTS (
                      SELECT 1 FROM Historial_Estado_Detalle x
                      WHERE x.pedido_detalle_id = d.pedido_detalle_id
                        AND x.estado_id = :destino
                        AND x.tipo = 'CAMBIO_ESTADO'
                        AND x.fecha_cambio > o.fecha_cambio
                        AND (x.fecha_cambio < d.fecha_cambio OR (x.fecha_cambio = d.fecha_cambio AND x.id < d.id))
                  )
                """, params, (rs, i) -> new ResumenTiempos(rs.getLong("ciclos"), rs.getLong("lineas"),
                rs.getLong("piezas"), segundosAHoras(rs, "promedio"), segundosAHoras(rs, "minimo"),
                segundosAHoras(rs, "maximo")));
    }

    /**
     * Pedidos cerrados en el rango. Usa IX_Pedido_FechaCierre. A tiempo: el día de cierre en Colombia (UTC-5 fijo,
     * sin horario de verano) no pasa de la fecha estimada; es la misma regla con la que un pedido abierto queda atrasado.
     * Completo: ninguna línea activa en una etapa no final (todas en Entregado o No sale), según su estado actual.
     * Los anulados no cuentan.
     */
    public KpiTiempoEntregaDTO tiempoEntrega(RangoFechas rango) {
        return jdbc.queryForObject("""
                SELECT COUNT(*) AS pedidos,
                       COUNT(p.fecha_estimada_entrega) AS con_fecha,
                       COUNT(CASE WHEN c.a_tiempo = 1 THEN 1 END) AS a_tiempo,
                       COUNT(CASE WHEN c.completo = 1 THEN 1 END) AS completos,
                       COUNT(CASE WHEN c.a_tiempo = 1 AND c.completo = 1 THEN 1 END) AS otif,
                       AVG(CAST(DATEDIFF_BIG(SECOND, p.fecha_creacion_fm, p.fecha_cierre) AS FLOAT)) AS promedio,
                       MIN(DATEDIFF_BIG(SECOND, p.fecha_creacion_fm, p.fecha_cierre)) AS minimo,
                       MAX(DATEDIFF_BIG(SECOND, p.fecha_creacion_fm, p.fecha_cierre)) AS maximo
                FROM Pedido p
                OUTER APPLY (
                    SELECT COUNT(*) AS pendientes
                    FROM Pedido_Detalle d
                    JOIN Estado_Detalle e ON e.id = d.estado_actual_id
                    WHERE d.pedido_id = p.id AND d.eliminado_en_fm = 0 AND e.es_final = 0
                ) l
                CROSS APPLY (
                    SELECT CASE WHEN CAST(DATEADD(HOUR, -5, p.fecha_cierre) AS DATE) <= p.fecha_estimada_entrega
                                THEN 1 ELSE 0 END AS a_tiempo,
                           CASE WHEN l.pendientes = 0 THEN 1 ELSE 0 END AS completo
                ) c
                WHERE p.fecha_cierre >= :desde AND p.fecha_cierre < :hasta AND p.eliminado_en_fm = 0
                  -- Anulado (todas las líneas en No sale): no hubo entrega que medir
                  AND p.estado_actual_id NOT IN (SELECT id FROM Estado_Pedido WHERE codigo = 'ANULADO')
                """, rango(rango), (rs, i) -> new KpiTiempoEntregaDTO(rs.getLong("pedidos"), rs.getLong("con_fecha"),
                rs.getLong("a_tiempo"), rs.getLong("completos"), rs.getLong("otif"), segundosADias(rs, "promedio"),
                segundosADias(rs, "minimo"), segundosADias(rs, "maximo")));
    }

    /** Cambios de estado hechos por usuarios (sin la sincronización ni las correcciones de cantidad). Usa IX_HED_Fecha. */
    public List<KpiProduccionOperadorDTO> produccionPorOperador(RangoFechas rango, Integer usuarioId) {
        MapSqlParameterSource params = rango(rango).addValue("usuario", usuarioId, Types.INTEGER);
        return jdbc.query("""
                SELECT u.id AS usuario_id, u.nombre_completo, e.id AS estado_id, e.nombre AS estado,
                       COUNT(*) AS movimientos,
                       COUNT(DISTINCT h.pedido_detalle_id) AS lineas,
                       COALESCE(SUM(CAST(h.cantidad_procesada AS BIGINT)), 0) AS piezas
                FROM Historial_Estado_Detalle h
                JOIN Usuario u ON u.id = h.usuario_id
                JOIN Estado_Detalle e ON e.id = h.estado_id
                WHERE h.origen = 'USUARIO'
                  AND h.tipo = 'CAMBIO_ESTADO' -- las correcciones de cantidad no son producción
                  AND h.fecha_cambio >= :desde AND h.fecha_cambio < :hasta
                  AND (:usuario IS NULL OR h.usuario_id = :usuario)
                GROUP BY u.id, u.nombre_completo, e.id, e.nombre, e.orden_logico
                ORDER BY u.nombre_completo, e.orden_logico
                OPTION (RECOMPILE) -- el filtro opcional por usuario no debe fijar un plan genérico
                """, params,
                (rs, i) -> new KpiProduccionOperadorDTO(rs.getInt("usuario_id"), rs.getString("nombre_completo"),
                        rs.getInt("estado_id"), rs.getString("estado"), rs.getLong("movimientos"),
                        rs.getLong("lineas"), rs.getLong("piezas")));
    }

    /** Pedidos creados en FileMaker dentro del rango, por ubicación de entrega. Usa IX_Pedido_FechaCreacionFm. */
    public List<KpiUbicacionDTO> pedidosPorUbicacion(NivelUbicacion nivel, RangoFechas rango) {
        // Columnas fijas por nivel (nunca texto del usuario): no hay riesgo de inyección
        String id;
        String nombre;
        switch (nivel) {
            case PAIS -> { id = "pa.id"; nombre = "COALESCE(pa.nombre, N'Sin ubicación')"; }
            case DEPARTAMENTO -> { id = "de.id"; nombre = "COALESCE(de.nombre, N'Sin departamento')"; }
            case ZONA -> { id = "z.id"; nombre = "COALESCE(z.nombre, N'Sin zona')"; }
            case CIUDAD -> { id = "ci.id"; nombre = "COALESCE(ci.nombre + N' (' + pa.nombre + N')', N'Sin ubicación')"; }
            default -> throw new IllegalArgumentException("Nivel no soportado: " + nivel);
        }
        return jdbc.query("""
                SELECT %1$s AS ubicacion_id, %2$s AS ubicacion,
                       COUNT(DISTINCT p.id) AS pedidos,
                       COUNT(DISTINCT CASE WHEN p.fecha_cierre IS NOT NULL THEN p.id END) AS cerrados,
                       COUNT(d.id) AS lineas,
                       COALESCE(SUM(CAST(d.cantidad_solicitada AS BIGINT)), 0) AS piezas
                FROM Pedido p
                LEFT JOIN Pedido_Detalle d ON d.pedido_id = p.id AND d.eliminado_en_fm = 0
                LEFT JOIN Ciudad ci ON ci.id = p.ciudad_envio_id
                LEFT JOIN Pais pa ON pa.id = ci.pais_id
                LEFT JOIN Departamento de ON de.id = ci.departamento_id
                LEFT JOIN Zona z ON z.id = ci.zona_id
                WHERE p.eliminado_en_fm = 0
                  AND p.fecha_creacion_fm >= :desde AND p.fecha_creacion_fm < :hasta
                GROUP BY %1$s, %2$s
                ORDER BY pedidos DESC, ubicacion
                """.formatted(id, nombre), rango(rango),
                (rs, i) -> new KpiUbicacionDTO(rs.getObject("ubicacion_id", Integer.class), rs.getString("ubicacion"),
                        rs.getLong("pedidos"), rs.getLong("cerrados"), rs.getLong("lineas"), rs.getLong("piezas")));
    }

    private static MapSqlParameterSource rango(RangoFechas rango) {
        return new MapSqlParameterSource()
                .addValue("desde", rango.desdeUtc())
                .addValue("hasta", rango.hastaUtc());
    }

    private static BigDecimal segundosAHoras(ResultSet rs, String columna) throws SQLException {
        return convertir(rs, columna, 3600);
    }

    private static BigDecimal segundosADias(ResultSet rs, String columna) throws SQLException {
        return convertir(rs, columna, 86400);
    }

    /** null (sin datos en el rango) se reporta como 0. */
    private static BigDecimal convertir(ResultSet rs, String columna, int divisor) throws SQLException {
        double segundos = rs.getDouble(columna);
        if (rs.wasNull()) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.UNNECESSARY);
        }
        return BigDecimal.valueOf(segundos / divisor).setScale(2, RoundingMode.HALF_UP);
    }

    /**
     * Agregados de tiempo de ciclo ya convertidos a horas.
     *
     * @param cantidad ciclos
     */
    public record ResumenTiempos(long cantidad, long lineas, long piezas,
                                 BigDecimal promedio, BigDecimal minimo, BigDecimal maximo) {
    }
}
