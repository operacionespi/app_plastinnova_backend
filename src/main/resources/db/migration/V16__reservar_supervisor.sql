-- ==============================================================================
-- MIGRACIÓN V16: El supervisor también compromete y libera producto terminado para los pedidos
-- ==============================================================================

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo = 'SUPERVISOR' AND p.codigo = 'inventario:reservar';
