-- ==============================================================================
-- MIGRACIÓN V12: Gestión de usuarios desde la app (solo el Administrador: permiso usuario:gestionar)
--
-- debe_cambiar_contrasena: el usuario entra con una contraseña temporal (creado o restablecido por el
--   administrador) y no tiene permisos hasta que la cambie.
-- sesiones_validas_desde: los tokens emitidos antes de esta fecha (UTC) dejan de servir; se fija al restablecer
--   la contraseña o desactivar al usuario, para cerrar sus sesiones abiertas.
-- ==============================================================================

ALTER TABLE Usuario ADD
    debe_cambiar_contrasena BIT NOT NULL CONSTRAINT DF_Usuario_DebeCambiarContrasena DEFAULT 0,
    sesiones_validas_desde  DATETIME2 NULL;

-- Descripciones al día con los permisos que se agregaron después de V2 (se muestran al asignar roles)
UPDATE Rol SET descripcion = 'Administrador del sistema: acceso total y gestión de usuarios' WHERE codigo = 'ADMIN';
UPDATE Rol SET descripcion = 'Coordinador de operaciones: gestiona pedidos, fechas de entrega, estados, sincronización e indicadores'
WHERE codigo = 'COORDINADOR';
UPDATE Rol SET descripcion = 'Supervisor de planta: reporta avance, corrige piezas, marca o devuelve líneas No sale y consulta indicadores'
WHERE codigo = 'SUPERVISOR';
