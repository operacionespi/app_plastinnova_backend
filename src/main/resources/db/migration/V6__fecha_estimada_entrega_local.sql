-- ==============================================================================
-- MIGRACIÓN V6: Fecha estimada de entrega administrada en la aplicación
-- FileMaker solo envía las fechas de creación y modificación; la fecha de entrega la asigna un usuario
-- con el permiso pedido:fecha-entrega. Es un día calendario (sin hora): el pedido está atrasado desde el
-- día siguiente. La columna siempre estuvo vacía, así que el cambio de tipo no pierde datos.
-- ==============================================================================

DROP INDEX IX_Pedido_Activos_Estado_Entrega ON Pedido;

ALTER TABLE Pedido ALTER COLUMN fecha_estimada_entrega DATE NULL;

CREATE INDEX IX_Pedido_Activos_Estado_Entrega ON Pedido(estado_actual_id, fecha_estimada_entrega) WHERE eliminado_en_fm = 0;

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('pedido:fecha-entrega', 'Asignar o cambiar la fecha estimada de entrega de un pedido');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR') AND p.codigo = 'pedido:fecha-entrega';
