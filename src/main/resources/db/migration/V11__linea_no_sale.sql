-- ==============================================================================
-- MIGRACIÓN V11: Líneas que no salen y pedidos anulados
--
-- "No sale": la línea no se despacha (no hay disponibilidad del producto). Solo el supervisor la marca y la puede
-- devolver a la etapa en que estaba, siempre con justificación; por eso no tiene transiciones en el catálogo
-- (son acciones propias, no pasos del proceso).
-- Un pedido está completo (el "in full" del OTIF) cuando todas sus líneas están en Entregado o No sale.
-- Si TODAS quedan en No sale, el pedido pasa a "Anulado" (no disponibilidad de producto); si se devuelve una
-- línea, el pedido se reabre en el estado que tenía. Los pedidos anulados no cuentan en los indicadores de entrega.
-- ==============================================================================

INSERT INTO Estado_Detalle (codigo, nombre, descripcion, orden_logico, color, es_inicial, es_final) VALUES
    ('NO_SALE', 'No sale', 'La línea no se despacha por no disponibilidad del producto', 80, '#546E7A', 0, 1);

INSERT INTO Estado_Pedido (codigo, nombre, descripcion, orden_logico, color, es_inicial, es_final) VALUES
    ('ANULADO', 'Anulado', 'Todas sus líneas quedaron en No sale: no disponibilidad de producto', 60, '#E53935', 0, 1);

-- Marcar y devolver quedan en el historial de la línea como movimientos propios (no son producción)
ALTER TABLE Historial_Estado_Detalle DROP CONSTRAINT CK_HED_Tipo;
ALTER TABLE Historial_Estado_Detalle ADD CONSTRAINT CK_HED_Tipo
    CHECK (tipo IN ('CAMBIO_ESTADO', 'CORRECCION_CANTIDAD', 'NO_SALE', 'DEVOLUCION'));

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('detalle:no-sale', 'Marcar una línea como No sale o devolverla a su etapa, con justificación');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo = 'SUPERVISOR' AND p.codigo = 'detalle:no-sale';
