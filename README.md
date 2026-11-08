# Aristools — Sistema de Gestión de Inventarios

Proyecto Integrador de cuarto semestre · Tecnología en Sistemas de Información · Institución Universitaria Antonio José Camacho (UNIAJC).

Aplicativo de escritorio en Java para controlar el inventario de la **Ferretería Aristools**: productos, bodegas, proveedores y cada movimiento de mercancía (entradas, salidas, transferencias, conteos físicos y ajustes). Cada movimiento guarda quién lo registró, quién lo autorizó, cuándo y por qué, de modo que el kardex de cualquier producto se puede reconstruir y siempre coincide con el stock. El alcance es la gestión de bodega: **no incluye ventas ni facturación**.

**Equipo:** Deibit Arley Urbano, Jefferson Paredes, Juan Sebastián Muñoz y Jerónimo Montoya.

---

## 1. Funcionalidades (15 requerimientos funcionales)

| RF | Funcionalidad | Rol principal |
|---|---|---|
| RF-001 | Iniciar y cerrar sesión (bloqueo al tercer intento fallido) | Todos |
| RF-002 | Gestionar usuarios y roles | Administrador |
| RF-003 | Gestionar productos y categorías | Administrador |
| RF-004 | Gestionar bodegas | Administrador |
| RF-005 | Gestionar proveedores | Jefe de Compras / Administrador |
| RF-006 | Registrar entradas de mercancía (compra, devolución) | Almacenista |
| RF-007 | Registrar salidas (consumo interno, merma, dañado o vencido, devolución a proveedor) | Almacenista |
| RF-008 | Registrar transferencias entre bodegas | Almacenista |
| RF-009 | Realizar conteo físico y generar ajustes | Almacenista |
| RF-010 | Autorizar o rechazar entradas | Jefe de Compras |
| RF-011 | Autorizar o rechazar salidas críticas y ajustes negativos | Supervisor de Bodega |
| RF-012 | Generar alertas de stock mínimo y máximo | Sistema |
| RF-013 | Consultar existencias | Todos |
| RF-014 | Generar kardex y reportes de movimientos | Administrador, Jefe de Compras, Supervisor |
| RF-015 | Configurar parámetros globales y umbrales | Administrador |

**Flujo principal:** el Almacenista registra el movimiento → el sistema lo valida → si el tipo lo exige, queda **PENDIENTE** → un usuario **distinto** lo autoriza o lo rechaza con motivo → al autorizarse, el stock se actualiza dentro de una transacción → se evalúan las alertas → el movimiento queda en el kardex.

---

## 2. Tecnologías

| Componente | Versión | Uso |
|---|---|---|
| Java (JDK) | 17 o superior | Lenguaje del aplicativo |
| Maven | 3.9 o superior | Compilación, dependencias y pruebas |
| MySQL Server | 8.x o superior (probado en 9.7) | Base de datos relacional |
| MySQL Connector/J | 9.1.0 | Conexión JDBC |
| jBCrypt | 0.4 | Cifrado de contraseñas con sal (RNF-001) |
| FlatLaf | 3.5.1 | Apariencia moderna de las ventanas Swing |
| JUnit 5 | 5.10.2 | Pruebas unitarias e integración |

---

## 3. Arquitectura (MVC + DAO)

```
 Vista (Swing)  ──►  Controlador  ──►  Modelo (reglas de negocio)
                         │
                         ▼
                   DAO (JDBC, SQL)  ──►  MySQL (aristools)
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Vista | `vista` *(en desarrollo, paso 9)* | Ventanas Swing: `JFrame` principal con `BorderLayout` (barra superior al norte, menú lateral al oeste) y un panel central con `CardLayout` que cambia de pantalla según la opción del menú |
| Controlador | `controlador` | Recibe la acción del usuario, verifica permisos, coordina modelo y DAO, maneja transacciones |
| Modelo | `modelo` | Entidades y reglas de negocio: herencia, polimorfismo y validaciones |
| Acceso a datos | `dao` | Consultas SQL con `PreparedStatement`; convierte filas en objetos |
| Seguridad | `seguridad` | BCrypt, permisos por rol y sesión del usuario |
| Conexión | `conexion` | Abre la conexión JDBC leyendo `db.properties` |
| Utilidades | `util` | Excepción de negocio y validaciones de formularios |

### Puntos técnicos destacados

- **Herencia y polimorfismo:** `Movimiento` es una clase abstracta con cuatro subclases (`Entrada`, `Salida`, `Transferencia`, `Ajuste`). Cada una implementa `validar()` y `calcularCambiosStock()` a su manera: la entrada suma, la salida resta y la transferencia resta en el origen y suma en el destino. `MovimientoDAO.mapear()` crea la subclase correcta al leer de la base.
- **Transacciones:** `GestorInventario` aplica cada movimiento con `COMMIT` / `ROLLBACK`: o se guarda todo (movimiento, existencias y alertas) o no se guarda nada.
- **Concurrencia:** las existencias se bloquean con `SELECT ... FOR UPDATE` y las bodegas se bloquean siempre en el mismo orden (por id) para evitar bloqueos cruzados en transferencias simultáneas. Al autorizar, se bloquea la fila del movimiento para que dos usuarios no lo resuelvan a la vez.
- **Segregación de funciones:** quien registra un movimiento no puede autorizarlo. Se valida en el modelo, en el controlador y en la base de datos (`CHECK`).
- **Seguridad:** contraseñas con BCrypt (hash con sal, nunca en texto plano), mensajes de inicio de sesión que no revelan qué dato falló, bloqueo al tercer intento y control de permisos por rol en cada acción (`Sesion.exigir()`).
- **Integridad:** el stock nunca queda negativo (validación en Java y `CHECK` en MySQL). Los registros no se borran: se inactivan (eliminación lógica) para conservar el historial.
- **Errores claros:** `NegocioException` traduce los errores técnicos de MySQL (datos duplicados, restricciones, conexión) a mensajes en español para el usuario.

---

## 4. Estructura del proyecto

```
aristools-inventario/
├── database/
│   ├── aristools.sql            # Estructura completa + datos obligatorios
│   ├── datos_iniciales.sql      # Usuarios de prueba e inventario inicial
│   ├── datos_adicionales.sql    # Un mes de operación (octubre de 2026)
│   ├── actualizar_bd_v2.sql     # Mejoras para una base ya creada (no borra datos)
│   ├── respaldar_bd.bat         # Respaldo (RNF-007)
│   └── restaurar_bd.bat         # Restauración (RNF-007)
├── src/main/java/com/aristools/
│   ├── conexion/    ConexionDB
│   ├── modelo/      25 clases (entidades, Movimiento y sus 4 subclases, enumeraciones y auxiliares)
│   ├── dao/         12 DAO
│   ├── seguridad/   Accion, Permisos, PasswordUtil, Sesion
│   ├── controlador/ 13 controladores (incluye GestorInventario y LoginControlador)
│   └── util/        NegocioException, Validador
├── src/main/resources/
│   └── db.properties.ejemplo    # Plantilla de la conexión (copiar como db.properties)
├── src/test/java/com/aristools/ # 35 pruebas JUnit
├── pom.xml
└── README.md
```

| Paquete | Clases |
|---|---|
| `modelo` | Rol, Usuario, Categoria, Producto, Bodega, Proveedor, Existencia, TipoMovimiento, Alerta, Parametro, **Movimiento** (abstracta), Entrada, Salida, Transferencia, Ajuste, EstadoUsuario, EstadoRegistro, EstadoMovimiento, EstadoAlerta, TipoAlerta, Naturaleza, CambioStock, LineaKardex, LineaConteo, FiltroMovimiento |
| `dao` | UsuarioDAO, RolDAO, CategoriaDAO, ProductoDAO, BodegaDAO, ProveedorDAO, TipoMovimientoDAO, ParametroDAO, ExistenciaDAO, MovimientoDAO, AlertaDAO, ReporteDAO |
| `controlador` | LoginControlador, GestorInventario, MovimientoControlador, AutorizacionControlador, AlertaControlador, ConteoControlador, ReporteControlador, PanelControlador, UsuarioControlador, ProductoControlador, BodegaControlador, ProveedorControlador, ParametroControlador |

---

## 5. Base de datos

Esquema `aristools` (InnoDB, utf8mb4) con **11 tablas**, 11 llaves primarias, **13 llaves foráneas**, restricciones `UNIQUE` y `CHECK`, y 3 índices para el kardex y las bandejas de autorización.

| Grupo | Tablas |
|---|---|
| Seguridad | `rol`, `usuario` |
| Catálogos | `categoria`, `producto`, `bodega`, `proveedor`, `tipo_movimiento` |
| Operación | `existencia` (producto × bodega, llave compuesta), `movimiento_inventario` |
| Control | `alerta`, `parametro` |

Los valores de estado se guardan en mayúscula (`ACTIVO`, `PENDIENTE`, `AUTORIZADO`…) porque coinciden con las enumeraciones de Java. El stock no se guarda en `producto`: es la suma de `existencia` y solo cambia con movimientos autorizados.

**Datos obligatorios:** 4 roles, 9 tipos de movimiento (Compra, Devolución, Consumo interno, Merma, Producto dañado o vencido, Devolución a proveedor, Transferencia, Ajuste positivo y Ajuste negativo) y 3 parámetros (nombre del establecimiento, stock mínimo por defecto 10 y máximo por defecto 100).

**Datos de prueba:** 6 usuarios, 6 categorías, 22 productos (21 activos), 4 bodegas (3 activas), 5 proveedores (4 activos), 54 movimientos (6 pendientes de autorización) y 6 alertas activas.

---

## 6. Instalación y ejecución

### Requisitos
- JDK 17 o superior, Maven 3.9 o superior y MySQL Server 8.x o superior.
- MySQL Workbench (recomendado) para ejecutar los scripts.

### Pasos
1. **Clonar el repositorio**
   ```
   git clone https://github.com/Jeffer-35/aristools-inventario.git
   ```
2. **Crear la base de datos** en MySQL Workbench, ejecutando en este orden:
   1. `database/aristools.sql` ⚠️ borra y vuelve a crear la base `aristools`.
   2. `database/datos_iniciales.sql`
   3. `database/datos_adicionales.sql`

   Si ya tenía la base creada con datos, **no** ejecute `aristools.sql`: ejecute solo `database/actualizar_bd_v2.sql` una vez.
3. **Configurar la conexión:** copiar `src/main/resources/db.properties.ejemplo` con el nombre `db.properties` y escribir la contraseña de MySQL en `clave=`. Este archivo está en `.gitignore` y nunca se sube.
4. **Compilar y probar**
   ```
   mvn test
   ```
   Resultado esperado: `Tests run: 35, Failures: 0` y `BUILD SUCCESS`.
5. **Abrir en un IDE:** el proyecto es Maven, así que NetBeans (*File → Open Project*), VS Code o IntelliJ lo abren directamente desde la carpeta que contiene `pom.xml`.

### Usuarios de prueba
Contraseña de todos: `Aristools2026*`

| Usuario | Rol | Estado |
|---|---|---|
| `admin` | Administrador del Sistema | Activo |
| `almacen` | Almacenista | Activo |
| `compras` | Jefe de Compras | Activo |
| `supervisor` | Supervisor de Bodega | Activo |
| `almacen.vega` | Almacenista | Activo |
| `almacen.rey` | Almacenista | **Bloqueado** (para probar la reactivación) |

---

## 7. Pruebas automatizadas (35)

| Clase | Pruebas | Qué verifica |
|---|---|---|
| `ConexionDBTest` | 2 | Conexión a MySQL; existen los 4 roles y los 9 tipos de movimiento |
| `ModeloTest` | 13 | Herencia y polimorfismo, validaciones (cantidad, motivo, proveedor y documento en compras), segregación de funciones, bloqueo al tercer intento, umbrales y conteo físico |
| `DaoLecturaTest` | 5 | Lectura de la base real: el kardex coincide con el stock, se crean las subclases correctas y los hashes BCrypt son compatibles |
| `SeguridadTest` | 4 | BCrypt con sal, permisos por rol y `Sesion.exigir()` |
| `LoginControladorTest` | 4 | Inicio de sesión correcto, mensajes que no revelan qué dato falló y bloqueo de la cuenta |
| `ControladoresTest` | 7 | Tarjetas del panel, permisos por rol, `ROLLBACK` real, conteo desde CSV, kardex recortado por rol y validaciones de catálogos y parámetros |

Las pruebas que usan la base no alteran el inventario ni bloquean cuentas activas: las operaciones con movimientos se revierten con `ROLLBACK`.

---

## 8. Respaldo y recuperación (RNF-007)

- `database/respaldar_bd.bat` genera `database/respaldos/aristools_AAAAMMDD_HHMM.sql`.
- `database/restaurar_bd.bat` restaura uno de esos archivos (pide confirmación, porque reemplaza la base).

Ambos detectan automáticamente la carpeta de MySQL Server 8.x o 9.x en `C:\Program Files\MySQL`.

---

## 9. Estado del desarrollo

| Paso | Contenido | Estado |
|---|---|---|
| 1–3 | Base de datos, scripts y datos de prueba | ✅ |
| 4 | Proyecto Maven y conexión | ✅ |
| 5 | Modelo con herencia y polimorfismo | ✅ |
| 6 | DAO con `PreparedStatement` y transacciones | ✅ |
| 7 | Seguridad e inicio de sesión | ✅ |
| 8 | Controladores, reglas de negocio y mejoras de la base | ✅ |
| 9 | Vista: ventanas Swing con `BorderLayout` y `CardLayout`, y `Main` | ⏳ En desarrollo |
| 10 | Pruebas funcionales, respaldo real y prueba de rendimiento | ⏳ |
| 11 | `.jar` ejecutable y entrega final | ⏳ |

La documentación completa (requerimientos, casos de uso, diagramas UML, modelo entidad-relación y mockups) está en el documento del Proyecto Integrador.
