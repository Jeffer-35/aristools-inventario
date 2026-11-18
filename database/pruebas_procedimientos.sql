-- =====================================================================
-- Aristools - Pruebas manuales de los procedimientos almacenados
-- (Bases de Datos 2, semanas 13 y 14)
--
-- Se ejecuta en MySQL Workbench sobre la base aristools, DESPUÉS de
-- ejecutar procedimientos.sql. No daña los datos de trabajo: crea su
-- propio producto de prueba (código SPD-...) y el bloque LIMPIEZA del
-- final borra ese producto con todo lo que se le hizo.
-- Ejecutar bloque por bloque (seleccionar el bloque y Ctrl+Shift+Enter) y
-- tomar captura del resultado de cada uno. Se puede repetir las veces
-- que se quiera.
-- =====================================================================
USE aristools;
SET SQL_SAFE_UPDATES = 0;

-- ---------------------------------------------------------------------
-- 0. Datos de la prueba: un producto con mínimo 10, máximo 50 y 45 unidades
-- ---------------------------------------------------------------------
SET @almacen    = (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'almacen');
SET @compras    = (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'compras');
SET @supervisor = (SELECT id_usuario FROM usuario WHERE nombre_usuario = 'supervisor');
SET @bod1       = (SELECT id_bodega FROM bodega WHERE codigo = 'BOD-01');
SET @bod2       = (SELECT id_bodega FROM bodega WHERE codigo = 'BOD-02');

INSERT INTO producto (codigo, nombre, id_categoria, unidad_medida, costo_referencia, stock_minimo, stock_maximo)
VALUES (CONCAT('SPD-', UNIX_TIMESTAMP()), 'Producto demo procedimientos', 1, 'Unidad', 1000, 10, 50);
SET @p = LAST_INSERT_ID();
INSERT INTO existencia (id_producto, id_bodega, cantidad) VALUES (@p, @bod1, 45);
SELECT @p AS producto_prueba, @almacen AS almacen, @compras AS compras, @supervisor AS supervisor;

-- ---------------------------------------------------------------------
-- PRUEBA 1. sp_consultar_existencias: filas + parámetro OUT
-- Esperado: 1 fila (BOD-01, 45) y total = 45
-- ---------------------------------------------------------------------
CALL sp_consultar_existencias(@p, @total);
SELECT @total AS total_unidades;

-- ---------------------------------------------------------------------
-- PRUEBA 2. sp_autorizar_movimiento con una entrada (Compra de 10)
-- Esperado: mensaje 'Movimiento autorizado', stock 55 y alerta SOBRESTOCK
-- (55 > 50), creada por sp_evaluar_alertas_producto
-- ---------------------------------------------------------------------
INSERT INTO movimiento_inventario (id_producto, id_tipo_movimiento, id_bodega_destino, id_usuario, estado,
                                   cantidad, documento_soporte, motivo)
VALUES (@p, (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre = 'Compra'), @bod1, @almacen,
        'PENDIENTE', 10, 'FAC-DEMO-1', 'Prueba del taller');
SET @compra = LAST_INSERT_ID();

CALL sp_autorizar_movimiento(@compra, @compras, @mensaje);
SELECT @mensaje AS mensaje;
SELECT id_movimiento, estado, id_usuario_autoriza, fecha_autorizacion FROM movimiento_inventario WHERE id_movimiento = @compra;
SELECT cantidad AS stock_bod01 FROM existencia WHERE id_producto = @p AND id_bodega = @bod1;
SELECT tipo, stock_detectado, umbral, estado FROM alerta WHERE id_producto = @p;

-- ---------------------------------------------------------------------
-- PRUEBA 3. Errores de sp_autorizar_movimiento (cada CALL debe dar Error 1644)
-- ---------------------------------------------------------------------
-- 3a. Autorizar otra vez el mismo movimiento
--     Esperado: 'El movimiento no existe o ya fue resuelto'
CALL sp_autorizar_movimiento(@compra, @compras, @mensaje);

-- 3b. Una merma de 100 (no hay stock) autorizada por el supervisor
--     Esperado: 'Stock insuficiente en la bodega de origen', la merma sigue PENDIENTE y el stock en 55
INSERT INTO movimiento_inventario (id_producto, id_tipo_movimiento, id_bodega_origen, id_usuario, estado,
                                   cantidad, motivo, causa_baja)
VALUES (@p, (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre = 'Merma'), @bod1, @almacen,
        'PENDIENTE', 100, 'Prueba del taller', 'OTRO');
SET @merma = LAST_INSERT_ID();
CALL sp_autorizar_movimiento(@merma, @supervisor, @mensaje);
SELECT estado FROM movimiento_inventario WHERE id_movimiento = @merma;
SELECT cantidad AS stock_bod01 FROM existencia WHERE id_producto = @p AND id_bodega = @bod1;

-- 3c. La misma merma la intenta autorizar el jefe de compras (rol equivocado)
--     Esperado: 'El usuario no está activo o no tiene el rol para autorizar este movimiento'
CALL sp_autorizar_movimiento(@merma, @compras, @mensaje);

-- ---------------------------------------------------------------------
-- PRUEBA 4. sp_registrar_transferencia: 12 unidades de BOD-01 a BOD-02
-- Esperado: devuelve el número del movimiento, BOD-01 = 43, BOD-02 = 12
-- ---------------------------------------------------------------------
CALL sp_registrar_transferencia(@p, @bod1, @bod2, 12, @almacen, 'Para el mostrador', @id_transferencia);
SELECT @id_transferencia AS movimiento_creado;
CALL sp_consultar_existencias(@p, @total);
SELECT @total AS total_unidades;

-- 4b. Errores (cada CALL debe dar Error 1644)
CALL sp_registrar_transferencia(@p, @bod1, @bod1, 5, @almacen, NULL, @id_transferencia);   -- misma bodega
CALL sp_registrar_transferencia(@p, @bod1, @bod2, 500, @almacen, NULL, @id_transferencia); -- sin stock
CALL sp_registrar_transferencia(@p, @bod1, @bod2, 1, @compras, NULL, @id_transferencia);   -- rol sin permiso

-- ---------------------------------------------------------------------
-- PRUEBA 5. sp_evaluar_alertas_producto solo (prueba unitaria)
-- Se baja el stock a 5 a mano y se evalúa dos veces.
-- Esperado: SOBRESTOCK RESUELTA y una sola BAJO_STOCK ACTIVA (no se repite)
-- ---------------------------------------------------------------------
UPDATE existencia SET cantidad = 5 WHERE id_producto = @p AND id_bodega = @bod1;
UPDATE existencia SET cantidad = 0 WHERE id_producto = @p AND id_bodega = @bod2;
CALL sp_evaluar_alertas_producto(@p);
CALL sp_evaluar_alertas_producto(@p);
SELECT tipo, stock_detectado, umbral, estado FROM alerta WHERE id_producto = @p ORDER BY id_alerta;

-- ---------------------------------------------------------------------
-- LIMPIEZA: borra el producto de prueba y todo lo que se le hizo
-- (sus alertas, movimientos y existencias). Ejecutarlo siempre al final.
-- ---------------------------------------------------------------------
DELETE FROM alerta WHERE id_producto = @p;
DELETE FROM movimiento_inventario WHERE id_producto = @p;
DELETE FROM existencia WHERE id_producto = @p;
DELETE FROM producto WHERE id_producto = @p;
SET SQL_SAFE_UPDATES = 1;
SELECT COUNT(*) AS productos_de_prueba_que_quedan FROM producto WHERE codigo LIKE 'SPD-%';
