-- ==============================================================================
-- MIGRACIÓN V4: La sincronización no importa pedidos que en FileMaker ya están en un estado final
-- (ej. Facturado). El control en la app inicia con los pedidos que siguen en proceso.
-- registros_omitidos: líneas de esos pedidos que se leyeron pero no se importaron.
-- ==============================================================================
ALTER TABLE Sync_Log ADD
    registros_omitidos INT NOT NULL CONSTRAINT DF_SyncLog_Omitidos DEFAULT 0;
