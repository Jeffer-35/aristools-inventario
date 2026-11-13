-- =====================================================================
-- Proyecto Integrador - Segunda Entrega
-- Sistema de Gestión de Inventarios - Ferretería Aristools
-- Archivo 1 de 2: aristools.sql
-- Crea la base de datos, las 11 tablas y los datos obligatorios del
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
    stock_minimo      INT           NULL,  -- NULL: usa el umbral por defecto de parametro
    stock_maximo      INT           NULL,
    estado            VARCHAR(10)   NOT NULL DEFAULT 'ACTIVO',
    CONSTRAINT pk_producto PRIMARY KEY (id_producto),
    CONSTRAINT uq_producto_codigo UNIQUE (codigo),
    CONSTRAINT fk_producto_categoria FOREIGN KEY (id_categoria) REFERENCES categoria (id_categoria),
    CONSTRAINT ck_producto_costo CHECK (costo_referencia >= 0),
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
    CONSTRAINT pk_proveedor PRIMARY KEY (id_proveedor),
    CONSTRAINT uq_proveedor_nit UNIQUE (nit),
    CONSTRAINT ck_proveedor_estado CHECK (estado IN ('ACTIVO', 'INACTIVO'))
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
    cantidad             INT          NOT NULL,
    fecha                DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    observacion          VARCHAR(255) NULL,
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

-- Roles del sistema (los id quedan: 1 Administrador del Sistema,
-- 2 Almacenista, 3 Jefe de Compras, 4 Supervisor de Bodega)
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
('minutos_inactividad',    '15',  'Minutos sin actividad para cerrar la sesión (0 = desactivado)');


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
