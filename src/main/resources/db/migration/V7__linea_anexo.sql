-- ==============================================================================
-- MIGRACIÓN V7: Líneas anexas
-- LinPed_Anexo (FileMaker: "SI" / "No") indica que la línea es un anexo agregado al pedido.
-- Las líneas existentes quedan en 0 y se corrigen en la siguiente sincronización: agregar el campo al
-- layout cambia la huella de todos los registros, así que todas las líneas se vuelven a aplicar.
-- ==============================================================================

ALTER TABLE Pedido_Detalle ADD es_anexo BIT NOT NULL CONSTRAINT DF_PD_EsAnexo DEFAULT 0;
