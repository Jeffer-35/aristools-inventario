-- =====================================================================
-- Aristools - Actualización v8: vencimientos (FEFO), clasificación ABC,
-- conteo cíclico, conteo a ciegas e historial de conteos
-- Ejecutar UNA SOLA VEZ en MySQL Workbench (con el rayo que ejecuta todo).
-- No borra datos. (Una instalación nueva con aristools.sql ya lo incluye.)
-- Si sale "Duplicate column" o "Table already exists", ya estaba ejecutado.
-- =====================================================================
USE aristools;

-- 1) Productos que vencen: sus compras exigen lote y fecha de vencimiento.
ALTER TABLE producto
    ADD COLUMN controla_vencimiento BOOLEAN NOT NULL DEFAULT FALSE AFTER estado;

-- 2) Lote y fecha de vencimiento de cada compra.
ALTER TABLE movimiento_inventario
    ADD COLUMN lote VARCHAR(30) NULL AFTER observacion,
    ADD COLUMN fecha_vencimiento DATE NULL AFTER lote;

-- 3) Historial de conteos físicos (general o cíclico, a ciegas o no).
CREATE TABLE conteo (
    id_conteo           INT         NOT NULL AUTO_INCREMENT,
    id_bodega           INT         NOT NULL,
    id_usuario          INT         NOT NULL,
    fecha               DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    tipo                VARCHAR(10) NOT NULL DEFAULT 'GENERAL',
    clase_abc           CHAR(1)     NULL,
    a_ciegas            BOOLEAN     NOT NULL DEFAULT FALSE,
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

-- 4) Días de anticipación para avisar que un lote está por vencer.
INSERT INTO parametro (clave, valor, descripcion) VALUES
('dias_aviso_vencimiento', '30', 'Días de anticipación con que se avisa que un lote está por vencer')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- 5) La pintura de prueba queda como producto con vencimiento (si existe).
UPDATE producto SET controla_vencimiento = TRUE WHERE id_producto > 0 AND codigo = 'PIN-001';

-- 6) Verificación: las columnas nuevas, las dos tablas nuevas y el parámetro.
SHOW COLUMNS FROM producto LIKE 'controla_vencimiento';
SHOW COLUMNS FROM movimiento_inventario LIKE 'fecha_vencimiento';
SHOW COLUMNS FROM movimiento_inventario LIKE 'lote';
SHOW TABLES LIKE 'conteo%';
SELECT clave, valor FROM parametro WHERE clave = 'dias_aviso_vencimiento';
