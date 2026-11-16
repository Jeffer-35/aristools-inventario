-- =====================================================================
-- Proyecto Integrador - Segunda Entrega
-- Sistema de Gestión de Inventarios - Ferretería Aristools
-- Archivo 2 de 2: datos_iniciales.sql  (DATOS DE PRUEBA)
-- Ejecutar DESPUÉS de aristools.sql.
--
-- Usuarios de prueba (uno por rol). Contraseña de los cuatro:
--     Aristools2026*
-- Las contraseñas se guardan como hash BCrypt ($2a$, costo 10), cada una
-- con su propia sal; por eso los cuatro hashes son distintos aunque la
-- contraseña sea la misma. Son datos ficticios: cambiarlos antes de usar
-- el sistema con información real.
-- =====================================================================

USE aristools;

INSERT INTO usuario (nombre_completo, documento, correo, nombre_usuario, contrasena_hash, id_rol) VALUES
('Administrador General', '1000000001', 'admin@aristools.com',      'admin',      '$2a$10$uAMEtfnrGDRMuJWHbsLLYuwpuuE6NEfjkNngFuFSTFi.UMGt3M7ey', 1),
('Almacenista de Prueba', '1000000002', 'almacen@aristools.com',    'almacen',    '$2a$10$2hW6cLnkFA3fw2S1k6sN0u8qB8gPsCyNuyx4qrzZJEwcZ9NPTdxkK', 2),
('Jefe de Compras',       '1000000003', 'compras@aristools.com',    'compras',    '$2a$10$zL1gBtAKmtUxHiV1MvoOEu9SHwXEsP2RFPsg4Ois7yuZhyT8NCYii', 3),
('Supervisor de Bodega',  '1000000004', 'supervisor@aristools.com', 'supervisor', '$2a$10$l3o/qkGuIfJyTsOAzqTpy.XRFrYTFvsHyRqIk2Ksftqw9ZwqqCPGm', 4);

-- Catálogos de ejemplo
INSERT INTO categoria (nombre, descripcion) VALUES
('Herramientas manuales',  'Martillos, destornilladores, llaves y similares'),
('Tornillería',            'Tornillos, tuercas, arandelas y chazos'),
('Pinturas',               'Pinturas, solventes y accesorios'),
('Material eléctrico',     'Cables, interruptores y tomas');

INSERT INTO bodega (codigo, nombre, ubicacion) VALUES
('BOD-01', 'Bodega principal', 'Primer piso, zona posterior'),
('BOD-02', 'Bodega de mostrador', 'Área de atención al cliente');

INSERT INTO proveedor (nit, razon_social, contacto, telefono, correo, direccion) VALUES
('900123456-1', 'Distribuidora Ferretera del Valle S.A.S.', 'Carlos Gómez', '3001234567', 'ventas@distriferrevalle.com', 'Calle 10 # 5-20'),
('900654321-2', 'Pinturas y Acabados Ltda.',                'Laura Ríos',   '3109876543', 'pedidos@pinturasyacabados.com', 'Carrera 8 # 12-45');

INSERT INTO producto (codigo, nombre, descripcion, id_categoria, unidad_medida, costo_referencia, stock_minimo, stock_maximo) VALUES
('HER-001', 'Martillo de uña 16 oz',      'Mango de fibra de vidrio',  1, 'Unidad',  25000.00, 5,    50),
('TOR-001', 'Tornillo drywall 6x1"',      'Caja por 100 unidades',     2, 'Caja',    12000.00, 10,  100),
('PIN-001', 'Pintura vinilo blanco 1 gal','Tipo 1 interior/exterior',  3, 'Galón',   48000.00, 8,    40),
('ELE-001', 'Cable eléctrico No. 12',     'Rollo de 100 metros',       4, 'Rollo',  185000.00, NULL, NULL);

-- La pintura es un producto con fecha de vencimiento: sus compras piden lote y fecha.
UPDATE producto SET controla_vencimiento = TRUE WHERE id_producto > 0 AND codigo = 'PIN-001';

-- Inventario inicial. Para respetar la regla "el stock solo cambia por
-- movimientos" (y que el kardex cuadre), cada existencia inicial se
-- registra también como un Ajuste positivo autorizado.
INSERT INTO movimiento_inventario
    (id_producto, id_tipo_movimiento, id_bodega_destino, id_usuario, estado,
     documento_soporte, motivo, cantidad)
VALUES
(1, 8, 1, 2, 'AUTORIZADO', 'INV-INICIAL', 'Inventario inicial', 20),
(1, 8, 2, 2, 'AUTORIZADO', 'INV-INICIAL', 'Inventario inicial', 5),
(2, 8, 1, 2, 'AUTORIZADO', 'INV-INICIAL', 'Inventario inicial', 40),
(3, 8, 1, 2, 'AUTORIZADO', 'INV-INICIAL', 'Inventario inicial', 6),
(4, 8, 1, 2, 'AUTORIZADO', 'INV-INICIAL', 'Inventario inicial', 12);

INSERT INTO existencia (id_producto, id_bodega, cantidad) VALUES
(1, 1, 20), (1, 2, 5),
(2, 1, 40),
(3, 1, 6),
(4, 1, 12);

-- ---------------------------------------------------------------------
-- Kardex valorizado (costo promedio ponderado).
-- Los datos de prueba se valoran al costo de referencia de cada producto:
-- ese es el costo promedio inicial y el costo de cada movimiento cargado.
-- (Se filtra por la llave primaria para que Workbench lo permita en modo seguro.)
-- ---------------------------------------------------------------------
UPDATE producto SET costo_promedio = costo_referencia
WHERE id_producto > 0 AND costo_promedio = 0;

UPDATE movimiento_inventario
SET costo_unitario = (SELECT p.costo_referencia FROM producto p
                      WHERE p.id_producto = movimiento_inventario.id_producto)
WHERE id_movimiento > 0 AND costo_unitario IS NULL;

-- Bodega de cuarentena: recibe por transferencia la mercancía averiada y la
-- guarda separada mientras el Supervisor decide la baja.
INSERT INTO bodega (codigo, nombre, ubicacion, es_cuarentena) VALUES
('BOD-CUA', 'Cuarentena', 'Zona separada para mercancía averiada', TRUE);

-- ---------------------------------------------------------------------
-- Causa estándar de las pérdidas de los datos de prueba (reporte de pérdidas):
-- los ajustes negativos del conteo son faltantes; las mermas y daños cargados
-- sin causa quedan como "Otro".
-- ---------------------------------------------------------------------
UPDATE movimiento_inventario SET causa_baja = 'FALTANTE'
WHERE id_movimiento > 0 AND causa_baja IS NULL AND id_tipo_movimiento IN
      (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre = 'Ajuste negativo');

UPDATE movimiento_inventario SET causa_baja = 'OTRO'
WHERE id_movimiento > 0 AND causa_baja IS NULL AND id_tipo_movimiento IN
      (SELECT id_tipo_movimiento FROM tipo_movimiento WHERE nombre IN ('Merma', 'Producto dañado o vencido'));

-- Días de entrega de los proveedores (sugerencia de compra)
UPDATE proveedor SET dias_entrega = 3 WHERE id_proveedor > 0 AND nit = '900123456-1';
UPDATE proveedor SET dias_entrega = 7 WHERE id_proveedor > 0 AND nit = '900654321-2';
