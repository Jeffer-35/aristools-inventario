-- =====================================================================
-- Aristools - Actualización v3: seguridad de contraseñas
-- Ejecutar UNA SOLA VEZ en MySQL Workbench sobre la base existente.
-- No borra datos. (Una instalación nueva con aristools.sql ya lo incluye.)
-- =====================================================================
USE aristools;

-- 1) Columnas nuevas en usuario
ALTER TABLE usuario
    ADD COLUMN fecha_cambio_clave DATETIME NULL AFTER ultimo_acceso,
    ADD COLUMN debe_cambiar_clave BOOLEAN NOT NULL DEFAULT FALSE AFTER fecha_cambio_clave;

-- Los usuarios que ya existen cuentan su vigencia desde hoy
SET SQL_SAFE_UPDATES = 0;
UPDATE usuario SET fecha_cambio_clave = NOW() WHERE fecha_cambio_clave IS NULL;
SET SQL_SAFE_UPDATES = 1;

-- 2) Parámetros de seguridad (RF-015)
INSERT INTO parametro (clave, valor, descripcion) VALUES
('dias_vigencia_clave', '0',  'Días de vigencia de la contraseña (0 = sin vencimiento, recomendación NIST SP 800-63B)'),
('minutos_inactividad', '15', 'Minutos sin actividad para cerrar la sesión (0 = desactivado)')
ON DUPLICATE KEY UPDATE descripcion = VALUES(descripcion);

-- 3) Verificación: deben salir las dos columnas y los dos parámetros
SHOW COLUMNS FROM usuario LIKE '%clave%';
SELECT clave, valor FROM parametro WHERE clave IN ('dias_vigencia_clave', 'minutos_inactividad');
