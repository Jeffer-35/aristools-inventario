-- =====================================================================
-- Aristools: actualización de la base de datos (versión 2)
-- Se ejecuta UNA SOLA VEZ sobre una base aristools que ya tiene datos.
-- No borra información. Si la base se crea desde cero con aristools.sql,
-- estos cambios ya vienen incluidos y este archivo NO se ejecuta.
-- =====================================================================
USE aristools;

-- 1. Corrección del RF-004: el nombre de la bodega no se puede repetir.
ALTER TABLE bodega
    ADD CONSTRAINT uq_bodega_nombre UNIQUE (nombre);

-- 2. Segregación de funciones en la base: quien registra no autoriza.
ALTER TABLE movimiento_inventario
    ADD CONSTRAINT ck_movimiento_segregacion
    CHECK (id_usuario_autoriza IS NULL OR id_usuario_autoriza <> id_usuario);

-- 3. Todo movimiento debe afectar al menos una bodega.
ALTER TABLE movimiento_inventario
    ADD CONSTRAINT ck_movimiento_bodega
    CHECK (id_bodega_origen IS NOT NULL OR id_bodega_destino IS NOT NULL);

-- ---------------------------------------------------------------------
-- Verificación: debe listar las 3 restricciones nuevas.
-- ---------------------------------------------------------------------
SELECT table_name, constraint_name, constraint_type
FROM information_schema.table_constraints
WHERE constraint_schema = 'aristools'
  AND constraint_name IN ('uq_bodega_nombre', 'ck_movimiento_segregacion',
                          'ck_movimiento_bodega');
