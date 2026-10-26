-- ==============================================================================
-- MIGRACIÓN V15: Producto terminado comprometido por los pedidos
--
-- Reserva_Inventario: unidades de la bodega de producto terminado (app.inventario.bodega-producto-terminado, 1A001)
--   apartadas para una línea de pedido. Se reserva por bodega, no por slot: los traslados entre slots de la misma
--   bodega no la afectan. Lo libre de un ítem es su existencia en la bodega menos lo reservado por todos los pedidos;
--   las salidas y traslados a otra bodega no pueden tomar lo reservado (los ajustes por conteo sí, porque son lo
--   que hay físicamente).
--   - La reserva la hace un usuario (botón Comprometer o por línea) y nunca supera la cantidad solicitada.
--   - Al pasar la línea a un estado final (Entregado) se registra la salida de lo reservado y la reserva desaparece.
--   - Se libera si la línea pasa a No sale, si se elimina en FileMaker o si el pedido se cierra.
-- Movimiento_Inventario.pedido_detalle_id: línea de pedido que originó la salida (despacho).
-- ==============================================================================

CREATE TABLE Reserva_Inventario (
    id                BIGINT IDENTITY(1,1) PRIMARY KEY,
    pedido_detalle_id BIGINT NOT NULL,
    item_id           INT NOT NULL,
    bodega_id         INT NOT NULL,
    cantidad          DECIMAL(18,6) NOT NULL,
    usuario           NVARCHAR(150) NOT NULL,
    fecha             DATETIME2 NOT NULL,
    CONSTRAINT FK_RI_Detalle FOREIGN KEY (pedido_detalle_id) REFERENCES Pedido_Detalle(id),
    CONSTRAINT FK_RI_Item    FOREIGN KEY (item_id)           REFERENCES Item(id),
    CONSTRAINT FK_RI_Bodega  FOREIGN KEY (bodega_id)         REFERENCES Bodega(id),
    CONSTRAINT UQ_RI_Detalle UNIQUE (pedido_detalle_id),
    CONSTRAINT CK_RI_Cantidad CHECK (cantidad > 0)
);
CREATE INDEX IX_RI_ItemBodega ON Reserva_Inventario(item_id, bodega_id) INCLUDE (cantidad);
CREATE INDEX IX_RI_Fecha ON Reserva_Inventario(fecha DESC);

ALTER TABLE Movimiento_Inventario ADD pedido_detalle_id BIGINT NULL
    CONSTRAINT FK_MI_Detalle FOREIGN KEY REFERENCES Pedido_Detalle(id);

INSERT INTO Permiso (codigo, descripcion) VALUES
    ('inventario:reservar', 'Comprometer y liberar producto terminado de la bodega para los pedidos');

INSERT INTO Rol_Permiso (rol_id, permiso_id)
SELECT r.id, p.id FROM Rol r CROSS JOIN Permiso p
WHERE r.codigo IN ('ADMIN', 'COORDINADOR') AND p.codigo = 'inventario:reservar';
