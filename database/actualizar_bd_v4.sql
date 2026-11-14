-- =====================================================================
-- Aristools - Actualización v4: bitácora de auditoría
-- Ejecutar UNA SOLA VEZ en MySQL Workbench (con el rayo que ejecuta todo).
-- No borra datos. (Una instalación nueva con aristools.sql ya la incluye.)
-- =====================================================================
USE aristools;

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

-- Verificación: debe mostrar la tabla vacía y los dos disparadores
SELECT COUNT(*) AS registros_en_bitacora FROM bitacora;
SHOW TRIGGERS WHERE `Table` = 'bitacora';
