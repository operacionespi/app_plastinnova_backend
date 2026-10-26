-- ==============================================================================
-- MIGRACIÓN V17: Órdenes de producción y entregas (remisiones)
--
-- Orden_Produccion: el planificador la crea con un ítem y una cantidad, sin pedido obligatorio. Recorre las mismas
--   etapas que las líneas (catálogo Estado_Detalle y sus transiciones) y cada etapa reporta como máximo las piezas
--   de la anterior. Al llegar a la etapa de almacén (app.inventario.estado-linea-recepcion) lo producido entra al
--   slot de producción de la bodega destino, se consumen los componentes directos de la lista de materiales desde la
--   bodega que ella indica (si alguno no alcanza, el reporte se rechaza) y la orden queda TERMINADA.
-- Orden_Produccion_Linea: líneas de pedido que atiende la orden (cantidad asignada a cada una). Mientras la orden
--   está abierta (activa = 1) la línea sigue sus etapas y no se reporta por separado; lo producido queda comprometido
--   para ella hasta lo asignado. Una línea solo sigue a una orden a la vez.
-- Remision: entrega de un pedido. Cada línea sale de la bodega de producto terminado (primero lo comprometido para
--   ella, luego lo libre); la línea pasa sola a Entregado cuando completa lo solicitado. Anular la remisión devuelve
--   el producto a los mismos slots, lo vuelve a comprometer y regresa la línea a la etapa en que estaba.
-- Pedido_Detalle.cantidad_entregada: suma de las remisiones vigentes de la línea.
-- ==============================================================================

CREATE SEQUENCE Seq_Orden_Produccion AS BIGINT START WITH 1 INCREMENT BY 1;
CREATE SEQUENCE Seq_Remision AS BIGINT START WITH 1 INCREMENT BY 1;

CREATE TABLE Orden_Produccion (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    numero             NVARCHAR(20) NOT NULL,
    item_id            INT NOT NULL,
    bodega_destino_id  INT NOT NULL,
    cantidad           INT NOT NULL,
    estado             NVARCHAR(20) NOT NULL CONSTRAINT DF_OP_Estado DEFAULT 'ABIERTA',
    etapa_id           INT NOT NULL,
    -- Piezas reportadas por la última etapa: tope de la siguiente (igual que en las líneas)
    cantidad_procesada INT NOT NULL CONSTRAINT DF_OP_Procesada DEFAULT 0,
    avance_reportado   BIT NOT NULL CONSTRAINT DF_OP_Avance DEFAULT 0,
    fecha_requerida    DATE NULL,
    observaciones      NVARCHAR(500) NULL,
    usuario_creacion   NVARCHAR(150) NOT NULL,
    fecha_creacion     DATETIME2 NOT NULL,
    fecha_cierre       DATETIME2 NULL,
    version            BIGINT NOT NULL CONSTRAINT DF_OP_Version DEFAULT 0,
    CONSTRAINT UQ_OP_Numero UNIQUE (numero),
    CONSTRAINT FK_OP_Item   FOREIGN KEY (item_id)           REFERENCES Item(id),
    CONSTRAINT FK_OP_Bodega FOREIGN KEY (bodega_destino_id) REFERENCES Bodega(id),
    CONSTRAINT FK_OP_Etapa  FOREIGN KEY (etapa_id)          REFERENCES Estado_Detalle(id),
    CONSTRAINT CK_OP_Cantidad CHECK (cantidad > 0),
    CONSTRAINT CK_OP_Procesada CHECK (cantidad_procesada >= 0),
    CONSTRAINT CK_OP_Estado CHECK (estado IN ('ABIERTA', 'TERMINADA', 'ANULADA'))
);
CREATE INDEX IX_OP_EstadoFecha ON Orden_Produccion(estado, fecha_creacion DESC);
CREATE INDEX IX_OP_Item ON Orden_Produccion(item_id);

CREATE TABLE Orden_Produccion_Linea (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    orden_produccion_id BIGINT NOT NULL,
    pedido_detalle_id   BIGINT NOT NULL,
    cantidad            INT NOT NULL,
    activa              BIT NOT NULL CONSTRAINT DF_OPL_Activa DEFAULT 1,
    CONSTRAINT FK_OPL_Orden   FOREIGN KEY (orden_produccion_id) REFERENCES Orden_Produccion(id),
    CONSTRAINT FK_OPL_Detalle FOREIGN KEY (pedido_detalle_id)   REFERENCES Pedido_Detalle(id),
    CONSTRAINT UQ_OPL_OrdenDetalle UNIQUE (orden_produccion_id, pedido_detalle_id),
    CONSTRAINT CK_OPL_Cantidad CHECK (cantidad > 0)
);
CREATE UNIQUE INDEX UX_OPL_LineaActiva ON Orden_Produccion_Linea(pedido_detalle_id) WHERE activa = 1;

CREATE TABLE Historial_Orden_Produccion (
    id                  BIGINT IDENTITY(1,1) PRIMARY KEY,
    orden_produccion_id BIGINT NOT NULL,
    tipo                NVARCHAR(20) NOT NULL,
    etapa_anterior_id   INT NULL,
    etapa_id            INT NOT NULL,
    cantidad            INT NULL,
    usuario             NVARCHAR(150) NOT NULL,
    fecha               DATETIME2 NOT NULL,
    observaciones       NVARCHAR(500) NULL,
    CONSTRAINT FK_HOP_Orden    FOREIGN KEY (orden_produccion_id) REFERENCES Orden_Produccion(id),
    CONSTRAINT FK_HOP_Anterior FOREIGN KEY (etapa_anterior_id)   REFERENCES Estado_Detalle(id),
    CONSTRAINT FK_HOP_Etapa    FOREIGN KEY (etapa_id)            REFERENCES Estado_Detalle(id),
    CONSTRAINT CK_HOP_Tipo CHECK (tipo IN ('CREACION', 'CAMBIO_ETAPA', 'EDICION', 'ANULACION'))
);
CREATE INDEX IX_HOP_Orden ON Historial_Orden_Produccion(orden_produccion_id, fecha DESC);

CREATE TABLE Remision (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    numero            NVARCHAR(20) NOT NULL,
    pedido_id         BIGINT NOT NULL,
    estado            NVARCHAR(20) NOT NULL CONSTRAINT DF_Remision_Estado DEFAULT 'EMITIDA',
    transportador     NVARCHAR(150) NULL,
    placa             NVARCHAR(20) NULL,
    observaciones     NVARCHAR(500) NULL,
    usuario           NVARCHAR(150) NOT NULL,
    fecha             DATETIME2 NOT NULL,
    usuario_anulacion NVARCHAR(150) NULL,
    fecha_anulacion   DATETIME2 NULL,
    motivo_anulacion  NVARCHAR(500) NULL,
    CONSTRAINT UQ_Remision_Numero UNIQUE (numero),
    CONSTRAINT FK_Remision_Pedido FOREIGN KEY (pedido_id) REFERENCES Pedido(id),
    CONSTRAINT CK_Remision_Estado CHECK (estado IN ('EMITIDA', 'ANULADA'))
);
CREATE INDEX IX_Remision_Pedido ON Remision(pedido_id);
CREATE INDEX IX_Remision_Fecha ON Remision(fecha DESC);

CREATE TABLE Remision_Linea (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    remision_id       BIGINT NOT NULL,
    pedido_detalle_id BIGINT NOT NULL,
    cantidad          INT NOT NULL,
    CONSTRAINT FK_RL_Remision FOREIGN KEY (remision_id)       REFERENCES Remision(id),
    CONSTRAINT FK_RL_Detalle  FOREIGN KEY (pedido_detalle_id) REFERENCES Pedido_Detalle(id),
    CONSTRAINT UQ_RL_RemisionDetalle UNIQUE (remision_id, pedido_detalle_id),
    CONSTRAINT CK_RL_Cantidad CHECK (cantidad > 0)
);
CREATE INDEX IX_RL_Detalle ON Remision_Linea(pedido_detalle_id);

-- Movimientos originados por una orden (entrada de lo producido, consumo de componentes) o por una remisión
ALTER TABLE Movimiento_Inventario ADD
    orden_produccion_id BIGINT NULL CONSTRAINT FK_MI_Orden FOREIGN KEY REFERENCES Orden_Produccion(id),
    remision_id         BIGINT NULL CONSTRAINT FK_MI_Remision FOREIGN KEY REFERENCES Remision(id);

ALTER TABLE Pedido_Detalle ADD cantidad_entregada INT NOT NULL CONSTRAINT DF_PD_CantidadEntregada DEFAULT 0;
GO

CREATE INDEX IX_MI_Orden ON Movimiento_Inventario(orden_produccion_id) WHERE orden_produccion_id IS NOT NULL;
CREATE INDEX IX_MI_Remision ON Movimiento_Inventario(remision_id) WHERE remision_id IS NOT NULL;

-- Las líneas ya entregadas antes de las remisiones: lo que reportó su última etapa
UPDATE d SET d.cantidad_entregada = d.cantidad_procesada
FROM Pedido_Detalle d
JOIN Estado_Detalle e ON e.id = d.estado_actual_id
WHERE e.codigo = 'ENTREGADO';

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('produccion:ver',       'Consultar las órdenes de producción, sus materiales y su avance'),
    ('produccion:gestionar', 'Crear, editar y anular órdenes de producción y vincularles líneas de pedido'),
    ('entrega:ver',          'Consultar las remisiones de los pedidos'),
    ('entrega:gestionar',    'Emitir y anular remisiones: registran la salida del producto terminado');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR', 'GERENCIA', 'OPERADOR') AND p.codigo = 'produccion:ver')
   OR (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR') AND p.codigo = 'produccion:gestionar')
   OR (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR', 'GERENCIA') AND p.codigo = 'entrega:ver')
   OR (r.codigo IN ('ADMIN', 'COORDINADOR', 'SUPERVISOR') AND p.codigo = 'entrega:gestionar');
