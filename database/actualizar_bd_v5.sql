-- =====================================================================
-- Aristools - Actualización v5: kardex valorizado (costo promedio ponderado)
--                              y responsable de revisión de los reportes PDF
-- Ejecutar UNA SOLA VEZ en MySQL Workbench (con el rayo que ejecuta todo).
-- No borra datos. (Una instalación nueva con aristools.sql ya lo incluye.)
-- =====================================================================
USE aristools;

-- 1) Costo promedio ponderado de cada producto.
--    Lo recalcula el sistema cada vez que se autoriza una compra:
--    nuevo promedio = (stock x promedio anterior + cantidad x costo de la factura)
--                     / (stock + cantidad)
ALTER TABLE producto
    ADD COLUMN costo_promedio DECIMAL(14,4) NOT NULL DEFAULT 0 AFTER costo_referencia,
    ADD CONSTRAINT ck_producto_costo_promedio CHECK (costo_promedio >= 0);

-- 2) Costo unitario con el que se valora cada movimiento.
--    Compra: el costo de la factura. Los demás: el costo promedio del
--    producto en el momento en que el movimiento afecta el stock.
ALTER TABLE movimiento_inventario
    ADD COLUMN costo_unitario DECIMAL(14,4) NULL AFTER cantidad,
    ADD CONSTRAINT ck_movimiento_costo CHECK (costo_unitario IS NULL OR costo_unitario >= 0);

-- 3) Punto de partida: lo que ya existe se valora al costo de referencia.
--    (Se filtra por la llave primaria para que Workbench lo permita en modo seguro.)
UPDATE producto SET costo_promedio = costo_referencia
WHERE id_producto > 0 AND costo_promedio = 0;

UPDATE movimiento_inventario
SET costo_unitario = (SELECT p.costo_referencia FROM producto p
                      WHERE p.id_producto = movimiento_inventario.id_producto)
WHERE id_movimiento > 0 AND costo_unitario IS NULL;

-- 4) Responsable de revisión: sale como "Revisó" en los reportes PDF.
--    El Administrador lo cambia en Configuración.
INSERT INTO parametro (clave, valor, descripcion) VALUES
('responsable_revision', 'Supervisor de Bodega', 'Nombre y cargo de quien revisa los reportes impresos (sale como Revisó en los PDF)')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- 5) Verificación: deben salir las dos columnas nuevas, ningún producto ni
--    movimiento sin costo (los dos conteos en 0) y el parámetro nuevo.
SHOW COLUMNS FROM producto LIKE 'costo%';
SHOW COLUMNS FROM movimiento_inventario LIKE 'costo%';
SELECT (SELECT COUNT(*) FROM producto WHERE costo_promedio = 0 AND costo_referencia > 0) AS productos_sin_costo,
       (SELECT COUNT(*) FROM movimiento_inventario WHERE costo_unitario IS NULL)          AS movimientos_sin_costo;
SELECT clave, valor FROM parametro WHERE clave = 'responsable_revision';
