-- ==============================================================================
-- MIGRACIÓN V18: Órdenes de producción con varios productos, rutas y entregas entre procesos
--
-- Ruta_Produccion: secuencia de procesos (etapas del catálogo de líneas) que recorre un ítem. Se asigna por ítem
--   (Item_Ruta); un ítem sin ruta recorre todas las etapas activas que no son inicial, final ni la de almacén. Toda
--   ruta termina en la etapa de almacén (app.inventario.estado-linea-recepcion), que no se guarda en la ruta.
-- Orden_Produccion_Producto: cada ítem que produce la orden, con su cantidad y su bodega destino. Copia la ruta del
--   ítem al crearse (Orden_Produccion_Etapa), así un cambio posterior de la ruta no mueve lo que está en proceso.
-- Orden_Produccion_Etapa: piezas del producto que están en cada proceso. Las piezas avanzan con entregas parciales.
-- Entrega_Proceso: un proceso entrega piezas al siguiente (AVANCE), las devuelve a uno anterior (DEVOLUCION) o se
--   dan de baja como desecho (BAJA). La entrega a la etapa de almacén consume los componentes y entra al inventario.
-- Orden_Produccion_Linea: ahora se vincula a un producto de la orden; cantidad_recibida = piezas ya comprometidas
--   para la línea al llegar al almacén.
-- ==============================================================================

CREATE TABLE Ruta_Produccion (
    id             INT IDENTITY(1,1) PRIMARY KEY,
    nombre         NVARCHAR(100) NOT NULL,
    descripcion    NVARCHAR(255) NULL,
    activa         BIT NOT NULL CONSTRAINT DF_Ruta_Activa DEFAULT 1,
    fecha_creacion DATETIME2 NOT NULL CONSTRAINT DF_Ruta_Fecha DEFAULT SYSUTCDATETIME(),
    version        BIGINT NOT NULL CONSTRAINT DF_Ruta_Version DEFAULT 0,
    CONSTRAINT UQ_Ruta_Nombre UNIQUE (nombre)
);

CREATE TABLE Ruta_Produccion_Etapa (
    id        INT IDENTITY(1,1) PRIMARY KEY,
    ruta_id   INT NOT NULL,
    etapa_id  INT NOT NULL,
    secuencia INT NOT NULL,
    CONSTRAINT FK_RPE_Ruta  FOREIGN KEY (ruta_id)  REFERENCES Ruta_Produccion(id),
    CONSTRAINT FK_RPE_Etapa FOREIGN KEY (etapa_id) REFERENCES Estado_Detalle(id),
    CONSTRAINT UQ_RPE_RutaEtapa UNIQUE (ruta_id, etapa_id),
    CONSTRAINT UQ_RPE_RutaSecuencia UNIQUE (ruta_id, secuencia)
);

CREATE TABLE Item_Ruta (
    item_id INT NOT NULL PRIMARY KEY,
    ruta_id INT NOT NULL,
    CONSTRAINT FK_IR_Item FOREIGN KEY (item_id) REFERENCES Item(id),
    CONSTRAINT FK_IR_Ruta FOREIGN KEY (ruta_id) REFERENCES Ruta_Produccion(id)
);
CREATE INDEX IX_IR_Ruta ON Item_Ruta(ruta_id);

CREATE TABLE Orden_Produccion_Producto (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    orden_produccion_id BIGINT NOT NULL,
    item_id             INT NOT NULL,
    bodega_destino_id   INT NOT NULL,
    -- Ruta con que se creó (solo informativa: las etapas quedan copiadas en Orden_Produccion_Etapa)
    ruta_id             INT NULL,
    cantidad            INT NOT NULL,
    cantidad_recibida   INT NOT NULL CONSTRAINT DF_OPP_Recibida DEFAULT 0,
    cantidad_baja       INT NOT NULL CONSTRAINT DF_OPP_Baja DEFAULT 0,
    version             BIGINT NOT NULL CONSTRAINT DF_OPP_Version DEFAULT 0,
    CONSTRAINT FK_OPP_Orden  FOREIGN KEY (orden_produccion_id) REFERENCES Orden_Produccion(id),
    CONSTRAINT FK_OPP_Item   FOREIGN KEY (item_id)             REFERENCES Item(id),
    CONSTRAINT FK_OPP_Bodega FOREIGN KEY (bodega_destino_id)   REFERENCES Bodega(id),
    CONSTRAINT FK_OPP_Ruta   FOREIGN KEY (ruta_id)             REFERENCES Ruta_Produccion(id),
    CONSTRAINT UQ_OPP_OrdenItem UNIQUE (orden_produccion_id, item_id),
    CONSTRAINT CK_OPP_Cantidad CHECK (cantidad > 0 AND cantidad_recibida >= 0 AND cantidad_baja >= 0
                                      AND cantidad_recibida + cantidad_baja <= cantidad)
);
CREATE INDEX IX_OPP_Item ON Orden_Produccion_Producto(item_id);

CREATE TABLE Orden_Produccion_Etapa (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    orden_producto_id   BIGINT NOT NULL,
    etapa_id            INT NOT NULL,
    secuencia           INT NOT NULL,
    cantidad            INT NOT NULL CONSTRAINT DF_OPE_Cantidad DEFAULT 0,
    CONSTRAINT FK_OPE_Producto FOREIGN KEY (orden_producto_id) REFERENCES Orden_Produccion_Producto(id),
    CONSTRAINT FK_OPE_Etapa    FOREIGN KEY (etapa_id)          REFERENCES Estado_Detalle(id),
    CONSTRAINT UQ_OPE_ProductoEtapa UNIQUE (orden_producto_id, etapa_id),
    CONSTRAINT UQ_OPE_ProductoSecuencia UNIQUE (orden_producto_id, secuencia),
    CONSTRAINT CK_OPE_Cantidad CHECK (cantidad >= 0)
);
CREATE INDEX IX_OPE_EtapaConPiezas ON Orden_Produccion_Etapa(etapa_id) WHERE cantidad > 0;

CREATE TABLE Entrega_Proceso (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    orden_producto_id BIGINT NOT NULL,
    tipo              NVARCHAR(20) NOT NULL,
    etapa_origen_id   INT NOT NULL,
    -- NULL en las bajas
    etapa_destino_id  INT NULL,
    cantidad          INT NOT NULL,
    usuario           NVARCHAR(150) NOT NULL,
    fecha             DATETIME2 NOT NULL,
    observaciones     NVARCHAR(500) NULL,
    CONSTRAINT FK_EP_Producto FOREIGN KEY (orden_producto_id) REFERENCES Orden_Produccion_Producto(id),
    CONSTRAINT FK_EP_Origen   FOREIGN KEY (etapa_origen_id)   REFERENCES Estado_Detalle(id),
    CONSTRAINT FK_EP_Destino  FOREIGN KEY (etapa_destino_id)  REFERENCES Estado_Detalle(id),
    CONSTRAINT CK_EP_Tipo CHECK (tipo IN ('AVANCE', 'DEVOLUCION', 'BAJA')),
    CONSTRAINT CK_EP_Destino CHECK ((tipo = 'BAJA' AND etapa_destino_id IS NULL)
                                    OR (tipo <> 'BAJA' AND etapa_destino_id IS NOT NULL)),
    CONSTRAINT CK_EP_Cantidad CHECK (cantidad > 0)
);
CREATE INDEX IX_EP_Producto ON Entrega_Proceso(orden_producto_id, fecha DESC);

ALTER TABLE Orden_Produccion_Linea ADD
    orden_producto_id BIGINT NULL CONSTRAINT FK_OPL_Producto FOREIGN KEY REFERENCES Orden_Produccion_Producto(id),
    cantidad_recibida INT NOT NULL CONSTRAINT DF_OPL_Recibida DEFAULT 0;

-- El historial de la orden ya no lleva etapa (las etapas son por producto y quedan en Entrega_Proceso)
ALTER TABLE Historial_Orden_Produccion ALTER COLUMN etapa_id INT NULL;
GO

-- ------------------------------------------------------------------------------
-- Órdenes existentes (un solo ítem): pasan a un producto con las etapas por defecto
-- ------------------------------------------------------------------------------
INSERT INTO Orden_Produccion_Producto (orden_produccion_id, item_id, bodega_destino_id, cantidad, cantidad_recibida)
SELECT o.id, o.item_id, o.bodega_destino_id, o.cantidad,
       CASE WHEN o.estado = 'TERMINADA' THEN o.cantidad_procesada ELSE 0 END
FROM Orden_Produccion o;

INSERT INTO Orden_Produccion_Etapa (orden_producto_id, etapa_id, secuencia, cantidad)
SELECT p.id, e.id, ROW_NUMBER() OVER (PARTITION BY p.id ORDER BY e.orden_logico, e.id), 0
FROM Orden_Produccion_Producto p
CROSS JOIN Estado_Detalle e
WHERE e.activo = 1 AND e.es_inicial = 0 AND e.es_final = 0 AND e.codigo <> 'ALMACEN1';

-- Las abiertas: todas sus piezas en la etapa en que iban (o en la primera, si iban en la inicial)
UPDATE pe SET pe.cantidad = o.cantidad
FROM Orden_Produccion_Etapa pe
JOIN Orden_Produccion_Producto p ON p.id = pe.orden_producto_id
JOIN Orden_Produccion o ON o.id = p.orden_produccion_id
WHERE o.estado = 'ABIERTA'
  AND (pe.etapa_id = o.etapa_id
       OR (pe.secuencia = 1 AND NOT EXISTS (SELECT 1 FROM Orden_Produccion_Etapa x
                                            WHERE x.orden_producto_id = p.id AND x.etapa_id = o.etapa_id)));

UPDATE l SET l.orden_producto_id = p.id,
             l.cantidad_recibida = CASE WHEN o.estado = 'TERMINADA' THEN l.cantidad ELSE 0 END
FROM Orden_Produccion_Linea l
JOIN Orden_Produccion o ON o.id = l.orden_produccion_id
JOIN Orden_Produccion_Producto p ON p.orden_produccion_id = o.id;
GO

ALTER TABLE Orden_Produccion_Linea ALTER COLUMN orden_producto_id BIGINT NOT NULL;
CREATE INDEX IX_OPL_Producto ON Orden_Produccion_Linea(orden_producto_id);

DROP INDEX IX_OP_Item ON Orden_Produccion;
ALTER TABLE Orden_Produccion DROP CONSTRAINT FK_OP_Item, FK_OP_Bodega, FK_OP_Etapa, CK_OP_Cantidad, CK_OP_Procesada,
    DF_OP_Procesada, DF_OP_Avance;
ALTER TABLE Orden_Produccion DROP COLUMN item_id, bodega_destino_id, cantidad, etapa_id, cantidad_procesada,
    avance_reportado;
