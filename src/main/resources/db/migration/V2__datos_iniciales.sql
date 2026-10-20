-- ==============================================================================
-- MIGRACIÓN V2: Datos iniciales (roles, permisos, usuario administrador, estados y transiciones)
-- Los estados y transiciones son editables desde la aplicación; esto es solo la configuración de arranque.
-- ==============================================================================

-- ------------------------------------------------------------------------------
-- ROLES Y PERMISOS
-- ------------------------------------------------------------------------------
INSERT INTO Rol (codigo, descripcion) VALUES
    ('ADMIN',       'Administrador del sistema: acceso total'),
    ('COORDINADOR', 'Coordinador de operaciones: gestiona pedidos, estados, sincronización e indicadores'),
    ('SUPERVISOR',  'Supervisor de planta: cambia estados, asigna pedidos y consulta indicadores'),
    ('OPERADOR',    'Operador de planta: consulta pedidos y reporta avance de las líneas de producción');

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('pedido:ver',             'Consultar pedidos, detalles e historial'),
    ('pedido:cambiar-estado',  'Cambiar el estado general de un pedido'),
    ('detalle:cambiar-estado', 'Cambiar el estado de una línea de producción y reportar cantidades'),
    ('pedido:asignar',         'Asignar un responsable a un pedido'),
    ('estado:gestionar',       'Crear, modificar y desactivar estados y transiciones'),
    ('usuario:gestionar',      'Administrar usuarios y roles'),
    ('sync:ejecutar',          'Ejecutar manualmente la sincronización con FileMaker y ver su bitácora'),
    ('kpi:ver',                'Consultar indicadores y tableros');

-- ADMIN: todos los permisos
INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p WHERE r.codigo = 'ADMIN';

-- COORDINADOR: todo excepto administración de usuarios
INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo = 'COORDINADOR' AND p.codigo <> 'usuario:gestionar';

-- SUPERVISOR
INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo = 'SUPERVISOR'
  AND p.codigo IN ('pedido:ver', 'pedido:cambiar-estado', 'detalle:cambiar-estado', 'pedido:asignar', 'kpi:ver');

-- OPERADOR
INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo = 'OPERADOR'
  AND p.codigo IN ('pedido:ver', 'detalle:cambiar-estado');

-- ------------------------------------------------------------------------------
-- USUARIO ADMINISTRADOR INICIAL
-- Contraseña temporal entregada por separado: CAMBIARLA en el primer ingreso.
-- ------------------------------------------------------------------------------
INSERT INTO Usuario (email, password_hash, nombre_completo, activo) VALUES
    ('admin@plast-innova.com.co', '$2a$10$Remfs/nwTtMvyg0T219Z.uV.Fo52izZKOi6ffj0H6ZZ6RpeLfiBIq', 'Administrador del Sistema', 1);

INSERT INTO Usuario_Rol (usuario_id, rol_id)
SELECT u.id, r.id FROM Usuario u CROSS JOIN Rol r
WHERE u.email = 'admin@plast-innova.com.co' AND r.codigo = 'ADMIN';

-- ------------------------------------------------------------------------------
-- ESTADOS DEL PEDIDO
-- ------------------------------------------------------------------------------
INSERT INTO Estado_Pedido (codigo, nombre, orden_logico, color, es_inicial, es_final) VALUES
    ('CREADO',          'Creado',          10, '#9E9E9E', 1, 0),
    ('EN_ALISTAMIENTO', 'En alistamiento', 20, '#1E88E5', 0, 0),
    ('EN_DESPACHOS',    'En despachos',    30, '#FB8C00', 0, 0),
    ('PARA_DESPACHAR',  'Para despachar',  40, '#8E24AA', 0, 0),
    ('FACTURADO',       'Facturado',       50, '#43A047', 0, 1);

-- ------------------------------------------------------------------------------
-- ESTADOS DE LAS LÍNEAS DE PRODUCCIÓN (productos del pedido)
-- ------------------------------------------------------------------------------
INSERT INTO Estado_Detalle (codigo, nombre, orden_logico, color, es_inicial, es_final) VALUES
    ('CREADO',      'Creado',      10, '#9E9E9E', 1, 0),
    ('SOLICITADO',  'Solicitado',  20, '#1E88E5', 0, 0),
    ('EN_ENSAMBLE', 'En ensamble', 30, '#FB8C00', 0, 0),
    ('RESINA',      'Resina',      40, '#6D4C41', 0, 0),
    ('CALIDAD',     'Calidad',     50, '#8E24AA', 0, 0),
    ('ALMACEN1',    'Almacén 1',   60, '#00897B', 0, 0),
    ('ENTREGADO',   'Entregado',   70, '#43A047', 0, 1);

-- ------------------------------------------------------------------------------
-- TRANSICIONES PERMITIDAS (flujo secuencial hacia adelante + reprocesos de calidad)
-- ------------------------------------------------------------------------------
INSERT INTO Transicion_Estado_Pedido (estado_origen_id, estado_destino_id)
SELECT o.id, d.id
FROM (VALUES
        ('CREADO',          'EN_ALISTAMIENTO'),
        ('EN_ALISTAMIENTO', 'EN_DESPACHOS'),
        ('EN_DESPACHOS',    'PARA_DESPACHAR'),
        ('PARA_DESPACHAR',  'FACTURADO')
     ) AS t(origen, destino)
JOIN Estado_Pedido o ON o.codigo = t.origen
JOIN Estado_Pedido d ON d.codigo = t.destino;

INSERT INTO Transicion_Estado_Detalle (estado_origen_id, estado_destino_id)
SELECT o.id, d.id
FROM (VALUES
        ('CREADO',      'SOLICITADO'),
        ('SOLICITADO',  'EN_ENSAMBLE'),
        ('EN_ENSAMBLE', 'RESINA'),
        ('RESINA',      'CALIDAD'),
        ('CALIDAD',     'ALMACEN1'),
        ('ALMACEN1',    'ENTREGADO'),
        -- Reprocesos: una pieza rechazada en calidad regresa a ensamble o resina
        ('CALIDAD',     'EN_ENSAMBLE'),
        ('CALIDAD',     'RESINA')
     ) AS t(origen, destino)
JOIN Estado_Detalle o ON o.codigo = t.origen
JOIN Estado_Detalle d ON d.codigo = t.destino;

-- ------------------------------------------------------------------------------
-- CONTROL DE SINCRONIZACIÓN (orden de dependencia)
-- ------------------------------------------------------------------------------
INSERT INTO Sync_Control (entidad) VALUES
    ('Pais'), ('Departamento'), ('Ciudad'), ('Cliente'), ('Producto'), ('Pedido');
