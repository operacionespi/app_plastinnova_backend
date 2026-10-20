-- ==============================================================================
-- PROYECTO: SISTEMA DE GESTIÓN Y TRAZABILIDAD DE PEDIDOS (INTEGRACIÓN FILEMAKER)
-- MOTOR: Microsoft SQL Server
-- MIGRACIÓN V1: Esquema inicial
--
-- CONVENCIONES:
--   * Todas las fechas se almacenan en UTC (DATETIME2 + SYSUTCDATETIME()).
--     Las fechas que llegan de FileMaker (hora Colombia) se convierten a UTC en la sincronización.
--   * filemaker_id = llave del registro en FileMaker. Cuando la columna admite NULL, la unicidad
--     se garantiza con un índice único FILTRADO (SQL Server solo admite un NULL en un UNIQUE normal).
--   * fm_mod_id / fecha_modificacion_fm = control de sincronización incremental.
--   * version = bloqueo optimista (JPA @Version) en las tablas operativas.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. SEGURIDAD (RBAC)
-- ------------------------------------------------------------------------------

CREATE TABLE Usuario (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    email               NVARCHAR(150) NOT NULL,
    password_hash       NVARCHAR(255) NOT NULL,
    nombre_completo     NVARCHAR(150) NOT NULL,
    activo              BIT NOT NULL CONSTRAINT DF_Usuario_Activo DEFAULT 1,
    fecha_creacion      DATETIME2 NOT NULL CONSTRAINT DF_Usuario_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion DATETIME2 NULL,
    CONSTRAINT UQ_Usuario_Email UNIQUE (email)
);

CREATE TABLE Rol (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    codigo      NVARCHAR(50) NOT NULL,
    descripcion NVARCHAR(255) NULL,
    CONSTRAINT UQ_Rol_Codigo UNIQUE (codigo)
);

CREATE TABLE Permiso (
    id          INT IDENTITY(1,1) PRIMARY KEY,
    codigo      NVARCHAR(100) NOT NULL,
    descripcion NVARCHAR(255) NULL,
    CONSTRAINT UQ_Permiso_Codigo UNIQUE (codigo)
);

CREATE TABLE Usuario_Rol (
    usuario_id INT NOT NULL,
    rol_id     INT NOT NULL,
    CONSTRAINT PK_Usuario_Rol PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT FK_UR_Usuario FOREIGN KEY (usuario_id) REFERENCES Usuario(id) ON DELETE CASCADE,
    CONSTRAINT FK_UR_Rol     FOREIGN KEY (rol_id)     REFERENCES Rol(id)     ON DELETE CASCADE
);

CREATE TABLE Rol_Permiso (
    rol_id     INT NOT NULL,
    permiso_id INT NOT NULL,
    CONSTRAINT PK_Rol_Permiso PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT FK_RP_Rol     FOREIGN KEY (rol_id)     REFERENCES Rol(id)     ON DELETE CASCADE,
    CONSTRAINT FK_RP_Permiso FOREIGN KEY (permiso_id) REFERENCES Permiso(id) ON DELETE CASCADE
);

-- ------------------------------------------------------------------------------
-- 2. SINCRONIZACIÓN CON FILEMAKER
-- ------------------------------------------------------------------------------

-- Bitácora de cada ejecución
CREATE TABLE Sync_Log (
    id                     BIGINT IDENTITY(1,1) PRIMARY KEY,
    entidad                NVARCHAR(100) NOT NULL,
    fecha_inicio           DATETIME2 NOT NULL CONSTRAINT DF_SyncLog_FechaInicio DEFAULT SYSUTCDATETIME(),
    fecha_fin              DATETIME2 NULL,
    registros_leidos       INT NOT NULL CONSTRAINT DF_SyncLog_Leidos DEFAULT 0,
    registros_insertados   INT NOT NULL CONSTRAINT DF_SyncLog_Insertados DEFAULT 0,
    registros_actualizados INT NOT NULL CONSTRAINT DF_SyncLog_Actualizados DEFAULT 0,
    registros_con_error    INT NOT NULL CONSTRAINT DF_SyncLog_ConError DEFAULT 0,
    estado                 NVARCHAR(20) NOT NULL,
    disparado_por          NVARCHAR(150) NULL, -- NULL = scheduler; email = ejecución manual
    mensaje_error          NVARCHAR(MAX) NULL,
    CONSTRAINT CK_SyncLog_Estado CHECK (estado IN ('EN_PROCESO', 'EXITO', 'PARCIAL', 'ERROR'))
);
CREATE INDEX IX_SyncLog_Entidad_Fecha ON Sync_Log(entidad, fecha_inicio DESC);

-- Marca de agua por entidad: desde dónde continuar la siguiente sincronización incremental
CREATE TABLE Sync_Control (
    entidad                       NVARCHAR(100) NOT NULL PRIMARY KEY,
    ultima_fecha_modificacion_fm  DATETIME2 NULL,
    ultima_ejecucion_exitosa      DATETIME2 NULL
);

-- ------------------------------------------------------------------------------
-- 3. GEOGRAFÍA (sincronizada desde FileMaker, base de los indicadores por territorio)
-- ------------------------------------------------------------------------------

CREATE TABLE Pais (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id          NVARCHAR(100) NULL,
    nombre                NVARCHAR(100) NOT NULL,
    codigo_iso            NVARCHAR(10) NULL,
    fecha_modificacion_fm DATETIME2 NULL
);
CREATE UNIQUE INDEX UX_Pais_Filemaker ON Pais(filemaker_id) WHERE filemaker_id IS NOT NULL;

CREATE TABLE Departamento (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id          NVARCHAR(100) NULL,
    pais_id               INT NOT NULL,
    nombre                NVARCHAR(150) NOT NULL,
    fecha_modificacion_fm DATETIME2 NULL,
    CONSTRAINT FK_Departamento_Pais FOREIGN KEY (pais_id) REFERENCES Pais(id)
);
CREATE UNIQUE INDEX UX_Departamento_Filemaker ON Departamento(filemaker_id) WHERE filemaker_id IS NOT NULL;
CREATE INDEX IX_Departamento_Pais ON Departamento(pais_id);

-- Zona comercial/logística: agrupa ciudades para indicadores (puede venir de FileMaker o definirse localmente)
CREATE TABLE Zona (
    id           INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NULL,
    codigo       NVARCHAR(50) NOT NULL,
    nombre       NVARCHAR(100) NOT NULL,
    activo       BIT NOT NULL CONSTRAINT DF_Zona_Activo DEFAULT 1,
    CONSTRAINT UQ_Zona_Codigo UNIQUE (codigo)
);
CREATE UNIQUE INDEX UX_Zona_Filemaker ON Zona(filemaker_id) WHERE filemaker_id IS NOT NULL;

CREATE TABLE Ciudad (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id          NVARCHAR(100) NULL,
    departamento_id       INT NOT NULL,
    zona_id               INT NULL,
    nombre                NVARCHAR(150) NOT NULL,
    fecha_modificacion_fm DATETIME2 NULL,
    CONSTRAINT FK_Ciudad_Departamento FOREIGN KEY (departamento_id) REFERENCES Departamento(id),
    CONSTRAINT FK_Ciudad_Zona         FOREIGN KEY (zona_id)         REFERENCES Zona(id)
);
CREATE UNIQUE INDEX UX_Ciudad_Filemaker ON Ciudad(filemaker_id) WHERE filemaker_id IS NOT NULL;
CREATE INDEX IX_Ciudad_Departamento ON Ciudad(departamento_id);
CREATE INDEX IX_Ciudad_Zona ON Ciudad(zona_id);

-- ------------------------------------------------------------------------------
-- 4. MAESTROS: CLIENTES Y PRODUCTOS
-- ------------------------------------------------------------------------------

CREATE TABLE Cliente (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id          NVARCHAR(100) NOT NULL,
    documento_identidad   NVARCHAR(50) NULL,  -- No único: FileMaker puede traer duplicados o vacíos
    razon_social          NVARCHAR(255) NOT NULL,
    email                 NVARCHAR(150) NULL,
    telefono              NVARCHAR(50) NULL,
    ciudad_id             INT NULL,
    activo                BIT NOT NULL CONSTRAINT DF_Cliente_Activo DEFAULT 1,
    fm_mod_id             INT NULL,
    fecha_modificacion_fm DATETIME2 NULL,
    fecha_creacion        DATETIME2 NOT NULL CONSTRAINT DF_Cliente_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion   DATETIME2 NULL,
    CONSTRAINT UQ_Cliente_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_Cliente_Ciudad FOREIGN KEY (ciudad_id) REFERENCES Ciudad(id)
);
CREATE INDEX IX_Cliente_Documento ON Cliente(documento_identidad);
CREATE INDEX IX_Cliente_RazonSocial ON Cliente(razon_social);

CREATE TABLE Producto (
    id                    INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id          NVARCHAR(100) NOT NULL,
    sku                   NVARCHAR(50) NULL,
    nombre                NVARCHAR(255) NOT NULL,
    precio_base           DECIMAL(18,2) NOT NULL CONSTRAINT DF_Producto_Precio DEFAULT 0,
    activo                BIT NOT NULL CONSTRAINT DF_Producto_Activo DEFAULT 1,
    fm_mod_id             INT NULL,
    fecha_modificacion_fm DATETIME2 NULL,
    fecha_creacion        DATETIME2 NOT NULL CONSTRAINT DF_Producto_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion   DATETIME2 NULL,
    CONSTRAINT UQ_Producto_Filemaker UNIQUE (filemaker_id)
);
CREATE UNIQUE INDEX UX_Producto_Sku ON Producto(sku) WHERE sku IS NOT NULL;

-- ------------------------------------------------------------------------------
-- 5. CATÁLOGOS DE ESTADOS (configurables desde la aplicación)
--    Un estado usado en pedidos o historial no se elimina físicamente: se desactiva (activo = 0).
-- ------------------------------------------------------------------------------

CREATE TABLE Estado_Pedido (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    codigo              NVARCHAR(50) NOT NULL,
    nombre              NVARCHAR(100) NOT NULL,
    descripcion         NVARCHAR(255) NULL,
    orden_logico        INT NOT NULL,
    color               NVARCHAR(7) NULL,  -- Hex para la UI, ej: '#1E88E5'
    es_inicial          BIT NOT NULL CONSTRAINT DF_EstadoPedido_EsInicial DEFAULT 0,
    es_final            BIT NOT NULL CONSTRAINT DF_EstadoPedido_EsFinal DEFAULT 0,
    activo              BIT NOT NULL CONSTRAINT DF_EstadoPedido_Activo DEFAULT 1,
    fecha_creacion      DATETIME2 NOT NULL CONSTRAINT DF_EstadoPedido_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion DATETIME2 NULL,
    CONSTRAINT UQ_EstadoPedido_Codigo UNIQUE (codigo)
);
-- Solo puede existir un estado inicial activo (el que recibe los pedidos nuevos de FileMaker)
CREATE UNIQUE INDEX UX_EstadoPedido_Inicial ON Estado_Pedido(es_inicial) WHERE es_inicial = 1 AND activo = 1;

CREATE TABLE Estado_Detalle (
    id                  INT IDENTITY(1,1) PRIMARY KEY,
    codigo              NVARCHAR(50) NOT NULL,
    nombre              NVARCHAR(100) NOT NULL,
    descripcion         NVARCHAR(255) NULL,
    orden_logico        INT NOT NULL,
    color               NVARCHAR(7) NULL,
    es_inicial          BIT NOT NULL CONSTRAINT DF_EstadoDetalle_EsInicial DEFAULT 0,
    es_final            BIT NOT NULL CONSTRAINT DF_EstadoDetalle_EsFinal DEFAULT 0,
    activo              BIT NOT NULL CONSTRAINT DF_EstadoDetalle_Activo DEFAULT 1,
    fecha_creacion      DATETIME2 NOT NULL CONSTRAINT DF_EstadoDetalle_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion DATETIME2 NULL,
    CONSTRAINT UQ_EstadoDetalle_Codigo UNIQUE (codigo)
);
CREATE UNIQUE INDEX UX_EstadoDetalle_Inicial ON Estado_Detalle(es_inicial) WHERE es_inicial = 1 AND activo = 1;

-- Transiciones permitidas (origen -> destino). Se administran desde la aplicación.
CREATE TABLE Transicion_Estado_Pedido (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    estado_origen_id  INT NOT NULL,
    estado_destino_id INT NOT NULL,
    CONSTRAINT UQ_TransicionPedido UNIQUE (estado_origen_id, estado_destino_id),
    CONSTRAINT CK_TransicionPedido_Distintos CHECK (estado_origen_id <> estado_destino_id),
    CONSTRAINT FK_TEP_Origen  FOREIGN KEY (estado_origen_id)  REFERENCES Estado_Pedido(id),
    CONSTRAINT FK_TEP_Destino FOREIGN KEY (estado_destino_id) REFERENCES Estado_Pedido(id)
);

CREATE TABLE Transicion_Estado_Detalle (
    id                INT IDENTITY(1,1) PRIMARY KEY,
    estado_origen_id  INT NOT NULL,
    estado_destino_id INT NOT NULL,
    CONSTRAINT UQ_TransicionDetalle UNIQUE (estado_origen_id, estado_destino_id),
    CONSTRAINT CK_TransicionDetalle_Distintos CHECK (estado_origen_id <> estado_destino_id),
    CONSTRAINT FK_TED_Origen  FOREIGN KEY (estado_origen_id)  REFERENCES Estado_Detalle(id),
    CONSTRAINT FK_TED_Destino FOREIGN KEY (estado_destino_id) REFERENCES Estado_Detalle(id)
);

-- ------------------------------------------------------------------------------
-- 6. TRANSACCIONAL: PEDIDOS
-- ------------------------------------------------------------------------------

CREATE TABLE Pedido (
    id                     BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id           NVARCHAR(100) NOT NULL,
    numero_pedido          NVARCHAR(50) NULL,      -- Consecutivo visible para el usuario
    cliente_id             BIGINT NOT NULL,
    -- Dirección como texto libre (así viene de FileMaker) + ciudad normalizada para indicadores.
    -- Zona, departamento y país se obtienen a través de la ciudad.
    direccion_envio        NVARCHAR(500) NULL,
    ciudad_envio_id        INT NULL,
    usuario_asignado_id    INT NULL,
    estado_actual_id       INT NOT NULL,
    fecha_creacion_fm      DATETIME2 NOT NULL,
    fecha_ingreso          DATETIME2 NOT NULL CONSTRAINT DF_Pedido_FechaIngreso DEFAULT SYSUTCDATETIME(),
    fecha_estimada_entrega DATETIME2 NULL,
    fecha_cierre           DATETIME2 NULL,         -- Se registra al llegar a un estado final (base de OTIF)
    total_pedido           DECIMAL(18,2) NOT NULL CONSTRAINT DF_Pedido_Total DEFAULT 0,
    observaciones          NVARCHAR(1000) NULL,
    eliminado_en_fm        BIT NOT NULL CONSTRAINT DF_Pedido_Eliminado DEFAULT 0,
    fm_mod_id              INT NULL,
    fecha_modificacion_fm  DATETIME2 NULL,
    version                BIGINT NOT NULL CONSTRAINT DF_Pedido_Version DEFAULT 0,
    fecha_actualizacion    DATETIME2 NULL,
    CONSTRAINT UQ_Pedido_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_Pedido_Cliente  FOREIGN KEY (cliente_id)          REFERENCES Cliente(id),
    CONSTRAINT FK_Pedido_Ciudad   FOREIGN KEY (ciudad_envio_id)     REFERENCES Ciudad(id),
    CONSTRAINT FK_Pedido_Usuario  FOREIGN KEY (usuario_asignado_id) REFERENCES Usuario(id),
    CONSTRAINT FK_Pedido_Estado   FOREIGN KEY (estado_actual_id)    REFERENCES Estado_Pedido(id)
);
CREATE INDEX IX_Pedido_Cliente ON Pedido(cliente_id);
CREATE INDEX IX_Pedido_Ciudad ON Pedido(ciudad_envio_id);
CREATE INDEX IX_Pedido_UsuarioAsignado ON Pedido(usuario_asignado_id);
CREATE INDEX IX_Pedido_NumeroPedido ON Pedido(numero_pedido);
-- Tablero operativo: pedidos activos por estado y fecha de entrega (atrasados / en curso)
CREATE INDEX IX_Pedido_Activos_Estado_Entrega ON Pedido(estado_actual_id, fecha_estimada_entrega) WHERE eliminado_en_fm = 0;
-- Búsquedas y KPIs por rango de ingreso
CREATE INDEX IX_Pedido_FechaIngreso ON Pedido(fecha_ingreso) INCLUDE (estado_actual_id, ciudad_envio_id, cliente_id);

CREATE TABLE Pedido_Detalle (
    id                    BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id          NVARCHAR(100) NULL,
    pedido_id             BIGINT NOT NULL,
    producto_id           INT NOT NULL,
    estado_actual_id      INT NOT NULL,
    cantidad_solicitada   INT NOT NULL,
    cantidad_procesada    INT NOT NULL CONSTRAINT DF_PD_CantProcesada DEFAULT 0,
    precio_unitario       DECIMAL(18,2) NOT NULL, -- Snapshot del precio al momento del pedido
    eliminado_en_fm       BIT NOT NULL CONSTRAINT DF_PD_Eliminado DEFAULT 0,
    fm_mod_id             INT NULL,
    fecha_modificacion_fm DATETIME2 NULL,
    version               BIGINT NOT NULL CONSTRAINT DF_PD_Version DEFAULT 0,
    fecha_creacion        DATETIME2 NOT NULL CONSTRAINT DF_PD_FechaCreacion DEFAULT SYSUTCDATETIME(),
    fecha_actualizacion   DATETIME2 NULL,
    CONSTRAINT FK_PD_Pedido   FOREIGN KEY (pedido_id)        REFERENCES Pedido(id),
    CONSTRAINT FK_PD_Producto FOREIGN KEY (producto_id)      REFERENCES Producto(id),
    CONSTRAINT FK_PD_Estado   FOREIGN KEY (estado_actual_id) REFERENCES Estado_Detalle(id),
    CONSTRAINT CK_PD_Cantidades CHECK (cantidad_solicitada >= 0 AND cantidad_procesada >= 0)
);
CREATE UNIQUE INDEX UX_PedidoDetalle_Filemaker ON Pedido_Detalle(filemaker_id) WHERE filemaker_id IS NOT NULL;
CREATE INDEX IX_PedidoDetalle_Pedido ON Pedido_Detalle(pedido_id);
CREATE INDEX IX_PedidoDetalle_Producto ON Pedido_Detalle(producto_id);
CREATE INDEX IX_PedidoDetalle_Estado ON Pedido_Detalle(estado_actual_id) WHERE eliminado_en_fm = 0;

-- ------------------------------------------------------------------------------
-- 7. TRAZABILIDAD
--    origen: 'USUARIO' (cambio manual desde la app) | 'SYNC' (creación/cambio por FileMaker)
-- ------------------------------------------------------------------------------

CREATE TABLE Historial_Estado_Pedido (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    pedido_id          BIGINT NOT NULL,
    estado_anterior_id INT NULL,
    estado_id          INT NOT NULL,
    usuario_id         INT NULL,
    origen             NVARCHAR(20) NOT NULL CONSTRAINT DF_HEP_Origen DEFAULT 'USUARIO',
    fecha_cambio       DATETIME2 NOT NULL CONSTRAINT DF_HEP_FechaCambio DEFAULT SYSUTCDATETIME(),
    observaciones      NVARCHAR(500) NULL,
    CONSTRAINT FK_HEP_Pedido         FOREIGN KEY (pedido_id)          REFERENCES Pedido(id),
    CONSTRAINT FK_HEP_EstadoAnterior FOREIGN KEY (estado_anterior_id) REFERENCES Estado_Pedido(id),
    CONSTRAINT FK_HEP_Estado         FOREIGN KEY (estado_id)          REFERENCES Estado_Pedido(id),
    CONSTRAINT FK_HEP_Usuario        FOREIGN KEY (usuario_id)         REFERENCES Usuario(id),
    CONSTRAINT CK_HEP_Origen CHECK (origen IN ('USUARIO', 'SYNC'))
);
-- Línea de tiempo de un pedido
CREATE INDEX IX_HEP_Pedido_Fecha ON Historial_Estado_Pedido(pedido_id, fecha_cambio);
-- KPIs: entradas a un estado en un rango de fechas
CREATE INDEX IX_HEP_Estado_Fecha ON Historial_Estado_Pedido(estado_id, fecha_cambio) INCLUDE (pedido_id);

CREATE TABLE Historial_Estado_Detalle (
    id                 BIGINT IDENTITY(1,1) PRIMARY KEY,
    pedido_detalle_id  BIGINT NOT NULL,
    estado_anterior_id INT NULL,
    estado_id          INT NOT NULL,
    usuario_id         INT NULL,
    origen             NVARCHAR(20) NOT NULL CONSTRAINT DF_HED_Origen DEFAULT 'USUARIO',
    cantidad_procesada INT NULL, -- Cantidad reportada en este movimiento (piezas reales para KPIs)
    fecha_cambio       DATETIME2 NOT NULL CONSTRAINT DF_HED_FechaCambio DEFAULT SYSUTCDATETIME(),
    observaciones      NVARCHAR(500) NULL,
    CONSTRAINT FK_HED_Detalle        FOREIGN KEY (pedido_detalle_id)  REFERENCES Pedido_Detalle(id),
    CONSTRAINT FK_HED_EstadoAnterior FOREIGN KEY (estado_anterior_id) REFERENCES Estado_Detalle(id),
    CONSTRAINT FK_HED_Estado         FOREIGN KEY (estado_id)          REFERENCES Estado_Detalle(id),
    CONSTRAINT FK_HED_Usuario        FOREIGN KEY (usuario_id)         REFERENCES Usuario(id),
    CONSTRAINT CK_HED_Origen CHECK (origen IN ('USUARIO', 'SYNC'))
);
CREATE INDEX IX_HED_Detalle_Fecha ON Historial_Estado_Detalle(pedido_detalle_id, fecha_cambio);
CREATE INDEX IX_HED_Estado_Fecha ON Historial_Estado_Detalle(estado_id, fecha_cambio) INCLUDE (pedido_detalle_id);
CREATE INDEX IX_HED_Usuario_Fecha ON Historial_Estado_Detalle(usuario_id, fecha_cambio) WHERE usuario_id IS NOT NULL;

-- Auditoría de cambios de datos (usuario_id NULL = cambio realizado por la sincronización)
CREATE TABLE Auditoria_Log (
    id               BIGINT IDENTITY(1,1) PRIMARY KEY,
    tabla_afectada   NVARCHAR(100) NOT NULL,
    registro_id      NVARCHAR(100) NOT NULL,
    campo_modificado NVARCHAR(100) NOT NULL,
    valor_anterior   NVARCHAR(MAX) NULL,
    valor_nuevo      NVARCHAR(MAX) NULL,
    usuario_id       INT NULL,
    fecha_cambio     DATETIME2 NOT NULL CONSTRAINT DF_Auditoria_FechaCambio DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_Auditoria_Usuario FOREIGN KEY (usuario_id) REFERENCES Usuario(id)
);
CREATE INDEX IX_Auditoria_Registro ON Auditoria_Log(tabla_afectada, registro_id, fecha_cambio);
