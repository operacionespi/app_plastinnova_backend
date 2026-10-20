-- ==============================================================================
-- MIGRACIÓN V8: Sincronización incremental
-- Cada ejecución lee solo lo modificado desde Sync_Control.ultima_fecha_modificacion_fm (columna de V1,
-- hasta ahora sin uso). Sync_Log registra qué tipo de lectura se hizo.
-- ==============================================================================

ALTER TABLE Sync_Log ADD modo NVARCHAR(20) NULL
    CONSTRAINT CK_SyncLog_Modo CHECK (modo IN ('COMPLETA', 'INCREMENTAL', 'RECONCILIACION'));
