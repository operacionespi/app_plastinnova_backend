-- ==============================================================================
-- MIGRACIÓN V5: Índices para los indicadores (KpiRepository) y el listado de pedidos.
-- Las fechas de negocio son fecha_creacion_fm (creación en FileMaker) y fecha_cierre (estado final).
-- ==============================================================================

-- Pedidos por ubicación y listado filtrado por fecha de creación
CREATE INDEX IX_Pedido_FechaCreacionFm ON Pedido(fecha_creacion_fm)
    INCLUDE (ciudad_envio_id, estado_actual_id, cliente_id, eliminado_en_fm, fecha_cierre);

-- Tiempo de entrega: pedidos cerrados en un rango
CREATE INDEX IX_Pedido_FechaCierre ON Pedido(fecha_cierre)
    INCLUDE (fecha_creacion_fm, eliminado_en_fm);

-- Carga por estado de línea: el conteo y las sumas se resuelven solo con el índice
DROP INDEX IX_PedidoDetalle_Estado ON Pedido_Detalle;
CREATE INDEX IX_PedidoDetalle_Estado ON Pedido_Detalle(estado_actual_id)
    INCLUDE (pedido_id, cantidad_solicitada, cantidad_procesada, tipo_alistamiento)
    WHERE eliminado_en_fm = 0;

-- Totales de líneas por pedido (listado) sin leer la tabla
DROP INDEX IX_PedidoDetalle_Pedido ON Pedido_Detalle;
CREATE INDEX IX_PedidoDetalle_Pedido ON Pedido_Detalle(pedido_id)
    INCLUDE (cantidad_solicitada, cantidad_procesada, eliminado_en_fm);

-- Tiempo de ciclo: entradas al estado destino en el rango (con la cantidad reportada)
DROP INDEX IX_HED_Estado_Fecha ON Historial_Estado_Detalle;
CREATE INDEX IX_HED_Estado_Fecha ON Historial_Estado_Detalle(estado_id, fecha_cambio)
    INCLUDE (pedido_detalle_id, cantidad_procesada);

-- Tiempo de ciclo: última entrada al estado origen de cada línea
CREATE INDEX IX_HED_Detalle_Estado_Fecha ON Historial_Estado_Detalle(pedido_detalle_id, estado_id, fecha_cambio);

-- Producción por operador: todos los movimientos de un rango
CREATE INDEX IX_HED_Fecha ON Historial_Estado_Detalle(fecha_cambio)
    INCLUDE (usuario_id, estado_id, pedido_detalle_id, cantidad_procesada, origen);
