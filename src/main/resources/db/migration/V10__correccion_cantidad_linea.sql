-- ==============================================================================
-- MIGRACIÓN V10: Las piezas avanzan de etapa en etapa y solo se corrigen con justificación
--
-- Cada etapa reporta como máximo las piezas que reportó la anterior (la primera, hasta la cantidad solicitada).
-- Si una etapa reportó de menos, un rol con más permisos corrige esa cantidad y justifica el porqué; la corrección
-- queda en el historial de la línea como un movimiento propio (no es un cambio de estado).
-- ==============================================================================

-- La línea ya tiene piezas reportadas por un usuario: desde entonces cantidad_procesada es el tope de la siguiente etapa
ALTER TABLE Pedido_Detalle ADD avance_reportado BIT NOT NULL CONSTRAINT DF_PD_AvanceReportado DEFAULT 0;

ALTER TABLE Historial_Estado_Detalle ADD
    tipo              NVARCHAR(30) NOT NULL CONSTRAINT DF_HED_Tipo DEFAULT 'CAMBIO_ESTADO'
                      CONSTRAINT CK_HED_Tipo CHECK (tipo IN ('CAMBIO_ESTADO', 'CORRECCION_CANTIDAD')),
    -- Solo en correcciones: piezas que había antes de corregir
    cantidad_anterior INT NULL;
GO

UPDATE d SET d.avance_reportado = 1
FROM Pedido_Detalle d
WHERE EXISTS (SELECT 1 FROM Historial_Estado_Detalle h WHERE h.pedido_detalle_id = d.id AND h.origen = 'USUARIO');

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('detalle:corregir-cantidad', 'Corregir las piezas reportadas en la etapa anterior de una línea, con justificación');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR') AND p.codigo = 'detalle:corregir-cantidad';
