-- ==============================================================================
-- MIGRACIÓN V9: Rol de gerencia
-- Consulta pedidos y tableros; no modifica estados, fechas, catálogos ni la sincronización.
-- ==============================================================================

INSERT INTO Rol (codigo, descripcion) VALUES
    ('GERENCIA', 'Gerencia: consulta pedidos e indicadores (solo lectura)');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo = 'GERENCIA' AND p.codigo IN ('pedido:ver', 'kpi:ver');
