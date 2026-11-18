-- =====================================================================
-- Aristools - Procedimientos almacenados (Bases de Datos 2, semanas 13 y 14)
-- Codifican en SQL de MySQL los algoritmos diseñados en el documento
-- "Análisis de Procedimientos SQL":
--   1. sp_evaluar_alertas_producto  (RF-012)
--   2. sp_autorizar_movimiento      (RF-010 y RF-011)
--   3. sp_registrar_transferencia   (RF-008)
--   4. sp_consultar_existencias     (RF-013, consulta que devuelve filas)
-- Ejecutar en MySQL Workbench con el rayo. Se puede ejecutar varias veces:
-- cada procedimiento se borra (DROP) y se vuelve a crear.
-- (Una instalación nueva con aristools.sql ya los incluye.)
-- =====================================================================
USE aristools;

DELIMITER $$

-- ---------------------------------------------------------------------
-- 1. sp_evaluar_alertas_producto (RF-012)
-- Compara el stock total del producto con su mínimo y su máximo, crea la
-- alerta que corresponde sin repetirla y resuelve las que ya no aplican.
-- No abre transacción: trabaja dentro de la de quien lo llama.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_evaluar_alertas_producto $$
CREATE PROCEDURE sp_evaluar_alertas_producto(IN p_id_producto INT)
proc: BEGIN
    DECLARE v_estado VARCHAR(10);
    DECLARE v_minimo INT;
    DECLARE v_maximo INT;
    DECLARE v_total INT DEFAULT 0;
    DECLARE v_activas INT DEFAULT 0;

    -- P3-S1: estado y umbrales (propios o los generales de parametro)
    SELECT p.estado,
           COALESCE(p.stock_minimo, (SELECT CAST(valor AS SIGNED) FROM parametro WHERE clave = 'stock_minimo_defecto')),
           COALESCE(p.stock_maximo, (SELECT CAST(valor AS SIGNED) FROM parametro WHERE clave = 'stock_maximo_defecto'))
      INTO v_estado, v_minimo, v_maximo
      FROM producto p
     WHERE p.id_producto = p_id_producto;

    IF v_estado IS NULL OR v_estado <> 'ACTIVO' THEN
        LEAVE proc;   -- un producto que no existe o está inactivo no genera alertas
    END IF;

    -- P3-S2: stock total en todas las bodegas
    SELECT COALESCE(SUM(cantidad), 0) INTO v_total
      FROM existencia
     WHERE id_producto = p_id_producto;

    -- Bajo stock: total <= mínimo
    IF v_total <= v_minimo THEN
        SELECT COUNT(*) INTO v_activas                          -- P3-S3
          FROM alerta
         WHERE id_producto = p_id_producto AND tipo = 'BAJO_STOCK' AND estado = 'ACTIVA';
        IF v_activas = 0 THEN
            INSERT INTO alerta (id_producto, tipo, stock_detectado, umbral, estado)   -- P3-S4
            VALUES (p_id_producto, 'BAJO_STOCK', v_total, v_minimo, 'ACTIVA');
        END IF;
    ELSE
        UPDATE alerta SET estado = 'RESUELTA'                   -- P3-S5
         WHERE id_producto = p_id_producto AND tipo = 'BAJO_STOCK' AND estado = 'ACTIVA';
    END IF;

    -- Sobrestock: total > máximo
    IF v_total > v_maximo THEN
        SELECT COUNT(*) INTO v_activas
          FROM alerta
         WHERE id_producto = p_id_producto AND tipo = 'SOBRESTOCK' AND estado = 'ACTIVA';
        IF v_activas = 0 THEN
            INSERT INTO alerta (id_producto, tipo, stock_detectado, umbral, estado)
            VALUES (p_id_producto, 'SOBRESTOCK', v_total, v_maximo, 'ACTIVA');
        END IF;
    ELSE
        UPDATE alerta SET estado = 'RESUELTA'
         WHERE id_producto = p_id_producto AND tipo = 'SOBRESTOCK' AND estado = 'ACTIVA';
    END IF;
END $$

-- ---------------------------------------------------------------------
-- 2. sp_autorizar_movimiento (RF-010 y RF-011)
-- Autoriza un movimiento PENDIENTE (entrada, salida crítica o ajuste
-- negativo), actualiza la existencia y revisa las alertas, todo en una
-- sola transacción. Si algo falla, ROLLBACK y el error llega a Java.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_autorizar_movimiento $$
CREATE PROCEDURE sp_autorizar_movimiento(IN p_id_movimiento INT, IN p_id_autorizador INT,
                                         OUT p_mensaje VARCHAR(200))
BEGIN
    DECLARE v_estado VARCHAR(12);
    DECLARE v_id_registra INT;
    DECLARE v_id_producto INT;
    DECLARE v_cantidad INT;
    DECLARE v_origen INT;
    DECLARE v_destino INT;
    DECLARE v_naturaleza VARCHAR(15);
    DECLARE v_rol_requerido INT;
    DECLARE v_estado_usuario VARCHAR(10);
    DECLARE v_rol_usuario INT;
    DECLARE v_disponible INT;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;   -- la aplicación recibe el mismo error con su mensaje
    END;

    SET p_mensaje = NULL;
    START TRANSACTION;

    -- P1-S1: el movimiento y su tipo, bloqueados hasta el COMMIT
    SELECT m.estado, m.id_usuario, m.id_producto, m.cantidad,
           m.id_bodega_origen, m.id_bodega_destino, t.naturaleza, t.id_rol_autorizador
      INTO v_estado, v_id_registra, v_id_producto, v_cantidad,
           v_origen, v_destino, v_naturaleza, v_rol_requerido
      FROM movimiento_inventario m
     INNER JOIN tipo_movimiento t ON t.id_tipo_movimiento = m.id_tipo_movimiento
     WHERE m.id_movimiento = p_id_movimiento
       FOR UPDATE;

    IF v_estado IS NULL OR v_estado <> 'PENDIENTE' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El movimiento no existe o ya fue resuelto';
    END IF;

    -- P1-S2: estado y rol de quien autoriza
    SELECT u.estado, u.id_rol
      INTO v_estado_usuario, v_rol_usuario
      FROM usuario u
     WHERE u.id_usuario = p_id_autorizador;

    IF v_estado_usuario IS NULL OR v_estado_usuario <> 'ACTIVO'
       OR v_rol_requerido IS NULL OR v_rol_usuario <> v_rol_requerido THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'El usuario no está activo o no tiene el rol para autorizar este movimiento';
    END IF;

    IF p_id_autorizador = v_id_registra THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Quien registra un movimiento no puede autorizarlo';
    END IF;

    IF v_naturaleza = 'ENTRADA' THEN
        -- P1-S3: suma en la bodega destino (crea la fila si no existe)
        INSERT INTO existencia (id_producto, id_bodega, cantidad)
        VALUES (v_id_producto, v_destino, v_cantidad)
        ON DUPLICATE KEY UPDATE cantidad = cantidad + v_cantidad;
    ELSEIF v_naturaleza = 'SALIDA' THEN
        -- P1-S4: existencia de origen bloqueada
        SELECT cantidad INTO v_disponible
          FROM existencia
         WHERE id_producto = v_id_producto AND id_bodega = v_origen
           FOR UPDATE;
        IF v_disponible IS NULL OR v_disponible < v_cantidad THEN
            SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stock insuficiente en la bodega de origen';
        END IF;
        -- P1-S5: resta en la bodega de origen
        UPDATE existencia
           SET cantidad = cantidad - v_cantidad
         WHERE id_producto = v_id_producto AND id_bodega = v_origen
           AND cantidad >= v_cantidad;
    ELSE
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Este tipo de movimiento no necesita autorización';
    END IF;

    -- P1-S6: el movimiento queda AUTORIZADO
    UPDATE movimiento_inventario
       SET estado = 'AUTORIZADO',
           id_usuario_autoriza = p_id_autorizador,
           fecha_autorizacion = NOW()
     WHERE id_movimiento = p_id_movimiento
       AND estado = 'PENDIENTE';

    -- P1-S7: revisar las alertas del producto dentro de la misma transacción
    CALL sp_evaluar_alertas_producto(v_id_producto);

    COMMIT;
    SET p_mensaje = 'Movimiento autorizado';
END $$

-- ---------------------------------------------------------------------
-- 3. sp_registrar_transferencia (RF-008)
-- Registra la transferencia ya AUTORIZADA y mueve las existencias en una
-- sola transacción. Devuelve el número del movimiento creado.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_registrar_transferencia $$
CREATE PROCEDURE sp_registrar_transferencia(IN p_id_producto INT, IN p_id_origen INT, IN p_id_destino INT,
                                            IN p_cantidad INT, IN p_id_usuario INT,
                                            IN p_observacion VARCHAR(255), OUT p_id_movimiento INT)
BEGIN
    DECLARE v_validos INT DEFAULT 0;
    DECLARE v_estado_usuario VARCHAR(10);
    DECLARE v_rol VARCHAR(30);
    DECLARE v_id_tipo INT;
    DECLARE v_disponible INT DEFAULT 0;

    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    SET p_id_movimiento = NULL;

    -- Validaciones sencillas antes de abrir la transacción
    IF p_cantidad IS NULL OR p_cantidad <= 0 OR p_id_origen = p_id_destino THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'La cantidad debe ser mayor que cero y las bodegas deben ser distintas';
    END IF;

    -- P2-S1: producto activo y dos bodegas activas (debe dar 3)
    SELECT (SELECT COUNT(*) FROM producto
             WHERE id_producto = p_id_producto AND estado = 'ACTIVO')
         + (SELECT COUNT(*) FROM bodega
             WHERE id_bodega IN (p_id_origen, p_id_destino) AND estado = 'ACTIVO')
      INTO v_validos;
    IF v_validos <> 3 THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El producto o alguna de las bodegas no existe o está inactiva';
    END IF;

    -- P2-S2: estado y rol de quien registra
    SELECT u.estado, r.nombre_rol
      INTO v_estado_usuario, v_rol
      FROM usuario u
     INNER JOIN rol r ON r.id_rol = u.id_rol
     WHERE u.id_usuario = p_id_usuario;
    IF v_estado_usuario IS NULL OR v_estado_usuario <> 'ACTIVO' OR v_rol <> 'Almacenista' THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'El usuario no tiene permiso para registrar movimientos';
    END IF;

    -- P2-S3: tipo de movimiento Transferencia
    SELECT id_tipo_movimiento INTO v_id_tipo
      FROM tipo_movimiento
     WHERE nombre = 'Transferencia';

    START TRANSACTION;

    -- P2-S4: bloquea las dos existencias (en el orden de la llave primaria)
    SELECT COALESCE(SUM(CASE WHEN id_bodega = p_id_origen THEN cantidad ELSE 0 END), 0)
      INTO v_disponible
      FROM existencia
     WHERE id_producto = p_id_producto
       AND id_bodega IN (p_id_origen, p_id_destino)
       FOR UPDATE;
    IF v_disponible < p_cantidad THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Stock insuficiente en la bodega de origen';
    END IF;

    -- P2-S5 y P2-S6: el movimiento y su número
    INSERT INTO movimiento_inventario
        (id_producto, id_tipo_movimiento, id_bodega_origen, id_bodega_destino,
         id_usuario, estado, cantidad, fecha, observacion)
    VALUES (p_id_producto, v_id_tipo, p_id_origen, p_id_destino,
            p_id_usuario, 'AUTORIZADO', p_cantidad, NOW(), p_observacion);
    SET p_id_movimiento = LAST_INSERT_ID();

    -- P2-S7: resta en el origen
    UPDATE existencia
       SET cantidad = cantidad - p_cantidad
     WHERE id_producto = p_id_producto AND id_bodega = p_id_origen
       AND cantidad >= p_cantidad;

    -- P2-S8: suma en el destino (crea la fila si no existe)
    INSERT INTO existencia (id_producto, id_bodega, cantidad)
    VALUES (p_id_producto, p_id_destino, p_cantidad)
    ON DUPLICATE KEY UPDATE cantidad = cantidad + p_cantidad;

    COMMIT;
END $$

-- ---------------------------------------------------------------------
-- 4. sp_consultar_existencias (RF-013)
-- Consulta: devuelve las existencias de un producto (o de todos, si el
-- parámetro llega NULL) y el total de unidades en un parámetro de salida.
-- ---------------------------------------------------------------------
DROP PROCEDURE IF EXISTS sp_consultar_existencias $$
CREATE PROCEDURE sp_consultar_existencias(IN p_id_producto INT, OUT p_total INT)
BEGIN
    SELECT p.codigo, p.nombre AS producto, b.codigo AS codigo_bodega, b.nombre AS bodega, e.cantidad
      FROM existencia e
     INNER JOIN producto p ON p.id_producto = e.id_producto
     INNER JOIN bodega b ON b.id_bodega = e.id_bodega
     WHERE (p_id_producto IS NULL OR e.id_producto = p_id_producto)
     ORDER BY p.codigo, b.codigo;

    SELECT COALESCE(SUM(cantidad), 0) INTO p_total
      FROM existencia
     WHERE (p_id_producto IS NULL OR id_producto = p_id_producto);
END $$

DELIMITER ;

-- Verificación: deben salir los cuatro procedimientos
SHOW PROCEDURE STATUS WHERE Db = 'aristools';
