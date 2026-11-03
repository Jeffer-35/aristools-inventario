# Aristools — Sistema de Gestión de Inventarios

Proyecto Integrador de cuarto semestre — Tecnología en Sistemas de Información,
Institución Universitaria Antonio José Camacho (UNIAJC).

Aplicativo de escritorio para controlar el inventario de la Ferretería Aristools:
productos, bodegas, proveedores, entradas, salidas, transferencias, conteo físico,
autorizaciones, alertas de stock y kardex. Cada movimiento queda trazado con quién
lo registró y quién lo autorizó. El proyecto **no** incluye ventas ni facturación.

**Equipo:** Deibit Arley Urbano, Jefferson Paredes, Juan Sebastián Muñoz y Jerónimo Montoya.

## Tecnologías

| Componente | Versión |
|---|---|
| Java (JDK) | 17 o superior |
| MySQL | 8.x o superior |
| Maven | 3.9 o superior |
| MySQL Connector/J | 9.1.0 |
| jBCrypt (contraseñas) | 0.4 |
| FlatLaf (apariencia Swing) | 3.5.1 |
| JUnit (pruebas) | 5.10.2 |

Arquitectura **MVC** con paquetes `modelo`, `dao`, `vista`, `controlador`,
`seguridad`, `conexion` y `util` (ver el diagrama de clases del documento, Figuras 24 a 27).

## Instalación

1. **Crear la base de datos** en MySQL Workbench, en este orden:
   1. `database/aristools.sql` (estructura y datos obligatorios)
   2. `database/datos_iniciales.sql` (usuarios y datos de prueba)
   3. `database/datos_adicionales.sql` (opcional: un mes de movimientos de ejemplo)

   ⚠️ `aristools.sql` borra la base `aristools` si ya existe.

2. **Configurar la conexión:** copiar `src/main/resources/db.properties.ejemplo`
   con el nombre `db.properties` y escribir la contraseña de MySQL en `clave=`.
   Este archivo no se sube al repositorio porque contiene la contraseña.

3. **Compilar y probar** desde la carpeta del proyecto:
   ```
   mvn test
   ```
   Debe terminar con `BUILD SUCCESS`.

## Usuarios de prueba

Contraseña de todos: `Aristools2026*` (guardada como hash BCrypt, nunca en texto plano).

| Usuario | Rol |
|---|---|
| `admin` | Administrador del Sistema |
| `almacen` | Almacenista |
| `compras` | Jefe de Compras |
| `supervisor` | Supervisor de Bodega |

Con `datos_adicionales.sql` también existen `almacen.vega` (Almacenista) y
`almacen.rey` (Almacenista **bloqueado**, para probar la reactivación).

## Respaldo y recuperación (RNF-007)

- `database/respaldar_bd.bat`: genera una copia en `database/respaldos/` con fecha y hora.
- `database/restaurar_bd.bat`: restaura una de esas copias (pide confirmación).

Antes de usarlos, revisar que la variable `MYSQL_BIN` apunte a la carpeta `bin` de MySQL.

## Estado del desarrollo

- [x] Base de datos (11 tablas) y datos de prueba
- [x] Modelo con herencia y polimorfismo (`Movimiento` y sus 4 subclases)
- [x] Capa de acceso a datos (DAO) con `PreparedStatement` y transacciones
- [x] Seguridad: BCrypt, roles, permisos e inicio de sesión
- [ ] Controladores de movimientos, autorizaciones, alertas, conteo y reportes
- [ ] Interfaz gráfica (ventanas Swing)
- [ ] Pruebas funcionales y documentación final
