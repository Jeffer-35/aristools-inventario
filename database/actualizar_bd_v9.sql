-- =====================================================================
-- Aristools - Actualización v9: seguimiento de la orden de compra
-- (pendiente, recibida en parte, recibida o anulada) y entrada enlazada
-- con la orden que recibe.
-- Ejecutar UNA SOLA VEZ en MySQL Workbench (con el rayo que ejecuta todo).
-- No borra datos. (Una instalación nueva con aristools.sql ya lo incluye.)
-- Si sale "Duplicate column" o "Duplicate foreign key", ya estaba ejecutado.
-- =====================================================================
USE aristools;

-- 1) Estado de cada orden. Las órdenes que ya existían quedan PENDIENTES;
--    si ya llegaron, se pueden anular desde la pantalla "Órdenes de compra".
ALTER TABLE orden_compra
    ADD COLUMN estado VARCHAR(10) NOT NULL DEFAULT 'PENDIENTE' AFTER observacion,
    ADD COLUMN motivo_anulacion VARCHAR(255) NULL AFTER estado,
    ADD CONSTRAINT ck_orden_estado CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'RECIBIDA', 'ANULADA'));

-- 2) Cuánto ha llegado de cada producto de la orden.
ALTER TABLE orden_compra_detalle
    ADD COLUMN cantidad_recibida INT NOT NULL DEFAULT 0 AFTER costo_unitario,
    ADD CONSTRAINT ck_detalle_recibida CHECK (cantidad_recibida BETWEEN 0 AND cantidad);

-- 3) La entrada (compra) puede indicar qué orden de compra está recibiendo.
ALTER TABLE movimiento_inventario
    ADD COLUMN id_orden INT NULL AFTER fecha_vencimiento,
    ADD CONSTRAINT fk_movimiento_orden FOREIGN KEY (id_orden) REFERENCES orden_compra (id_orden);

-- 4) Verificación: deben salir las tres columnas nuevas.
SHOW COLUMNS FROM orden_compra LIKE 'estado';
SHOW COLUMNS FROM orden_compra_detalle LIKE 'cantidad_recibida';
SHOW COLUMNS FROM movimiento_inventario LIKE 'id_orden';
