-- =====================================================================
-- Aristools - Actualización v7: sugerencia de compra y orden de compra
-- Ejecutar UNA SOLA VEZ en MySQL Workbench (con el rayo que ejecuta todo).
-- No borra datos. (Una instalación nueva con aristools.sql ya lo incluye.)
-- Si sale "Duplicate column" o "Table already exists", ya estaba ejecutado.
-- =====================================================================
USE aristools;

-- 1) Días que tarda cada proveedor en entregar un pedido.
--    Vacío: el sistema usa el parámetro general dias_entrega_defecto.
ALTER TABLE proveedor
    ADD COLUMN dias_entrega INT NULL AFTER estado,
    ADD CONSTRAINT ck_proveedor_dias CHECK (dias_entrega IS NULL OR dias_entrega BETWEEN 0 AND 180);

-- 2) Órdenes de compra y sus productos.
-- Orden de compra: pedido que el Jefe de Compras envía al proveedor a partir de
-- la sugerencia de compra. El número del documento es OC- más el id.
CREATE TABLE orden_compra (
    id_orden      INT           NOT NULL AUTO_INCREMENT,
    id_proveedor  INT           NOT NULL,
    id_usuario    INT           NOT NULL,             -- quien la genera
    fecha         DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    total         DECIMAL(14,2) NOT NULL DEFAULT 0,   -- valor estimado al costo de la última compra
    observacion   VARCHAR(255)  NULL,
    CONSTRAINT pk_orden_compra PRIMARY KEY (id_orden),
    CONSTRAINT fk_orden_proveedor FOREIGN KEY (id_proveedor) REFERENCES proveedor (id_proveedor),
    CONSTRAINT fk_orden_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario),
    CONSTRAINT ck_orden_total CHECK (total >= 0)
) ENGINE = InnoDB;

-- Productos de cada orden de compra (llave compuesta: un producto una vez por orden)
CREATE TABLE orden_compra_detalle (
    id_orden        INT           NOT NULL,
    id_producto     INT           NOT NULL,
    cantidad        INT           NOT NULL,
    costo_unitario  DECIMAL(14,4) NOT NULL DEFAULT 0,
    CONSTRAINT pk_orden_detalle PRIMARY KEY (id_orden, id_producto),
    CONSTRAINT fk_detalle_orden FOREIGN KEY (id_orden) REFERENCES orden_compra (id_orden),
    CONSTRAINT fk_detalle_producto FOREIGN KEY (id_producto) REFERENCES producto (id_producto),
    CONSTRAINT ck_detalle_cantidad CHECK (cantidad > 0),
    CONSTRAINT ck_detalle_costo CHECK (costo_unitario >= 0)
) ENGINE = InnoDB;

-- 3) Parámetro general de días de entrega (el Administrador lo cambia en Configuración).
INSERT INTO parametro (clave, valor, descripcion) VALUES
('dias_entrega_defecto', '5', 'Días de entrega para los proveedores que no tienen los suyos (sugerencia de compra)')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- 4) Días de entrega de los proveedores de prueba (si existen).
--    (Se filtra por la llave primaria para que Workbench lo permita en modo seguro.)
UPDATE proveedor SET dias_entrega = 3 WHERE id_proveedor > 0 AND nit = '900123456-1';
UPDATE proveedor SET dias_entrega = 7 WHERE id_proveedor > 0 AND nit = '900654321-2';
UPDATE proveedor SET dias_entrega = 4 WHERE id_proveedor > 0 AND nit = '901456789-2';
UPDATE proveedor SET dias_entrega = 2 WHERE id_proveedor > 0 AND nit = '900777888-5';

-- 5) Verificación: la columna nueva, las dos tablas nuevas y el parámetro.
SHOW COLUMNS FROM proveedor LIKE 'dias_entrega';
SHOW TABLES LIKE 'orden_compra%';
SELECT razon_social, dias_entrega FROM proveedor ORDER BY razon_social;
SELECT clave, valor FROM parametro WHERE clave = 'dias_entrega_defecto';
