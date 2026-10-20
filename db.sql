-- ==============================================================================
-- PROYECTO: SISTEMA DE GESTIÓN Y TRAZABILIDAD DE PEDIDOS (INTEGRACIÓN FILEMAKER)
-- MOTOR: Microsoft SQL Server
-- ARQUITECTURA: Relacional 3FN / Event Sourcing Ligero / RBAC
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- 1. DOMINIO DE SEGURIDAD Y SISTEMA (RBAC)
-- ------------------------------------------------------------------------------

CREATE TABLE Usuario (
    id INT IDENTITY(1,1) PRIMARY KEY,
    email NVARCHAR(150) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    nombre_completo NVARCHAR(150) NOT NULL,
    activo BIT NOT NULL DEFAULT 1,
    CONSTRAINT UQ_Usuario_Email UNIQUE (email)
);

CREATE TABLE Rol (
    id INT IDENTITY(1,1) PRIMARY KEY,
    codigo NVARCHAR(50) NOT NULL, -- Ej: 'ADMIN', 'OPERADOR'
    descripcion NVARCHAR(255),
    CONSTRAINT UQ_Rol_Codigo UNIQUE (codigo)
);

CREATE TABLE Permiso (
    id INT IDENTITY(1,1) PRIMARY KEY,
    codigo NVARCHAR(100) NOT NULL, -- Ej: 'pedido:editar', 'estado:cambiar'
    descripcion NVARCHAR(255),
    CONSTRAINT UQ_Permiso_Codigo UNIQUE (codigo)
);

CREATE TABLE Usuario_Rol (
    usuario_id INT NOT NULL,
    rol_id INT NOT NULL,
    PRIMARY KEY (usuario_id, rol_id),
    CONSTRAINT FK_UR_Usuario FOREIGN KEY (usuario_id) REFERENCES Usuario(id),
    CONSTRAINT FK_UR_Rol FOREIGN KEY (rol_id) REFERENCES Rol(id)
);

CREATE TABLE Rol_Permiso (
    rol_id INT NOT NULL,
    permiso_id INT NOT NULL,
    PRIMARY KEY (rol_id, permiso_id),
    CONSTRAINT FK_RP_Rol FOREIGN KEY (rol_id) REFERENCES Rol(id),
    CONSTRAINT FK_RP_Permiso FOREIGN KEY (permiso_id) REFERENCES Permiso(id)
);

CREATE TABLE Sync_Log (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    fecha_ejecucion DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    entidad NVARCHAR(100) NOT NULL,
    registros_leidos INT NOT NULL DEFAULT 0,
    registros_insertados INT NOT NULL DEFAULT 0,
    registros_actualizados INT NOT NULL DEFAULT 0,
    estado NVARCHAR(50) NOT NULL, -- 'EXITO', 'ERROR', 'PARCIAL'
    mensaje_error NVARCHAR(MAX)
);

-- ------------------------------------------------------------------------------
-- 2. DOMINIO DE CATÁLOGOS Y MAESTROS
-- ------------------------------------------------------------------------------

CREATE TABLE Estado_Pedido (
    id INT IDENTITY(1,1) PRIMARY KEY,
    codigo NVARCHAR(50) NOT NULL,
    nombre NVARCHAR(100) NOT NULL,
    orden_logico INT NOT NULL,
    CONSTRAINT UQ_EstadoPedido_Codigo UNIQUE (codigo)
);

CREATE TABLE Estado_Detalle (
    id INT IDENTITY(1,1) PRIMARY KEY,
    codigo NVARCHAR(50) NOT NULL,
    nombre NVARCHAR(100) NOT NULL,
    orden_logico INT NOT NULL,
    CONSTRAINT UQ_EstadoDetalle_Codigo UNIQUE (codigo)
);

CREATE TABLE Producto (
    id INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NOT NULL,
    sku NVARCHAR(50) NOT NULL,
    nombre NVARCHAR(255) NOT NULL,
    precio_base DECIMAL(18,2) NOT NULL DEFAULT 0,
    CONSTRAINT UQ_Producto_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT UQ_Producto_Sku UNIQUE (sku)
);

-- ------------------------------------------------------------------------------
-- 3. DOMINIO GEOGRÁFICO Y TERCEROS
-- ------------------------------------------------------------------------------

CREATE TABLE Pais (
    id INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NULL,
    nombre NVARCHAR(100) NOT NULL,
    codigo_iso NVARCHAR(10) NULL,
    CONSTRAINT UQ_Pais_Filemaker UNIQUE (filemaker_id)
);

CREATE TABLE Departamento (
    id INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NULL,
    pais_id INT NOT NULL,
    nombre NVARCHAR(150) NOT NULL,
    CONSTRAINT UQ_Departamento_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_Departamento_Pais FOREIGN KEY (pais_id) REFERENCES Pais(id)
);

CREATE TABLE Ciudad (
    id INT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NULL,
    departamento_id INT NOT NULL,
    nombre NVARCHAR(150) NOT NULL,
    CONSTRAINT UQ_Ciudad_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_Ciudad_Departamento FOREIGN KEY (departamento_id) REFERENCES Departamento(id)
);

CREATE TABLE Cliente (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NOT NULL,
    documento_identidad NVARCHAR(50) NOT NULL,
    razon_social NVARCHAR(255) NOT NULL,
    email NVARCHAR(150),
    telefono NVARCHAR(50),
    CONSTRAINT UQ_Cliente_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT UQ_Cliente_Documento UNIQUE (documento_identidad)
);

CREATE TABLE Direccion (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NULL,
    cliente_id BIGINT NOT NULL,
    ciudad_id INT NOT NULL,
    direccion_texto NVARCHAR(255) NOT NULL,
    tipo NVARCHAR(50), -- Ej: 'Facturacion', 'Envio'
    CONSTRAINT UQ_Direccion_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_Direccion_Cliente FOREIGN KEY (cliente_id) REFERENCES Cliente(id),
    CONSTRAINT FK_Direccion_Ciudad FOREIGN KEY (ciudad_id) REFERENCES Ciudad(id)
);

-- ------------------------------------------------------------------------------
-- 4. DOMINIO TRANSACCIONAL (CORE OPERATIVO)
-- ------------------------------------------------------------------------------

CREATE TABLE Pedido (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NOT NULL,
    cliente_id BIGINT NOT NULL,
    direccion_envio_id BIGINT NOT NULL,
    usuario_asignado_id INT NULL,
    estado_actual_id INT NOT NULL,
    fecha_creacion_fm DATETIME2 NOT NULL,
    fecha_ingreso_local DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    fecha_estimada_entrega DATETIME2 NULL,
    total_pedido DECIMAL(18,2) NOT NULL DEFAULT 0,
    eliminado_en_fm BIT NOT NULL DEFAULT 0, -- Regla de Soft Delete
    CONSTRAINT UQ_Pedido_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_Pedido_Cliente FOREIGN KEY (cliente_id) REFERENCES Cliente(id),
    CONSTRAINT FK_Pedido_Direccion FOREIGN KEY (direccion_envio_id) REFERENCES Direccion(id),
    CONSTRAINT FK_Pedido_Usuario FOREIGN KEY (usuario_asignado_id) REFERENCES Usuario(id),
    CONSTRAINT FK_Pedido_Estado FOREIGN KEY (estado_actual_id) REFERENCES Estado_Pedido(id)
);

CREATE TABLE Pedido_Detalle (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    filemaker_id NVARCHAR(100) NULL,
    pedido_id BIGINT NOT NULL,
    producto_id INT NOT NULL,
    estado_actual_id INT NOT NULL,
    cantidad_solicitada INT NOT NULL,
    cantidad_procesada INT NOT NULL DEFAULT 0,
    precio_unitario DECIMAL(18,2) NOT NULL, -- Snapshot del precio
    eliminado_en_fm BIT NOT NULL DEFAULT 0,
    CONSTRAINT UQ_PedidoDetalle_Filemaker UNIQUE (filemaker_id),
    CONSTRAINT FK_PD_Pedido FOREIGN KEY (pedido_id) REFERENCES Pedido(id),
    CONSTRAINT FK_PD_Producto FOREIGN KEY (producto_id) REFERENCES Producto(id),
    CONSTRAINT FK_PD_Estado FOREIGN KEY (estado_actual_id) REFERENCES Estado_Detalle(id)
);

-- ------------------------------------------------------------------------------
-- 5. DOMINIO DE TRAZABILIDAD E HISTÓRICOS
-- ------------------------------------------------------------------------------

CREATE TABLE Historial_Estado_Pedido (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pedido_id BIGINT NOT NULL,
    estado_id INT NOT NULL,
    usuario_id INT NULL,
    fecha_cambio DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    observaciones NVARCHAR(500),
    CONSTRAINT FK_HEP_Pedido FOREIGN KEY (pedido_id) REFERENCES Pedido(id),
    CONSTRAINT FK_HEP_Estado FOREIGN KEY (estado_id) REFERENCES Estado_Pedido(id),
    CONSTRAINT FK_HEP_Usuario FOREIGN KEY (usuario_id) REFERENCES Usuario(id)
);

CREATE TABLE Historial_Estado_Detalle (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    pedido_detalle_id BIGINT NOT NULL,
    estado_id INT NOT NULL,
    usuario_id INT NULL,
    fecha_cambio DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    observaciones NVARCHAR(500),
    CONSTRAINT FK_HED_Detalle FOREIGN KEY (pedido_detalle_id) REFERENCES Pedido_Detalle(id),
    CONSTRAINT FK_HED_Estado FOREIGN KEY (estado_id) REFERENCES Estado_Detalle(id),
    CONSTRAINT FK_HED_Usuario FOREIGN KEY (usuario_id) REFERENCES Usuario(id)
);

CREATE TABLE Auditoria_Log (
    id BIGINT IDENTITY(1,1) PRIMARY KEY,
    tabla_afectada NVARCHAR(100) NOT NULL,
    registro_id NVARCHAR(100) NOT NULL,
    campo_modificado NVARCHAR(100) NOT NULL,
    valor_anterior NVARCHAR(MAX),
    valor_nuevo NVARCHAR(MAX),
    usuario_id INT NOT NULL,
    fecha_cambio DATETIME2 NOT NULL DEFAULT SYSUTCDATETIME(),
    CONSTRAINT FK_Auditoria_Usuario FOREIGN KEY (usuario_id) REFERENCES Usuario(id)
);

-- ------------------------------------------------------------------------------
-- 6. CREACIÓN DE ÍNDICES PARA RENDIMIENTO Y KPIs
-- ------------------------------------------------------------------------------

-- Índices B-Tree para Claves Foráneas de alto tráfico (Vital en SQL Server)
CREATE INDEX IX_Pedido_Cliente ON Pedido(cliente_id);
CREATE INDEX IX_Pedido_Direccion ON Pedido(direccion_envio_id);
CREATE INDEX IX_PedidoDetalle_Pedido ON Pedido_Detalle(pedido_id);
CREATE INDEX IX_PedidoDetalle_Producto ON Pedido_Detalle(producto_id);

-- Índice compuesto para Dashboard Operativo (Pedidos atrasados o en curso)
CREATE INDEX IX_Pedido_Estado_FechaEntrega ON Pedido(estado_actual_id, fecha_estimada_entrega);

-- Índice compuesto para análisis histórico de tiempos (Event Sourcing)
CREATE INDEX IX_HistorialDetalle_CalculoTiempos ON Historial_Estado_Detalle(pedido_detalle_id, estado_id, fecha_cambio);

-- Índice Filtrado (Parcial) para búsquedas sobre pedidos activos únicamente
CREATE INDEX IX_Pedido_Activos ON Pedido(cliente_id, fecha_estimada_entrega) 
WHERE eliminado_en_fm = 0; 

-- NOTA ARQUITECTÓNICA SOBRE BÚSQUEDAS DE TEXTO PARCIAL:
-- Para búsquedas tipo LIKE '%texto%' en Cliente.razon_social o Cliente.documento_identidad, 
-- se recomienda implementar "Full-Text Search (FTS)" nativo de SQL Server.
-- Requiere la creación previa de un FULLTEXT CATALOG en el servidor, lo cual se hace a nivel de instancia.