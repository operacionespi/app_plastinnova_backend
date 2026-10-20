-- ==============================================================================
-- MIGRACIÓN V3: Ajustes para la sincronización con el layout plano de FileMaker "PedidosLineas-Api"
--
-- El layout entrega un registro por línea de pedido con los datos del pedido y del cliente repetidos:
--   LinPed_FechaCreacion, LinPed_Alistamiento, LinPed_Estado, LinPed_PosPedido, LinPed_CodPT,
--   LinPed_CantPedido, Ped_NumPedido, Ped_Cliente, Ped_Estado, Cliente_Ciudad, Cliente_Pais,
--   Cliente_Distribuidor
-- No trae departamento, nombre de producto ni precio. La zona se administra localmente en la app.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. GEOGRAFÍA: la ciudad cuelga directamente del país; el departamento queda opcional
--    (se puede asignar después desde la app).
-- ------------------------------------------------------------------------------
ALTER TABLE Ciudad ADD pais_id INT NULL;
GO

UPDATE c SET c.pais_id = d.pais_id
FROM Ciudad c JOIN Departamento d ON d.id = c.departamento_id;

ALTER TABLE Ciudad ALTER COLUMN pais_id INT NOT NULL;
ALTER TABLE Ciudad ADD CONSTRAINT FK_Ciudad_Pais FOREIGN KEY (pais_id) REFERENCES Pais(id);

DROP INDEX IX_Ciudad_Departamento ON Ciudad;
ALTER TABLE Ciudad ALTER COLUMN departamento_id INT NULL;
CREATE INDEX IX_Ciudad_Departamento ON Ciudad(departamento_id);

-- FileMaker envía país y ciudad como texto: el nombre es la llave (la intercalación de la BD ignora mayúsculas)
CREATE UNIQUE INDEX UX_Pais_Nombre ON Pais(nombre);
CREATE UNIQUE INDEX UX_Ciudad_Pais_Nombre ON Ciudad(pais_id, nombre);

-- La zona se define localmente, no viene de FileMaker
DROP INDEX UX_Zona_Filemaker ON Zona;
ALTER TABLE Zona DROP COLUMN filemaker_id;

-- ------------------------------------------------------------------------------
-- 2. CLIENTE: filemaker_id = razón social (Ped_Cliente); vendedor = Cliente_Distribuidor
-- ------------------------------------------------------------------------------
ALTER TABLE Cliente ADD vendedor NVARCHAR(150) NULL;
GO
CREATE INDEX IX_Cliente_Vendedor ON Cliente(vendedor) WHERE vendedor IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 3. LÍNEAS DEL PEDIDO
--    filemaker_id = recordId del registro en FileMaker
--    huella_fm    = SHA-256 de los campos del layout: detecta cambios también en los campos
--                   relacionados (cliente, ciudad), que no alteran el modId de la línea
-- ------------------------------------------------------------------------------
ALTER TABLE Pedido_Detalle ADD
    posicion          INT NULL,
    tipo_alistamiento NVARCHAR(20) NULL,
    fecha_creacion_fm DATETIME2 NULL,
    huella_fm         NVARCHAR(64) NULL;
GO
ALTER TABLE Pedido_Detalle ADD CONSTRAINT CK_PD_TipoAlistamiento
    CHECK (tipo_alistamiento IN ('NACIONAL', 'INTERNACIONAL'));
-- FileMaker no envía precio
ALTER TABLE Pedido_Detalle ADD CONSTRAINT DF_PD_PrecioUnitario DEFAULT 0 FOR precio_unitario;
CREATE INDEX IX_PedidoDetalle_TipoAlistamiento ON Pedido_Detalle(tipo_alistamiento) WHERE eliminado_en_fm = 0;

-- ------------------------------------------------------------------------------
-- 4. CONTROL DE SINCRONIZACIÓN: un único layout reemplaza las entidades separadas
-- ------------------------------------------------------------------------------
-- registros_eliminados: líneas que desaparecieron de FileMaker y se marcaron eliminado_en_fm = 1
-- avisos: situaciones que no impiden sincronizar (ej. un estado de FileMaker que no existe en el catálogo)
ALTER TABLE Sync_Log ADD
    registros_eliminados INT NOT NULL CONSTRAINT DF_SyncLog_Eliminados DEFAULT 0,
    avisos               NVARCHAR(MAX) NULL;

DELETE FROM Sync_Control;
INSERT INTO Sync_Control (entidad) VALUES ('PedidosLineas');
