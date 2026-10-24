-- ==============================================================================
-- MIGRACIÓN V13: Lista de materiales (BOM multinivel), maestro de ítems y bodegas
--
-- Los tres vienen del ERP como CSV (bodegas, ítems y lista de materiales) y se recargan completos desde la app.
-- Al recargar, los ítems y bodegas se actualizan por código (sus id se conservan para el inventario futuro) y la
-- lista de materiales se reemplaza entera. Un pedido solo consulta la lista: no crea órdenes ni consume inventario.
--
-- Item.en_maestro = 0: el código aparece en la lista de materiales pero no en el maestro de ítems (sin descripción,
--   tipo ni unidad), o salió del maestro en una carga posterior.
-- Lista_Material.cantidad: unidades del hijo por cada unidad del padre.
-- ==============================================================================

CREATE TABLE Bodega (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    codigo              NVARCHAR(20) NOT NULL,
    descripcion         NVARCHAR(100) NOT NULL,
    descripcion_corta   NVARCHAR(50) NULL,
    fecha_creacion      DATETIME2 NOT NULL CONSTRAINT DF_Bodega_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion DATETIME2 NULL,
    CONSTRAINT UQ_Bodega_Codigo UNIQUE (codigo)
);

CREATE TABLE Item (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    codigo              NVARCHAR(50) NOT NULL,
    erp_id              INT NULL,
    descripcion         NVARCHAR(255) NULL,
    tipo                NVARCHAR(20) NULL,
    unidad              NVARCHAR(10) NULL,
    en_maestro          BIT NOT NULL CONSTRAINT DF_Item_EnMaestro DEFAULT 1,
    fecha_creacion      DATETIME2 NOT NULL CONSTRAINT DF_Item_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion DATETIME2 NULL,
    CONSTRAINT UQ_Item_Codigo UNIQUE (codigo),
    CONSTRAINT CK_Item_Tipo CHECK (tipo IN ('PRODUCTO_TERMINADO', 'SEMITERMINADO', 'MATERIA_PRIMA'))
);

CREATE TABLE Lista_Material (
    id            INT IDENTITY(1,1) PRIMARY KEY,
    item_padre_id INT NOT NULL,
    item_hijo_id  INT NOT NULL,
    secuencia     INT NOT NULL,
    cantidad      DECIMAL(18,6) NOT NULL,
    operacion_id  INT NULL,
    bodega_id     INT NULL,
    CONSTRAINT FK_LM_Padre  FOREIGN KEY (item_padre_id) REFERENCES Item(id),
    CONSTRAINT FK_LM_Hijo   FOREIGN KEY (item_hijo_id)  REFERENCES Item(id),
    CONSTRAINT FK_LM_Bodega FOREIGN KEY (bodega_id)     REFERENCES Bodega(id),
    CONSTRAINT UQ_LM_PadreHijo UNIQUE (item_padre_id, item_hijo_id),
    CONSTRAINT CK_LM_Cantidad CHECK (cantidad > 0),
    CONSTRAINT CK_LM_SinAutoReferencia CHECK (item_padre_id <> item_hijo_id)
);
CREATE INDEX IX_LM_Hijo ON Lista_Material(item_hijo_id);

-- Bitácora de cargas: cuándo y quién recargó, con lo que se leyó y los avisos (códigos sin maestro, etc.)
CREATE TABLE Carga_Lista_Materiales (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    fecha              DATETIME2 NOT NULL,
    usuario            NVARCHAR(150) NOT NULL,
    bodegas            INT NOT NULL,
    items              INT NOT NULL,
    relaciones         INT NOT NULL,
    items_sin_maestro  INT NOT NULL,
    avisos             NVARCHAR(MAX) NULL
);

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('material:ver',    'Consultar la lista de materiales de los productos y los materiales que requiere un pedido'),
    ('material:cargar', 'Recargar desde CSV la lista de materiales, el maestro de ítems y las bodegas');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR', 'GERENCIA') AND p.codigo = 'material:ver')
   OR (r.codigo IN ('ADMIN', 'COORDINADOR') AND p.codigo = 'material:cargar');
