-- =====================================================================
-- Proyecto Integrador - Segunda Entrega
-- Sistema de Gestión de Inventarios - Ferretería Aristools
-- Archivo 1 de 2: aristools.sql
-- Crea la base de datos, las 16 tablas y los datos obligatorios del
-- sistema (roles, tipos de movimiento y parámetros globales).
-- Corresponde a la Figura 28 (modelo entidad-relación) y a la Figura 29
-- (diagrama de datos) del documento. Motor: MySQL 8.x.
--
-- Orden de ejecución: 1) aristools.sql   2) datos_iniciales.sql
-- ATENCIÓN: este script borra la base aristools si ya existe.
-- =====================================================================

DROP DATABASE IF EXISTS aristools;
CREATE DATABASE aristools CHARACTER SET utf8mb4 COLLATE utf8mb4_spanish_ci;
USE aristools;

-- ---------------------------------------------------------------------
-- 1. Seguridad: roles y usuarios (RF-001, RF-002)
-- ---------------------------------------------------------------------
CREATE TABLE rol (
    id_rol        INT          NOT NULL AUTO_INCREMENT,
    nombre_rol    VARCHAR(30)  NOT NULL,
    descripcion   VARCHAR(150) NULL,
    CONSTRAINT pk_rol PRIMARY KEY (id_rol),
    CONSTRAINT uq_rol_nombre UNIQUE (nombre_rol)
) ENGINE = InnoDB;

CREATE TABLE usuario (
    id_usuario        INT          NOT NULL AUTO_INCREMENT,
    nombre_completo   VARCHAR(100) NOT NULL,
    documento         VARCHAR(20)  NOT NULL,
    correo            VARCHAR(100) NOT NULL,
    nombre_usuario    VARCHAR(30)  NOT NULL,
    contrasena_hash   VARCHAR(255) NOT NULL,
    id_rol            INT          NOT NULL,
    estado            VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    ultimo_acceso     DATETIME     NULL,
    fecha_cambio_clave DATETIME    NULL,                    -- último cambio de contraseña (vencimiento)
    debe_cambiar_clave BOOLEAN     NOT NULL DEFAULT FALSE,  -- clave temporal: cambiarla al ingresar
    CONSTRAINT pk_usuario PRIMARY KEY (id_usuario),
    CONSTRAINT uq_usuario_documento UNIQUE (documento),
    CONSTRAINT uq_usuario_correo UNIQUE (correo),
    CONSTRAINT uq_usuario_nombre UNIQUE (nombre_usuario),
    CONSTRAINT fk_usuario_rol FOREIGN KEY (id_rol) REFERENCES rol (id_rol),
    CONSTRAINT ck_usuario_estado CHECK (estado IN ('ACTIVO', 'INACTIVO', 'BLOQUEADO')),
    CONSTRAINT ck_usuario_intentos CHECK (intentos_fallidos BETWEEN 0 AND 3)
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 2. Catálogos: categorías, productos, bodegas y proveedores (RF-003 a RF-005)
-- ---------------------------------------------------------------------
CREATE TABLE categoria (
    id_categoria  INT          NOT NULL AUTO_INCREMENT,
    nombre        VARCHAR(50)  NOT NULL,
    descripcion   VARCHAR(150) NULL,
    estado        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    CONSTRAINT pk_categoria PRIMARY KEY (id_categoria),
    CONSTRAINT uq_categoria_nombre UNIQUE (nombre),
    CONSTRAINT ck_categoria_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB;

CREATE TABLE producto (
    id_producto       INT           NOT NULL AUTO_INCREMENT,
    codigo            VARCHAR(20)   NOT NULL,
    nombre            VARCHAR(100)  NOT NULL,
    descripcion       VARCHAR(255)  NULL,
    id_categoria      INT           NOT NULL,
    unidad_medida     VARCHAR(20)   NOT NULL,
    costo_referencia  DECIMAL(12,2) NOT NULL DEFAULT 0,
    costo_promedio    DECIMAL(14,4) NOT NULL DEFAULT 0,  -- costo promedio ponderado: lo recalcula cada compra autorizada
    stock_minimo      INT           NULL,  -- NULL: usa el umbral por defecto de parametro
    stock_maximo      INT           NULL,
    estado            VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO',
    controla_vencimiento BOOLEAN    NOT NULL DEFAULT FALSE,  -- TRUE: sus compras exigen lote y fecha de vencimiento
    CONSTRAINT pk_producto PRIMARY KEY (id_producto),
    CONSTRAINT uq_producto_codigo UNIQUE (codigo),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES categoria (id_categoria),
    CONSTRAINT ck_producto_costo CHECK (costo_referencia >= 0),
    CONSTRAINT ck_producto_costo_promedio CHECK (costo_promedio >= 0),
    CONSTRAINT ck_producto_minimo CHECK (stock_minimo IS NULL OR stock_minimo >= 0),
    CONSTRAINT ck_producto_umbrales CHECK (stock_minimo IS NULL OR stock_maximo IS NULL
                                           OR stock_maximo >= stock_minimo),  -- RF-003: mínimo ≤ máximo
    CONSTRAINT ck_producto_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB;

CREATE TABLE bodega (
    id_bodega   INT          NOT NULL AUTO_INCREMENT,
    codigo      VARCHAR(20)  NOT NULL,
    nombre      VARCHAR(60)  NOT NULL,
    ubicacion   VARCHAR(150) NULL,
    estado      VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    es_cuarentena BOOLEAN    NOT NULL DEFAULT FALSE,  -- TRUE: guarda mercancía averiada mientras se decide la baja
    CONSTRAINT pk_bodega PRIMARY KEY (id_bodega),
    CONSTRAINT uq_bodega_codigo UNIQUE (codigo),
    CONSTRAINT uq_bodega_nombre UNIQUE (nombre),   -- RF-004: código y nombre únicos
    CONSTRAINT ck_bodega_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
) ENGINE = InnoDB;

CREATE TABLE proveedor (
    id_proveedor  INT          NOT NULL AUTO_INCREMENT,
    nit           VARCHAR(20)  NOT NULL,
    razon_social  VARCHAR(100) NOT NULL,
    contacto      VARCHAR(100) NULL,
    telefono      VARCHAR(20)  NULL,
    correo        VARCHAR(100) NULL,
    direccion     VARCHAR(150) NULL,
    estado        VARCHAR(10)  NOT NULL DEFAULT 'ACTIVO',
    dias_entrega  INT          NULL,  -- días que tarda en entregar un pedido; NULL: usa el parámetro general
    CONSTRAINT pk_proveedor PRIMARY KEY (id_proveedor),
    CONSTRAINT uq_proveedor_nit UNIQUE (nit),
    CONSTRAINT ck_proveedor_estado CHECK (estado IN ('ACTIVO', 'INACTIVO')),
    CONSTRAINT ck_proveedor_dias CHECK (dias_entrega IS NULL OR dias_entrega BETWEEN 0 AND 180)
) ENGINE = InnoDB;

-- Existencia: cantidad de cada producto en cada bodega (llave compuesta).
-- El stock total de un producto es la suma de sus existencias.
CREATE TABLE existencia (
    id_producto  INT NOT NULL,
    id_bodega    INT NOT NULL,
    cantidad     INT NOT NULL DEFAULT 0,
    CONSTRAINT pk_existencia PRIMARY KEY (id_producto, id_bodega),
    CONSTRAINT fk_existencia_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
    CONSTRAINT fk_existencia_bodega FOREIGN KEY (id_bodega) REFERENCES bodega (id_bodega),
    CONSTRAINT ck_existencia_cantidad CHECK (cantidad >= 0)   -- el stock nunca queda negativo
) ENGINE = InnoDB;

-- ---------------------------------------------------------------------
-- 3. Movimientos y autorizaciones (RF-006 a RF-011)
-- ---------------------------------------------------------------------
CREATE TABLE tipo_movimiento (
    id_tipo_movimiento     INT         NOT NULL AUTO_INCREMENT,
    nombre                 VARCHAR(40) NOT NULL,
    naturaleza             VARCHAR(15) NOT NULL,
    requiere_autorizacion  BOOLEAN     NOT NULL DEFAULT FALSE,
    id_rol_autorizador     INT         NULL,
    CONSTRAINT pk_tipo_movimiento PRIMARY KEY (id_tipo_movimiento),
    CONSTRAINT uq_tipo_movimiento_nombre UNIQUE (nombre),
    CONSTRAINT fk_tipo_movimiento_rol FOREIGN KEY (id_rol_autorizador) REFERENCES rol (id_rol),
    CONSTRAINT ck_tipo_movimiento_naturaleza CHECK (naturaleza IN ('ENTRADA', 'SALIDA', 'TRANSFERENCIA'))
) ENGINE = InnoDB;

CREATE TABLE movimiento_inventario (
    id_movimiento        INT          NOT NULL AUTO_INCREMENT,
    id_producto          INT          NOT NULL,
    id_tipo_movimiento   INT          NOT NULL,
    id_bodega_origen     INT          NULL,  -- salidas y transferencias
    id_bodega_destino    INT          NULL,  -- entradas y transferencias
    id_proveedor         INT          NULL,  -- compras y devoluciones a proveedor
    id_usuario           INT          NOT NULL,  -- quien registra
    estado               VARCHAR(12)  NOT NULL DEFAULT 'PENDIENTE',
    id_usuario_autoriza  INT          NULL,  -- quien autoriza o rechaza
    fecha_autorizacion   DATETIME     NULL,
    motivo_rechazo       VARCHAR(255) NULL,
    documento_soporte    VARCHAR(50)  NULL,
    motivo               VARCHAR(255) NULL,
    causa_baja           VARCHAR(20)  NULL,  -- causa estándar de la pérdida (mermas, daños y faltantes)
    cantidad             INT          NOT NULL,
    costo_unitario       DECIMAL(14,4) NULL,  -- compra: costo de la factura; los demás: costo promedio al aplicarse
    fecha                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacion          VARCHAR(255) NULL,
    lote                 VARCHAR(30)  NULL,  -- lote del fabricante (compras de productos con vencimiento)
    fecha_vencimiento    DATE         NULL,  -- fecha en que vence ese lote
    id_orden             INT          NULL,  -- orden de compra que se recibe con esta entrada (opcional)
    CONSTRAINT pk_movimiento PRIMARY KEY (id_movimiento),
    CONSTRAINT fk_movimiento_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
    CONSTRAINT fk_movimiento_tipo FOREIGN KEY (id_tipo_movimiento) REFERENCES tipo_movimiento (id_tipo_movimiento),
    CONSTRAINT fk_movimiento_bodega_origen FOREIGN KEY (id_bodega_origen) REFERENCES bodega (id_bodega),
    CONSTRAINT fk_movimiento_bodega_destino FOREIGN KEY (id_bodega_destino) REFERENCES bodega (id_bodega),
    CONSTRAINT fk_movimiento_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor),
    CONSTRAINT fk_movimiento_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    CONSTRAINT fk_movimiento_usuario_autoriza FOREIGN KEY (id_usuario_autoriza) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_movimiento_estado CHECK (estado IN ('PENDIENTE', 'AUTORIZADO', 'RECHAZADO')),
    CONSTRAINT ck_movimiento_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_movimiento_costo CHECK (costo_unitario IS NULL OR costo_unitario >= 0),
    CONSTRAINT ck_movimiento_causa CHECK (causa_baja IS NULL OR causa_baja IN
        ('VENCIDO', 'ROTO_O_GOLPEADO', 'DANO_TRANSPORTE', 'HUMEDAD', 'DEFECTO_FABRICA', 'FALTANTE', 'OTRO')),
    -- Segregación de funciones: quien registra no puede autorizar (RF-010, RF-011)
    CONSTRAINT ck_movimiento_segregacion CHECK (id_usuario_autoriza IS NULL OR id_usuario_autoriza <> id_usuario),
    -- Todo movimiento afecta al menos una bodega (origen, destino o ambas)
    CONSTRAINT ck_movimiento_bodega CHECK (id_bodega_origen IS NOT NULL OR id_bodega_destino IS NOT NULL)
) ENGINE = InnoDB;

-- Índices para las consultas de kardex, reportes y bandejas de autorización (RF-010, RF-011, RF-014)
CREATE INDEX ix_movimiento_producto_fecha ON movimiento_inventario (id_producto, fecha);
CREATE INDEX ix_movimiento_estado ON movimiento_inventario (estado);

-- ---------------------------------------------------------------------
-- 4. Alertas y parámetros (RF-012, RF-015)
-- ---------------------------------------------------------------------
CREATE TABLE alerta (
    id_alerta         INT         NOT NULL AUTO_INCREMENT,
    id_producto       INT         NOT NULL,
    tipo              VARCHAR(12) NOT NULL,
    stock_detectado   INT         NOT NULL,
    umbral            INT         NOT NULL,
    fecha_generacion  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    estado            VARCHAR(10) NOT NULL DEFAULT 'ACTIVA',
    CONSTRAINT pk_alerta PRIMARY KEY (id_alerta),
    CONSTRAINT fk_alerta_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
    CONSTRAINT ck_alerta_tipo CHECK (tipo IN ('BAJO_STOCK', 'SOBRESTOCK')),
    CONSTRAINT ck_alerta_estado CHECK (estado IN ('ACTIVA', 'RESUELTA'))
) ENGINE = InnoDB;

CREATE INDEX ix_alerta_estado ON alerta (estado);

CREATE TABLE parametro (
    id_parametro  INT          NOT NULL AUTO_INCREMENT,
    clave         VARCHAR(50)  NOT NULL,
    valor         VARCHAR(100) NOT NULL,
    descripcion   VARCHAR(150) NULL,
    CONSTRAINT pk_parametro PRIMARY KEY (id_parametro),
    CONSTRAINT uq_parametro_clave UNIQUE (clave)
) ENGINE = InnoDB;

-- =====================================================================
-- DATOS OBLIGATORIOS DEL SISTEMA
-- (sin estos registros la aplicación no puede funcionar)
-- =====================================================================

-- Orden de compra: pedido que el Jefe de Compras envía al proveedor a partir de
-- la sugerencia de compra. El número del documento es OC- más el id.
CREATE TABLE orden_compra (
    id_orden      INT           NOT NULL AUTO_INCREMENT,
    id_proveedor  INT           NOT NULL,
    id_usuario    INT           NOT NULL,             -- quien la genera
    fecha         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total         DECIMAL(14,2) NOT NULL DEFAULT 0,   -- valor estimado al costo de la última compra
    observacion   VARCHAR(255)  NULL,
    estado        VARCHAR(10)   NOT NULL DEFAULT 'PENDIENTE',  -- PENDIENTE, PARCIAL, RECIBIDA o ANULADA
    motivo_anulacion VARCHAR(255) NULL,
    CONSTRAINT pk_orden_compra PRIMARY KEY (id_orden),
    CONSTRAINT fk_orden_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor),
    CONSTRAINT fk_orden_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_orden_total CHECK (total >= 0),
    CONSTRAINT ck_orden_estado CHECK (estado IN ('PENDIENTE', 'PARCIAL', 'RECIBIDA', 'ANULADA'))
) ENGINE = InnoDB;

-- Productos de cada orden de compra (llave compuesta: un producto una vez por orden)
CREATE TABLE orden_compra_detalle (
    id_orden        INT           NOT NULL,
    id_producto     INT           NOT NULL,
    cantidad        INT           NOT NULL,
    costo_unitario  DECIMAL(14,4) NOT NULL DEFAULT 0,
    cantidad_recibida INT         NOT NULL DEFAULT 0,  -- lo que ya llegó con entradas autorizadas
    CONSTRAINT pk_orden_detalle PRIMARY KEY (id_orden, id_producto),
    CONSTRAINT fk_detalle_orden FOREIGN KEY (id_orden) REFERENCES orden_compra (id_orden),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_costo CHECK (costo_unitario >= 0),
    CONSTRAINT ck_detalle_recibida CHECK (cantidad_recibida BETWEEN 0 AND cantidad)
) ENGINE = InnoDB;

-- La entrada que recibe una orden de compra queda enlazada con ella. La llave
-- se agrega aquí porque movimiento_inventario se crea antes que orden_compra.
ALTER TABLE movimiento_inventario
    ADD CONSTRAINT fk_movimiento_orden FOREIGN KEY (id_orden) REFERENCES orden_compra (id_orden);

-- Historial de conteos físicos: cada vez que se carga un conteo queda guardado
-- con lo que decía el sistema y lo que se contó, haya o no diferencias.
CREATE TABLE conteo (
    id_conteo           INT         NOT NULL AUTO_INCREMENT,
    id_bodega           INT         NOT NULL,
    id_usuario          INT         NOT NULL,             -- quien contó
    fecha               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tipo                VARCHAR(10) NOT NULL DEFAULT 'GENERAL',  -- GENERAL: toda la bodega; CICLICO: una clase ABC
    clase_abc           CHAR(1)     NULL,                 -- clase contada en el conteo cíclico
    a_ciegas            BOOLEAN     NOT NULL DEFAULT FALSE,  -- TRUE: la plantilla no mostraba el stock del sistema
    productos_contados  INT         NOT NULL,
    con_diferencia      INT         NOT NULL,
    CONSTRAINT pk_conteo PRIMARY KEY (id_conteo),
    CONSTRAINT fk_conteo_bodega FOREIGN KEY (id_bodega) REFERENCES bodega (id_bodega),
    CONSTRAINT fk_conteo_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_conteo_tipo CHECK (tipo IN ('GENERAL', 'CICLICO')),
    CONSTRAINT ck_conteo_clase CHECK (clase_abc IS NULL OR clase_abc IN ('A', 'B', 'C')),
    CONSTRAINT ck_conteo_cantidades CHECK (productos_contados > 0 AND con_diferencia BETWEEN 0 AND productos_contados)
) ENGINE = InnoDB;

CREATE TABLE conteo_detalle (
    id_conteo         INT NOT NULL,
    id_producto       INT NOT NULL,
    stock_sistema     INT NOT NULL,
    cantidad_contada  INT NOT NULL,
    CONSTRAINT pk_conteo_detalle PRIMARY KEY (id_conteo, id_producto),
    CONSTRAINT fk_conteo_detalle_conteo FOREIGN KEY (id_conteo) REFERENCES conteo (id_conteo),
    CONSTRAINT fk_conteo_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
    CONSTRAINT ck_conteo_detalle_contada CHECK (cantidad_contada >= 0)
) ENGINE = InnoDB;

-- Roles del sistema (los id quedan: 1 Administrador del Sistema,
-- 2 Almacenista, 3 Jefe de Compras, 4 Supervisor de Bodega)
-- Bitácora de auditoría: registro de solo inserción de las acciones sensibles
-- (ingresos, contraseñas, usuarios, catálogos y parámetros). No se modifica ni se borra.
CREATE TABLE bitacora (
    id_bitacora    INT          NOT NULL AUTO_INCREMENT,
    fecha          DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    id_usuario     INT          NULL,               -- NULL si el usuario escrito no existe
    nombre_usuario VARCHAR(60)  NULL,               -- se guarda el texto por si el usuario no existe
    accion         VARCHAR(30)  NOT NULL,
    detalle        VARCHAR(500) NOT NULL,
    CONSTRAINT pk_bitacora PRIMARY KEY (id_bitacora),
    CONSTRAINT fk_bitacora_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    INDEX ix_bitacora_fecha (fecha),
    INDEX ix_bitacora_accion (accion)
) ENGINE = InnoDB;

-- La bitácora es inalterable: ni la aplicación ni nadie desde Workbench puede cambiarla o borrarla
DELIMITER $$
CREATE TRIGGER trg_bitacora_no_modificar BEFORE UPDATE ON bitacora FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La bitácora no se puede modificar.';
END$$
CREATE TRIGGER trg_bitacora_no_borrar BEFORE DELETE ON bitacora FOR EACH ROW
BEGIN
    SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'La bitácora no se puede borrar.';
END$$
DELIMITER ;

INSERT INTO rol (nombre_rol, descripcion) VALUES
('Administrador del Sistema', 'Configura el sistema, los usuarios, los catálogos y los parámetros'),
('Almacenista',               'Registra entradas, salidas, transferencias y conteos físicos'),
('Jefe de Compras',           'Gestiona proveedores y autoriza las entradas de mercancía'),
('Supervisor de Bodega',      'Autoriza salidas críticas y ajustes negativos');

-- Tipos de movimiento (catálogo definido en 3.2.2 y en las fichas RF-006 a RF-011)
INSERT INTO tipo_movimiento (nombre, naturaleza, requiere_autorizacion, id_rol_autorizador) VALUES
('Compra',                    'ENTRADA',       TRUE,  3),
('Devolución',                'ENTRADA',       TRUE,  3),
('Consumo interno',           'SALIDA',        FALSE, NULL),
('Merma',                     'SALIDA',        TRUE,  4),
('Producto dañado o vencido', 'SALIDA',        TRUE,  4),
('Devolución a proveedor',    'SALIDA',        TRUE,  4),
('Transferencia',             'TRANSFERENCIA', FALSE, NULL),
('Ajuste positivo',           'ENTRADA',       FALSE, NULL),
('Ajuste negativo',           'SALIDA',        TRUE,  4);

-- Parámetros globales (RF-015)
INSERT INTO parametro (clave, valor, descripcion) VALUES
('nombre_establecimiento', 'Ferretería Aristools', 'Nombre que aparece en pantallas y reportes'),
('stock_minimo_defecto',   '10',  'Umbral mínimo para productos sin stock mínimo propio'),
('stock_maximo_defecto',   '100', 'Umbral máximo para productos sin stock máximo propio'),
('dias_vigencia_clave',    '0',   'Días de vigencia de la contraseña (0 = sin vencimiento, recomendación NIST SP 800-63B)'),
('minutos_inactividad',    '15',  'Minutos sin actividad para cerrar la sesión (0 = desactivado)'),
('responsable_revision',   'Supervisor de Bodega', 'Nombre y cargo de quien revisa los reportes impresos (sale como Revisó en los PDF)'),
('dias_entrega_defecto',   '5',   'Días de entrega para los proveedores que no tienen los suyos (sugerencia de compra)'),
('dias_aviso_vencimiento', '30',  'Días de anticipación con que se avisa que un lote está por vencer'),
('dias_respaldo_automatico', '7', 'Cada cuántos días se hace el respaldo automático de la base (0 = desactivado)');


-- Nota para la aplicación:
-- * Los tipos que no requieren autorización (consumo interno,
--   transferencia y ajuste positivo) se guardan con estado 'AUTORIZADO';
--   los demás quedan 'PENDIENTE' hasta que el rol autorizador decida.
-- * Las reglas que comparan dos columnas con llave foránea (quien
--   autoriza debe ser distinto de quien registra; bodega de origen
--   distinta de la de destino) se validan en el Controlador (MVC).
-- * Las contraseñas se guardan con BCrypt (RNF-001). Si no existe ningún
--   Administrador del Sistema activo, la aplicación pide crear el primero
--   al iniciar; por eso este archivo no trae usuarios.

-- ---------------------------------------------------------------------
-- Procedimientos almacenados (Bases de Datos 2): ver database/procedimientos.sql
-- ---------------------------------------------------------------------
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
