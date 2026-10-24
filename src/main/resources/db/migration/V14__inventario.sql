-- ==============================================================================
-- MIGRACIÓN V14: Inventario por bodega y slot
--
-- Slot: ubicación dentro de una bodega, administrada desde la app. Un slot con existencias o movimientos no se
--   elimina: se desactiva.
-- Existencia: saldo actual de un ítem en un slot. Solo cambia con un movimiento (en la misma transacción), así que
--   siempre coincide con la suma de los movimientos. Nunca queda negativa.
-- Movimiento_Inventario: bitácora inmutable. Cada movimiento resta de slot_origen y/o suma a slot_destino:
--   ENTRADA        → destino                     SALIDA → origen
--   TRASLADO       → origen y destino (otro slot, de la misma u otra bodega)
--   AJUSTE         → conteo físico: cantidad_contada es lo contado y cantidad la diferencia con el saldo
--                    (sobrante → destino, faltante → origen)
--   CARGA_INICIAL  → igual que el ajuste, desde el CSV de existencias
-- El requerimiento neto de un pedido descuenta las existencias de la bodega que indica la lista de materiales; no
-- reserva ni consume inventario.
-- ==============================================================================

CREATE TABLE Slot (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    bodega_id           INT NOT NULL,
    codigo              NVARCHAR(30) NOT NULL,
    descripcion         NVARCHAR(100) NULL,
    activo              BIT NOT NULL CONSTRAINT DF_Slot_Activo DEFAULT 1,
    fecha_creacion      DATETIME2 NOT NULL CONSTRAINT DF_Slot_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion DATETIME2 NULL,
    CONSTRAINT FK_Slot_Bodega FOREIGN KEY (bodega_id) REFERENCES Bodega(id),
    CONSTRAINT UQ_Slot_BodegaCodigo UNIQUE (bodega_id, codigo)
);

CREATE TABLE Existencia (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    item_id             INT NOT NULL,
    slot_id             INT NOT NULL,
    cantidad            DECIMAL(18,6) NOT NULL,
    fecha_actualizacion DATETIME2 NOT NULL,
    CONSTRAINT FK_Existencia_Item FOREIGN KEY (item_id) REFERENCES Item(id),
    CONSTRAINT FK_Existencia_Slot FOREIGN KEY (slot_id) REFERENCES Slot(id),
    CONSTRAINT UQ_Existencia_ItemSlot UNIQUE (item_id, slot_id),
    CONSTRAINT CK_Existencia_NoNegativa CHECK (cantidad >= 0)
);
CREATE INDEX IX_Existencia_Slot ON Existencia(slot_id);

CREATE TABLE Movimiento_Inventario (
    id               BIGINT IDENTITY(1,1) PRIMARY KEY,
    tipo             NVARCHAR(20) NOT NULL,
    item_id          INT NOT NULL,
    slot_origen_id   INT NULL,
    slot_destino_id  INT NULL,
    cantidad         DECIMAL(18,6) NOT NULL,
    cantidad_contada DECIMAL(18,6) NULL,
    motivo           NVARCHAR(100) NULL,
    observaciones    NVARCHAR(500) NULL,
    usuario          NVARCHAR(150) NOT NULL,
    fecha            DATETIME2 NOT NULL,
    CONSTRAINT FK_MI_Item    FOREIGN KEY (item_id)         REFERENCES Item(id),
    CONSTRAINT FK_MI_Origen  FOREIGN KEY (slot_origen_id)  REFERENCES Slot(id),
    CONSTRAINT FK_MI_Destino FOREIGN KEY (slot_destino_id) REFERENCES Slot(id),
    CONSTRAINT CK_MI_Tipo CHECK (tipo IN ('ENTRADA', 'SALIDA', 'TRASLADO', 'AJUSTE', 'CARGA_INICIAL')),
    CONSTRAINT CK_MI_Cantidad CHECK (cantidad >= 0),
    CONSTRAINT CK_MI_Slots CHECK (
        (tipo = 'ENTRADA'  AND slot_origen_id IS NULL AND slot_destino_id IS NOT NULL) OR
        (tipo = 'SALIDA'   AND slot_origen_id IS NOT NULL AND slot_destino_id IS NULL) OR
        (tipo = 'TRASLADO' AND slot_origen_id IS NOT NULL AND slot_destino_id IS NOT NULL
                           AND slot_origen_id <> slot_destino_id) OR
        (tipo IN ('AJUSTE', 'CARGA_INICIAL') AND cantidad_contada IS NOT NULL
                           AND (slot_origen_id IS NULL OR slot_destino_id IS NULL)
                           AND COALESCE(slot_origen_id, slot_destino_id) IS NOT NULL))
);
CREATE INDEX IX_MI_ItemFecha ON Movimiento_Inventario(item_id, fecha DESC);
CREATE INDEX IX_MI_Fecha ON Movimiento_Inventario(fecha DESC);

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('inventario:ver',       'Consultar existencias, movimientos y el requerimiento neto de los pedidos'),
    ('inventario:mover',     'Registrar entradas, salidas, traslados y ajustes por conteo'),
    ('inventario:gestionar', 'Administrar los slots de las bodegas y hacer la carga inicial de existencias');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR', 'GERENCIA') AND p.codigo = 'inventario:ver')
   OR (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR') AND p.codigo = 'inventario:mover')
   OR (r.codigo IN ('ADMIN', 'COORDINADOR') AND p.codigo = 'inventario:gestionar');
