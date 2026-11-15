# Aristools — Sistema de Gestión de Inventarios

Proyecto Integrador de cuarto semestre · Tecnología en Sistemas de Información · Institución Universitaria Antonio José Camacho (UNIAJC).

Aplicativo de escritorio en Java para controlar el inventario de la **Ferretería Aristools**: productos, bodegas, proveedores y cada movimiento de mercancía (entradas, salidas, transferencias, conteos físicos y ajustes). Cada movimiento guarda quién lo registró, quién lo autorizó, cuándo y por qué, de modo que el kardex de cualquier producto se puede reconstruir y siempre coincide con el stock. El alcance es la gestión de bodega: **no incluye ventas ni facturación**.

**Equipo:** Deibit Arley Urbano y Jefferson Paredes Espinel.

---

## 1. Inicio rápido

| Quiero… | Qué hago | ¿Necesita MySQL? |
|---|---|---|
| Comprobar que las pruebas pasan | `mvn test` | **No** (usan una base H2 en memoria) |
| Ejecutar el programa desde el proyecto | Crear la base (sección 6), configurar `db.properties` y `mvn compile exec:java` | Sí |
| Generar el programa instalable | `mvn clean package` → `target/Aristools.jar` (sección 7) | Sí, para usarlo |

```
git clone https://github.com/Jeffer-35/aristools-inventario.git
cd aristools-inventario
mvn test
```

Resultado esperado: `Tests run: 53, Failures: 0, Errors: 0` y `BUILD SUCCESS`, en cualquier computador con JDK 17 y Maven, sin instalar MySQL y sin contraseñas.

---

## 2. Funcionalidades

### Requerimientos funcionales (15)

| RF | Funcionalidad | Rol principal |
|---|---|---|
| RF-001 | Iniciar y cerrar sesión (bloqueo al tercer intento fallido) | Todos |
| RF-002 | Gestionar usuarios y roles | Administrador |
| RF-003 | Gestionar productos y categorías | Administrador |
| RF-004 | Gestionar bodegas | Administrador |
| RF-005 | Gestionar proveedores | Jefe de Compras / Administrador |
| RF-006 | Registrar entradas de mercancía (compra, devolución); la compra exige el costo unitario de la factura | Almacenista |
| RF-007 | Registrar salidas (consumo interno, merma, dañado o vencido, devolución a proveedor) | Almacenista |
| RF-008 | Registrar transferencias entre bodegas | Almacenista |
| RF-009 | Realizar conteo físico y generar ajustes | Almacenista |
| RF-010 | Autorizar o rechazar entradas | Jefe de Compras |
| RF-011 | Autorizar o rechazar salidas críticas y ajustes negativos | Supervisor de Bodega |
| RF-012 | Generar alertas de stock mínimo y máximo | Sistema |
| RF-013 | Consultar existencias | Todos |
| RF-014 | Generar kardex valorizado y reportes de movimientos (exportación a Excel en CSV e impresión en PDF) | Administrador, Jefe de Compras, Supervisor |
| RF-015 | Configurar parámetros globales y umbrales | Administrador |

**Flujo principal:** el Almacenista registra el movimiento → el sistema lo valida → si el tipo lo exige, queda **PENDIENTE** → un usuario **distinto** lo autoriza o lo rechaza con motivo → al autorizarse, el stock se actualiza dentro de una transacción → se evalúan las alertas → el movimiento queda en el kardex.

### Kardex valorizado (costo promedio ponderado)

Además de las unidades, el kardex lleva el valor en pesos del inventario:

- En cada **compra** el Almacenista escribe el costo unitario de la factura. Cuando el Jefe de Compras la autoriza, el sistema recalcula el costo promedio del producto:

  ```
  nuevo promedio = (stock anterior × promedio anterior + cantidad comprada × costo de la factura)
                   ÷ (stock anterior + cantidad comprada)
  ```

- Las **salidas, transferencias, devoluciones y ajustes** se valoran al costo promedio de ese momento y no lo cambian.
- Cada movimiento guarda el costo unitario con el que se valoró, por eso el kardex se puede reconstruir en cualquier fecha.

**Ejemplo:** hay 12 rollos de cable a $ 185.000 y se compran 8 a $ 200.000. El promedio queda en (12 × 185.000 + 8 × 200.000) ÷ 20 = **$ 191.000**. Luego salen 5 rollos valorados a $ 191.000 y el saldo queda en 15 rollos por $ 2.865.000. Este caso lo comprueba la prueba `KardexValorizadoTest`.

### Reportes en PDF

El botón **Imprimir PDF** está en el kardex, el reporte de movimientos, las existencias y la bitácora. El documento lleva el nombre del establecimiento, los filtros usados, la tabla, el resumen y los responsables:

- **Elaboró:** el usuario que genera el reporte, con su rol.
- **Revisó:** el responsable de revisión que el Administrador define en *Configuración* (el cambio queda en la bitácora).

### Seguridad de acceso (RNF-001)

| Medida | Cómo funciona |
|---|---|
| Contraseñas cifradas | BCrypt con sal; nunca se guardan en texto plano |
| Contraseña segura | Mínimo 8 caracteres, con mayúscula, minúscula, número y símbolo; el sistema indica qué le falta |
| Bloqueo de cuenta | Al tercer intento fallido la cuenta queda bloqueada hasta que el Administrador la reactive |
| Contraseña temporal | Cuando el Administrador crea un usuario o le restablece la clave, el usuario debe cambiarla en su primer ingreso |
| Vencimiento configurable | Parámetro `dias_vigencia_clave`: 0 = sin vencimiento (recomendación NIST SP 800-63B); el Administrador puede activarlo |
| Cierre por inactividad | Parámetro `minutos_inactividad` (15 por defecto; 0 = desactivado) |
| Primer administrador | Si la base no tiene un Administrador activo, el programa pide crearlo; no hay contraseñas de administrador escritas en el código |
| Permisos por rol | Cada acción se verifica con `Sesion.exigir()` y el menú solo muestra lo que el rol puede hacer |

### Bitácora de auditoría

Registra las acciones sensibles: ingresos e intentos fallidos, cambios de contraseña, y la creación o modificación de usuarios, productos (por ejemplo, el precio **antes → después**), categorías, bodegas, proveedores y parámetros.

- **Es inalterable:** dos disparadores (*triggers*) de MySQL impiden modificarla o borrarla, incluso desde MySQL Workbench.
- **No frena el trabajo:** si el registro en la bitácora falla, la operación del usuario se guarda igual y el error se informa en la consola.
- **Solo la consulta el Administrador** (permiso `VER_BITACORA`).

---

## 3. Tecnologías

| Componente | Versión | Uso |
|---|---|---|
| Java (JDK) | 17 o superior | Lenguaje del aplicativo |
| Maven | 3.9 o superior | Compilación, dependencias, pruebas y empaquetado |
| MySQL Server | 8.x o superior (probado en 9.7) | Base de datos del programa |
| MySQL Connector/J | 9.1.0 | Conexión JDBC |
| jBCrypt | 0.4 | Cifrado de contraseñas con sal |
| FlatLaf | 3.5.1 | Apariencia moderna de las ventanas Swing |
| JUnit 5 | 5.10.2 | Pruebas automatizadas |
| OpenPDF | 1.3.43 | Generación de los reportes en PDF |
| H2 | 2.2.224 | Base de datos en memoria, **solo para las pruebas** (no va en el `.jar`) |

---

## 4. Arquitectura (MVC + DAO)

```
 Vista (Swing)  ──►  Controlador  ──►  Modelo (reglas de negocio)
                         │
                         ▼
                   DAO (JDBC, SQL)  ──►  MySQL (aristools)
```

| Capa | Paquete | Responsabilidad |
|---|---|---|
| Vista | `vista` | Ventanas Swing: `JFrame` principal con `BorderLayout` (barra superior al norte, menú lateral al oeste) y un panel central con `CardLayout` que cambia de pantalla según la opción del menú. No contiene SQL |
| Controlador | `controlador` | Recibe la acción del usuario, verifica permisos, coordina modelo y DAO, maneja transacciones y registra en la bitácora |
| Modelo | `modelo` | Entidades y reglas de negocio: herencia, polimorfismo y validaciones |
| Acceso a datos | `dao` | Consultas SQL con `PreparedStatement`; convierte filas en objetos |
| Seguridad | `seguridad` | BCrypt, permisos por rol y sesión del usuario |
| Conexión | `conexion` | Abre la conexión JDBC leyendo `db.properties` |
| Utilidades | `util` | Excepción de negocio, validaciones de formularios, comparación de cambios para la bitácora, formato de pesos y generador de reportes PDF |

### Puntos técnicos destacados

- **Herencia y polimorfismo:** `Movimiento` es una clase abstracta con cuatro subclases (`Entrada`, `Salida`, `Transferencia`, `Ajuste`). Cada una implementa `validar()` y `calcularCambiosStock()` a su manera: la entrada suma, la salida resta y la transferencia resta en el origen y suma en el destino. Para el kardex valorizado, `Entrada` redefine `costoParaValorar()` y `nuevoCostoPromedio()`: solo la compra cambia el costo promedio. `MovimientoDAO.mapear()` crea la subclase correcta al leer de la base. En la vista, `PanelCatalogo` y `PanelMovimiento` son clases abstractas que comparten el comportamiento de las pantallas, y la interfaz `Recargable` permite refrescar cualquier panel.
- **Transacciones:** `GestorInventario` aplica cada movimiento con `COMMIT` / `ROLLBACK`: o se guarda todo (movimiento, costo, existencias y alertas) o no se guarda nada.
- **Concurrencia:** la fila del producto (para el costo promedio) y las existencias se bloquean con `SELECT ... FOR UPDATE` y las bodegas se bloquean siempre en el mismo orden (por id) para evitar bloqueos cruzados en transferencias simultáneas. Al autorizar, se bloquea la fila del movimiento para que dos usuarios no lo resuelvan a la vez.
- **Segregación de funciones:** quien registra un movimiento no puede autorizarlo. Se valida en el modelo, en el controlador y en la base de datos (`CHECK`).
- **Reglas en dos niveles:** las validaciones están en Java y también en la base (`CHECK`, `UNIQUE`, llaves foráneas y disparadores). El stock nunca queda negativo. Los registros no se borran: se inactivan (eliminación lógica) para conservar el historial.
- **Errores claros:** `NegocioException` traduce los errores técnicos de MySQL (datos duplicados, restricciones, conexión) a mensajes en español para el usuario.

---

## 5. Estructura del proyecto

```
aristools-inventario/
├── database/
│   ├── aristools.sql            # Estructura completa + datos obligatorios
│   ├── datos_iniciales.sql      # Usuarios de prueba e inventario inicial
│   ├── datos_adicionales.sql    # Un mes de operación (octubre de 2026)
│   ├── actualizar_bd_v2.sql     # Actualizaciones para una base ya creada
│   ├── actualizar_bd_v3.sql     #   v3: seguridad de contraseñas
│   ├── actualizar_bd_v4.sql     #   v4: bitácora de auditoría
│   ├── actualizar_bd_v5.sql     #   v5: kardex valorizado y responsable de revisión
│   ├── respaldar_bd.bat         # Respaldo (RNF-007)
│   └── restaurar_bd.bat         # Restauración (RNF-007)
├── src/main/java/com/aristools/
│   ├── Main.java                # Punto de entrada del programa
│   ├── conexion/    ConexionDB
│   ├── modelo/      26 clases (entidades, Movimiento y sus 4 subclases, enumeraciones y auxiliares)
│   ├── dao/         13 DAO
│   ├── seguridad/   Accion, Permisos, PasswordUtil, Sesion
│   ├── controlador/ 15 clases (controladores, GestorInventario y Auditoria)
│   ├── util/        NegocioException, Validador, Cambios, Formato, ReportePdf
│   └── vista/       32 clases (ventanas, paneles y diálogos Swing)
├── src/main/resources/
│   └── db.properties.ejemplo    # Plantilla de la conexión a MySQL (copiar como db.properties)
├── src/test/java/com/aristools/ # 53 pruebas JUnit
├── src/test/resources/
│   └── db-pruebas.properties    # Conexión de las pruebas: H2 en memoria
├── ejecutar_aristools.bat       # Lanzador del .jar (sin ventana de consola)
├── pom.xml
└── README.md
```

| Paquete | Clases |
|---|---|
| `modelo` | Rol, Usuario, Categoria, Producto, Bodega, Proveedor, Existencia, TipoMovimiento, Alerta, Parametro, RegistroBitacora, **Movimiento** (abstracta), Entrada, Salida, Transferencia, Ajuste, EstadoUsuario, EstadoRegistro, EstadoMovimiento, EstadoAlerta, TipoAlerta, Naturaleza, CambioStock, LineaKardex, LineaConteo, FiltroMovimiento |
| `dao` | UsuarioDAO, RolDAO, CategoriaDAO, ProductoDAO, BodegaDAO, ProveedorDAO, TipoMovimientoDAO, ParametroDAO, ExistenciaDAO, MovimientoDAO, AlertaDAO, ReporteDAO, BitacoraDAO |
| `controlador` | LoginControlador, GestorInventario, MovimientoControlador, AutorizacionControlador, AlertaControlador, ConteoControlador, ReporteControlador, PanelControlador, UsuarioControlador, ProductoControlador, BodegaControlador, ProveedorControlador, ParametroControlador, BitacoraControlador, Auditoria |
| `vista` | **Ventanas y diálogos:** LoginVista, PrincipalVista, CambiarClaveDialogo, PrimerAdminDialogo · **Pantallas:** InicioPanel, CatalogosPanel, ProductosPanel, CategoriasPanel, BodegasPanel, ProveedoresPanel, UsuariosPanel, MovimientosPanel, EntradaPanel, SalidaPanel, TransferenciaPanel, ConteoPanel, AutorizacionesPanel, ExistenciasPanel, ReportesPanel, ParametrosPanel, BitacoraPanel · **Base y apoyo:** PanelCatalogo y PanelMovimiento (abstractas), Recargable (interfaz), PanelDesplazable, PanelEnConstruccion, BarraFiltros, ExportadorCsv, ExportadorPdf, MonitorInactividad, MostrarClave, Tema |

---

## 6. Base de datos

Esquema `aristools` (InnoDB, utf8mb4) con **12 tablas**, 12 llaves primarias, **14 llaves foráneas**, restricciones `UNIQUE` y `CHECK`, 5 índices y 2 disparadores.

| Grupo | Tablas |
|---|---|
| Seguridad | `rol`, `usuario` |
| Catálogos | `categoria`, `producto`, `bodega`, `proveedor`, `tipo_movimiento` |
| Operación | `existencia` (producto × bodega, llave compuesta), `movimiento_inventario` |
| Control | `alerta`, `parametro` |
| Auditoría | `bitacora` (solo inserción, protegida por 2 disparadores) |

Los valores de estado se guardan en mayúscula (`ACTIVO`, `PENDIENTE`, `AUTORIZADO`…) porque coinciden con las enumeraciones de Java. El stock no se guarda en `producto`: es la suma de `existencia` y solo cambia con movimientos autorizados. Para el kardex valorizado, `producto` guarda el `costo_promedio` y cada movimiento su `costo_unitario`; los datos de prueba se valoran al costo de referencia.

**Datos obligatorios:** 4 roles, 9 tipos de movimiento (Compra, Devolución, Consumo interno, Merma, Producto dañado o vencido, Devolución a proveedor, Transferencia, Ajuste positivo y Ajuste negativo) y 6 parámetros (nombre del establecimiento, stock mínimo por defecto 10, stock máximo por defecto 100, días de vigencia de la contraseña 0, minutos de inactividad 15 y responsable de revisión de los reportes).

**Datos de prueba:** 6 usuarios, 6 categorías, 22 productos (21 activos), 4 bodegas (3 activas), 5 proveedores (4 activos), 54 movimientos (6 pendientes de autorización) y 6 alertas activas.

### Crear la base (instalación nueva)

En MySQL Workbench, ejecutar en este orden:

1. `database/aristools.sql` ⚠️ borra y vuelve a crear la base `aristools`.
2. `database/datos_iniciales.sql`
3. `database/datos_adicionales.sql`

### Actualizar una base que ya tiene datos

**No** ejecute `aristools.sql` (borraría los datos). Ejecute, una sola vez y en orden, solo los scripts que le falten:

| Script | Qué agrega |
|---|---|
| `actualizar_bd_v2.sql` | Mejoras de integridad de la base |
| `actualizar_bd_v3.sql` | Seguridad de contraseñas: 2 columnas en `usuario` y 2 parámetros |
| `actualizar_bd_v4.sql` | Bitácora de auditoría: tabla `bitacora` y 2 disparadores |
| `actualizar_bd_v5.sql` | Kardex valorizado: `costo_promedio` en `producto`, `costo_unitario` en `movimiento_inventario` y parámetro `responsable_revision` |

Cada integrante ejecuta los scripts en su propio MySQL: GitHub comparte el código, no la base de datos.

---

## 7. Instalación y ejecución

### Requisitos

- JDK 17 o superior y Maven 3.9 o superior.
- MySQL Server 8.x o superior y MySQL Workbench (para el programa; las pruebas no los necesitan).

### Ejecutar desde el proyecto

1. **Clonar el repositorio**
   ```
   git clone https://github.com/Jeffer-35/aristools-inventario.git
   ```
2. **Crear la base de datos** (sección 6).
3. **Configurar la conexión:** copiar `src/main/resources/db.properties.ejemplo` con el nombre `db.properties` (en la misma carpeta) y escribir la contraseña de MySQL en `clave=`. Este archivo está en `.gitignore` y nunca se sube a GitHub.
4. **Ejecutar**
   ```
   mvn compile exec:java
   ```

### Generar el programa instalable (.jar)

1. En la carpeta del proyecto:
   ```
   mvn clean package
   ```
   Se crea `target/Aristools.jar`, con las librerías incluidas (MySQL, BCrypt, FlatLaf y OpenPDF).
2. Copiar en una misma carpeta (por ejemplo, `Aristools_programa`):
   - `target/Aristools.jar`
   - `ejecutar_aristools.bat`
   - `db.properties` con la contraseña de MySQL de ese computador
3. Abrir con doble clic en `ejecutar_aristools.bat` (o en `Aristools.jar`).

El archivo `db.properties` va **al lado** del `.jar`, no dentro: así la contraseña de MySQL nunca queda empaquetada en el ejecutable y cada computador usa la suya. El programa lo busca en este orden: junto al `.jar`, en la carpeta desde donde se ejecuta y, por último, en `src/main/resources`.

### Abrir en un IDE

El proyecto es Maven, así que NetBeans (*File → Open Project*), VS Code o IntelliJ lo abren directamente desde la carpeta que contiene `pom.xml`. En NetBeans, la base se conecta desde *Services → Databases* con el conector de MySQL.

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

## 8. Pruebas automatizadas (53)

```
mvn test
```

### No dependen del MySQL local

Las pruebas usan **H2**, una base de datos en memoria que Maven descarga sola (la primera vez se necesita internet). Al empezar, la clase `BaseDePruebas` crea la base ejecutando los **mismos scripts del proyecto** (`database/aristools.sql` y `database/datos_iniciales.sql`); al terminar, la base desaparece.

| | El programa | Las pruebas (`mvn test`) |
|---|---|---|
| Base de datos | MySQL | H2 en memoria (modo MySQL) |
| Archivo de conexión | `db.properties` (no se sube: tiene la contraseña) | `src/test/resources/db-pruebas.properties` (sí se sube: no tiene contraseña) |
| Requiere instalar algo | MySQL Server | Nada |

`ConexionDB` usa `db-pruebas.properties` solo cuando ese archivo existe, y eso ocurre únicamente al ejecutar las pruebas. Ni ese archivo ni H2 van dentro del `.jar`: el programa siempre usa MySQL.

**Límite conocido:** los dos disparadores de la bitácora están escritos en el lenguaje de MySQL y H2 no los ejecuta. Se comprueban en MySQL Workbench intentando un `UPDATE` o un `DELETE` sobre la tabla `bitacora`: MySQL responde con el error “La bitácora no se puede modificar” o “La bitácora no se puede borrar”.

### Qué verifica cada clase

| Clase | Pruebas | Qué verifica |
|---|---|---|
| `BaseDePruebasTest` | 3 | Las pruebas usan H2 y no MySQL; el script real crea las 12 tablas; el lector separa bien las sentencias de un script |
| `ConexionDBTest` | 2 | `ConexionDB` entrega una conexión válida; existen los 4 roles y los 9 tipos de movimiento |
| `ModeloTest` | 16 | Herencia y polimorfismo, validaciones (cantidad, motivo, proveedor, documento y costo en compras), costo promedio ponderado, segregación de funciones, bloqueo al tercer intento, umbrales y conteo físico |
| `KardexValorizadoTest` | 1 | Flujo completo: compra registrada, autorizada por otro usuario, promedio recalculado, salida valorada y kardex en pesos |
| `FormatoTest` | 3 | Forma de mostrar y de leer valores en pesos |
| `ReportePdfTest` | 2 | El PDF trae encabezado, datos, resumen y responsables; un reporte largo ocupa varias páginas |
| `DaoLecturaTest` | 5 | El kardex coincide con el stock, se crean las subclases correctas y los hashes BCrypt son compatibles |
| `SeguridadTest` | 4 | BCrypt con sal, permisos por rol y `Sesion.exigir()` |
| `ClaveSeguraTest` | 4 | Reglas de la contraseña segura y vencimiento según los días de vigencia |
| `LoginControladorTest` | 4 | Inicio de sesión correcto, mensajes que no revelan qué dato falló y bloqueo de la cuenta |
| `ControladoresTest` | 7 | Tarjetas del panel, permisos por rol, `ROLLBACK` real, conteo desde CSV, kardex recortado por rol y validaciones de catálogos y parámetros |
| `CambiosTest` | 2 | Texto “antes → después” que se guarda en la bitácora |

Las operaciones con movimientos se revierten con `ROLLBACK`, así que los datos de prueba no cambian entre una prueba y otra.

---

## 9. Respaldo y recuperación (RNF-007)

- `database/respaldar_bd.bat` genera `database/respaldos/aristools_AAAAMMDD_HHMM.sql`.
- `database/restaurar_bd.bat` restaura uno de esos archivos (pide confirmación, porque reemplaza la base).

Ambos detectan automáticamente la carpeta de MySQL Server 8.x o 9.x en `C:\Program Files\MySQL`. La carpeta `database/respaldos/` está en `.gitignore` porque puede contener datos reales.

---

## 10. Estado del desarrollo

| Paso | Contenido | Estado |
|---|---|---|
| 1–3 | Base de datos, scripts y datos de prueba | ✅ |
| 4 | Proyecto Maven y conexión | ✅ |
| 5 | Modelo con herencia y polimorfismo | ✅ |
| 6 | DAO con `PreparedStatement` y transacciones | ✅ |
| 7 | Seguridad e inicio de sesión | ✅ |
| 8 | Controladores, reglas de negocio y mejoras de la base | ✅ |
| 9 | Vista: ventanas Swing con `BorderLayout` y `CardLayout`, menú según el rol | ✅ |
| 10 | `.jar` ejecutable y proyecto abierto en NetBeans (tag `segunda-entrega`) | ✅ |
| 11 | Seguridad de contraseñas y bitácora de auditoría | ✅ |
| 12 | Pruebas sin depender de MySQL (H2 en memoria) | ✅ |
| 13 | Kardex valorizado (costo promedio ponderado) y reportes en PDF | ✅ |

### Mejoras planeadas para la entrega final

Averías y reporte de pérdidas con sus documentos de soporte en PDF, sugerencia de compra, clasificación ABC con conteo cíclico, control de vencimientos (FEFO), control mensual, tablero de indicadores (KPI) y respaldo automático.

**Trabajo futuro (fuera del alcance):** base de datos en red o en la nube, control de stock por lote y lector de código de barras.

La documentación completa (requerimientos, casos de uso, diagramas UML, modelo entidad-relación y mockups) está en el documento del Proyecto Integrador.
