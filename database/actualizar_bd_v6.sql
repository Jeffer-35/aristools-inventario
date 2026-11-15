-- =====================================================================
-- Aristools - Actualización v6: averías, pérdidas y bodega de cuarentena
-- Ejecutar UNA SOLA VEZ en MySQL Workbench (con el rayo que ejecuta todo).
-- No borra datos. (Una instalación nueva con aristools.sql ya lo incluye.)
-- =====================================================================
USE aristools;

-- 1) Causa estándar de la pérdida en mermas, daños y faltantes.
--    Con ella el reporte de pérdidas totaliza por causa.
ALTER TABLE movimiento_inventario
    ADD COLUMN causa_baja VARCHAR(20) NULL AFTER motivo,
    ADD CONSTRAINT ck_movimiento_causa CHECK (causa_baja IS NULL OR causa_baja IN
        ('VENCIDO', 'ROTO_O_GOLPEADO', 'DANO_TRANSPORTE', 'HUMEDAD', 'DEFECTO_FABRICA', 'FALTANTE', 'OTRO'));

-- 2) Bodega de cuarentena: guarda la mercancía averiada mientras se decide la baja.
ALTER TABLE bodega
    ADD COLUMN es_cuarentena BOOLEAN NOT NULL DEFAULT FALSE AFTER estado;

INSERT INTO bodega (codigo, nombre, ubicacion, es_cuarentena)
SELECT 'BOD-CUA', 'Cuarentena', 'Zona separada para mercancía averiada', TRUE
FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM bodega WHERE es_cuarentena = TRUE);

-- 3) Lo que ya existe: los ajustes negativos del conteo son faltantes; las
--    mermas y daños registrados antes de esta versión quedan como "Otro".
--    (Se filtra por la llave primaria para que Workbench lo permita en modo seguro.)
UPDATE movimiento_inventario SET causa_baja = 'FALTANTE'
WHERE id_movimiento > 0 AND causa_baja IS NULL AND id_tipo_movimiento IN
      (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre = 'Ajuste negativo');

UPDATE movimiento_inventario SET causa_baja = 'OTRO'
WHERE id_movimiento > 0 AND causa_baja IS NULL AND id_tipo_movimiento IN
      (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre IN ('Merma', 'Producto dañado o vencido'));

-- 4) Verificación: debe salir la columna nueva, la bodega de cuarentena y
--    cuántas pérdidas quedaron con cada causa.
SHOW COLUMNS FROM movimiento_inventario LIKE 'causa_baja';
SELECT codigo, nombre, es_cuarentena FROM bodega WHERE es_cuarentena = TRUE;
SELECT causa_baja, COUNT(*) AS movimientos FROM movimiento_inventario
WHERE causa_baja IS NOT NULL GROUP BY causa_baja;
