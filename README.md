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

Resultado esperado: `Tests run: 84, Failures: 0, Errors: 0` y `BUILD SUCCESS`, en cualquier computador con JDK 17 y Maven, sin instalar MySQL y sin contraseñas.

---

## 2. Funcionalidades

### Requerimientos funcionales (15)

| RF | Funcionalidad | Rol principal |
|---|---|---|
| RF-001 | Iniciar y cerrar sesión (bloqueo al tercer intento fallido) | Todos |
| RF-002 | Gestionar usuarios y roles | Administrador |
| RF-003 | Gestionar productos y categorías | Administrador |
| RF-004 | Gestionar bodegas | Administrador |
| RF-005 | Gestionar proveedores (con sus días de entrega) | Jefe de Compras / Administrador |
| RF-006 | Registrar entradas de mercancía (compra, devolución); la compra exige el número de la factura y su costo unitario, que el sistema propone con el de la última compra | Almacenista |
| RF-007 | Registrar salidas (consumo interno, merma, dañado o vencido, devolución a proveedor); las pérdidas exigen causa estándar y motivo | Almacenista |
| RF-008 | Registrar transferencias entre bodegas | Almacenista |
| RF-009 | Realizar conteo físico y generar ajustes | Almacenista |
| RF-010 | Autorizar o rechazar entradas | Jefe de Compras |
| RF-011 | Autorizar o rechazar salidas críticas y ajustes negativos | Supervisor de Bodega |
| RF-012 | Generar alertas de stock mínimo y máximo | Sistema |
| RF-013 | Consultar existencias | Todos |
| RF-014 | Generar kardex valorizado, reportes de movimientos y de pérdidas, y documentos de soporte (exportación a Excel en CSV e impresión en PDF) | Administrador, Jefe de Compras, Supervisor |
| RF-015 | Configurar parámetros globales y umbrales | Administrador |

**Flujo principal:** el Almacenista registra el movimiento → el sistema lo valida → si el tipo lo exige, queda **PENDIENTE** → un usuario **distinto** lo autoriza o lo rechaza con motivo → al autorizarse, el stock se actualiza dentro de una transacción → se evalúan las alertas → el movimiento queda en el kardex.

### Kardex valorizado (costo promedio ponderado)

Además de las unidades, el kardex lleva el valor en pesos del inventario:

- En cada **compra** el Almacenista confirma el costo unitario de la factura. Al elegir el producto, el campo se llena con el costo de la última compra (o el promedio) y se muestran ambos como referencia; si el costo escrito es menos de la mitad o más del doble del promedio, el programa pide confirmación (atrapa errores como escribir 5.5 en vez de 5.500). Cuando el Jefe de Compras la autoriza, el sistema recalcula el costo promedio del producto:

  ```
  nuevo promedio = (stock anterior × promedio anterior + cantidad comprada × costo de la factura)
                   ÷ (stock anterior + cantidad comprada)
  ```

- Las **salidas, transferencias, devoluciones y ajustes** se valoran al costo promedio de ese momento y no lo cambian.
- Cada movimiento guarda el costo unitario con el que se valoró, por eso el kardex se puede reconstruir en cualquier fecha.

**Ejemplo:** hay 12 rollos de cable a $ 185.000 y se compran 8 a $ 200.000. El promedio queda en (12 × 185.000 + 8 × 200.000) ÷ 20 = **$ 191.000**. Luego salen 5 rollos valorados a $ 191.000 y el saldo queda en 15 rollos por $ 2.865.000. Este caso lo comprueba la prueba `KardexValorizadoTest`.

### Averías, pérdidas y bodega de cuarentena

| Paso | Quién | Qué pasa |
|---|---|---|
| 1 | Almacenista | Pasa la mercancía averiada a la **bodega de cuarentena** con una transferencia. Queda separada: de allí no sale consumo interno y allí no entran compras |
| 2 | Almacenista | Registra la baja (merma o producto dañado o vencido) con su **causa estándar** y el motivo. Queda Pendiente |
| 3 | Supervisor de Bodega | La autoriza o la rechaza. Al autorizar, el stock baja y se puede generar el **acta de baja** en PDF |
| 4 | Supervisor o Administrador | Consulta el **reporte de pérdidas** del período, con el valor perdido en pesos y totales por causa y por bodega |

Las causas son una lista fija: vencido, roto o golpeado en bodega, daño en transporte, humedad, defecto de fábrica, faltante sin explicación (la asigna el sistema a los ajustes negativos del conteo) y otro. El valor de cada pérdida es la cantidad por el costo promedio del producto en el momento de la baja.

### Sugerencia de compra y orden de compra

La opción *Sugerencia de compra* (Jefe de Compras y Administrador) dice qué pedir y cuánto:

| Dato | Cómo se obtiene |
|---|---|
| Consumo diario | Unidades que salieron en los últimos 90 días ÷ 90 (consumo interno, mermas, daños y faltantes; no cuentan las transferencias ni las devoluciones a proveedor) |
| Días de entrega | Los del proveedor de la última compra; si no tiene, el parámetro general *Días de entrega por defecto* |
| Punto de reorden | Consumo diario × días de entrega + stock mínimo (colchón de seguridad) |
| ¿Hay que pedir? | Sí, cuando el stock disponible (sin contar la cuarentena) llega al punto de reorden |
| Cantidad sugerida | Stock máximo − stock actual |

**Ejemplo:** salen 2 tornillos por día, el proveedor tarda 5 días y el mínimo es 10: el punto de reorden es 2 × 5 + 10 = 20. Si quedan 18 y el máximo es 100, se sugiere pedir 82.

La columna *Pedir* se puede cambiar. Con lo que se decide pedir se genera la **orden de compra** para un proveedor: se guarda con su número (`OC-000001`) y se imprime en PDF con los datos del proveedor, los productos, el costo de referencia y el total estimado. La orden es solo el pedido: el stock cambia cuando llega la mercancía y se registra la compra con la factura.

### Clasificación ABC y conteo cíclico

La pestaña *Clasificación ABC* de Reportes ordena los productos por el valor de lo que sale de la bodega en un período (unidades × costo):

| Clase | Regla | Qué significa |
|---|---|---|
| A | Los que suman hasta el 80 % del valor | Pocos productos que mueven casi todo el dinero: se cuentan más seguido |
| B | Los que siguen, hasta el 95 % | Control intermedio |
| C | El resto y los que no tuvieron salidas | Control básico |

En *Inventario físico* se elige el **tipo de conteo**: general (toda la bodega) o **cíclico** (solo una clase ABC, con el consumo de los últimos 90 días). La casilla **A ciegas** genera la plantilla sin el stock del sistema, para que quien cuenta no se guíe por ese número. Cada conteo queda en la pestaña **Historial** con su **exactitud** (productos que coincidieron ÷ productos contados), haya o no diferencias.

### Vencimientos (FEFO)

Los productos marcados con **Controla vencimiento** piden el **lote** y la **fecha de vencimiento** en cada compra, y no se puede recibir mercancía ya vencida. La opción *Vencimientos* (todos los roles) lista los lotes que siguen en bodega ordenados por fecha: el primero es el que debe salir primero (FEFO: *First Expired, First Out*). Los vencidos salen en rojo y los que vencen dentro de los días de aviso (parámetro *Aviso de vencimiento*, 30 por defecto), en naranja; la tarjeta *Lotes por vencer* de Inicio los cuenta.

El sistema no lleva el stock lote por lote: lo **estima** repartiendo las unidades que hay en bodega con la misma regla FEFO. **Ejemplo:** entran 10 unidades que vencen en enero y 10 que vencen en marzo; si quedan 12, se asume que 10 son del lote de marzo y 2 del de enero.

### Control del período y tablero de indicadores

La pestaña *Control del período* de Reportes (Supervisor y Administrador) muestra, producto por producto, cómo se movió el inventario en un mes o en cualquier rango de fechas:

**Saldo final = saldo inicial + entradas + sobrantes − salidas − pérdidas**

Las pérdidas son mermas, productos dañados o vencidos y faltantes del conteo; los sobrantes son los ajustes positivos del conteo. Las transferencias no se cuentan porque no cambian el total. Se exporta a Excel y se imprime en PDF.

La opción *Indicadores* (roles con reportes) es un tablero con nueve tarjetas y semáforo (verde cumple, naranja alerta, rojo crítico, azul informativo):

| Indicador | Cómo se calcula | Meta |
|---|---|---|
| Valor del inventario | Existencias × costo promedio | Informativo |
| Rotación | Costo de lo que salió ÷ inventario promedio del período | Informativo |
| Días de inventario | Días del período ÷ rotación | Informativo |
| Exactitud del inventario | Productos que coincidieron ÷ productos contados | 95 % o más |
| Pérdidas | Valor de las pérdidas ÷ valor de todo lo que salió | 2 % o menos |
| Productos en bajo stock | Alertas de bajo stock ÷ productos activos | 10 % o menos |
| Productos sin movimiento | Con existencias y sin entradas ni salidas en el período | 20 % o menos |
| Lotes vencidos en bodega | Lotes con fecha pasada y unidades en bodega | 0 |
| Movimientos por autorizar | Entradas y salidas pendientes | 0 |

**Ejemplo de rotación:** salió mercancía por $ 600.000 y el inventario promedio fue (900.000 + 1.100.000) ÷ 2 = $ 1.000.000; la rotación es 0,6 veces y en un mes de 30 días equivale a 30 ÷ 0,6 = 50 días de inventario.

### Respaldos de la base de datos

La opción *Respaldos* (solo el Administrador) crea una copia de seguridad de toda la base en la carpeta `respaldos`, junto al programa, y permite restaurar una anterior. Usa las herramientas oficiales de MySQL (`mysqldump` para respaldar y `mysql` para restaurar), que el sistema busca en `C:\Program Files\MySQL`; si no las encuentra, se escribe la carpeta en `db.properties` con la propiedad `mysql_bin`.

| Función | Cómo funciona |
|---|---|
| Respaldo manual | Botón *Crear respaldo ahora*. El archivo lleva fecha y hora en el nombre y nunca se borra solo |
| Respaldo automático | Al iniciar sesión, si pasaron los días configurados (7 por defecto; 0 lo desactiva), se hace en segundo plano. Se conservan los 10 más recientes |
| Respaldo de seguridad | Se guarda solo justo antes de restaurar (nombre `aristools_antes_de_restaurar_...`, tipo *Seguridad*), por si se elige el archivo equivocado. Se conservan los 5 más recientes |
| Restaurar | Reemplaza la base por la del respaldo. Antes guarda un respaldo de seguridad de la base actual |
| Seguridad | La contraseña de MySQL no va en la línea de comandos: se pasa en un archivo temporal que se borra al terminar |

Los respaldos y las restauraciones quedan en la bitácora. Los archivos `respaldar_bd.bat` y `restaurar_bd.bat` de la carpeta `database` siguen sirviendo para hacerlo sin abrir la aplicación.

### Documentos de soporte

Cada movimiento autorizado tiene su documento en PDF. El número es el prefijo del tipo y el número del movimiento (por ejemplo `BAJ-000057`), así nunca se repite y siempre lleva al registro.

| Prefijo | Documento | Movimientos |
|---|---|---|
| `ENT` | Comprobante de entrada | Compra y devolución |
| `VAL` | Vale de salida | Consumo interno y devolución a proveedor |
| `BAJ` | Acta de baja | Merma y producto dañado o vencido |
| `TRF` | Comprobante de traslado | Transferencia entre bodegas |
| `CNT` | Acta de ajuste por conteo físico | Ajuste positivo y negativo |

Se generan desde *Reportes → Movimientos* (botón **Documento PDF**) y al autorizar un movimiento. **Elaboró** es quien registró el movimiento y **Revisó** quien lo autorizó, con fecha y hora. El título y el prefijo los define cada subclase de `Movimiento` (polimorfismo).

### Reportes en PDF

El botón **Imprimir PDF** está en el kardex, el reporte de movimientos, el reporte de pérdidas, las existencias y la bitácora. Las fechas de esos filtros se eligen en un calendario, con botones rápidos *Hoy*, *Este mes* y *Mes anterior*. El documento lleva el nombre del establecimiento, los filtros usados, la tabla, el resumen y los responsables:

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
| LGoodDatePicker | 11.2.1 | Calendario para elegir las fechas de los filtros |
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

- **Herencia y polimorfismo:** `Movimiento` es una clase abstracta con cuatro subclases (`Entrada`, `Salida`, `Transferencia`, `Ajuste`). Cada una implementa `validar()` y `calcularCambiosStock()` a su manera: la entrada suma, la salida resta y la transferencia resta en el origen y suma en el destino. Para el kardex valorizado, `Entrada` redefine `costoParaValorar()` y `nuevoCostoPromedio()`: solo la compra cambia el costo promedio. Cada subclase define también su documento de soporte (`prefijoDocumento()`, `tituloDocumento()`) y si es una pérdida (`esPerdida()`). `MovimientoDAO.mapear()` crea la subclase correcta al leer de la base. En la vista, `PanelCatalogo` y `PanelMovimiento` son clases abstractas que comparten el comportamiento de las pantallas, y la interfaz `Recargable` permite refrescar cualquier panel.
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
│   ├── actualizar_bd_v6.sql     #   v6: causas de baja y bodega de cuarentena
│   ├── actualizar_bd_v7.sql     #   v7: días de entrega y órdenes de compra
│   ├── actualizar_bd_v8.sql     #   v8: vencimientos e historial de conteos
│   ├── respaldar_bd.bat         # Respaldo (RNF-007)
│   └── restaurar_bd.bat         # Restauración (RNF-007)
├── src/main/java/com/aristools/
│   ├── Main.java                # Punto de entrada del programa
│   ├── conexion/    ConexionDB
│   ├── modelo/      36 clases (entidades, Movimiento y sus 4 subclases, enumeraciones y auxiliares)
│   ├── dao/         15 DAO
│   ├── seguridad/   Accion, Permisos, PasswordUtil, Sesion
│   ├── controlador/ 18 clases (controladores, GestorInventario y Auditoria)
│   ├── util/        NegocioException, Validador, Cambios, Formato, ReportePdf, RespaldoBD
│   └── vista/       37 clases (ventanas, paneles y diálogos Swing)
├── src/main/resources/
│   └── db.properties.ejemplo    # Plantilla de la conexión a MySQL (copiar como db.properties)
├── src/test/java/com/aristools/ # 84 pruebas JUnit
├── src/test/resources/
│   └── db-pruebas.properties    # Conexión de las pruebas: H2 en memoria
├── ejecutar_aristools.bat       # Lanzador del .jar (sin ventana de consola)
├── pom.xml
└── README.md
```

| Paquete | Clases |
|---|---|
| `modelo` | Rol, Usuario, Categoria, Producto, Bodega, Proveedor, Existencia, TipoMovimiento, Alerta, Parametro, RegistroBitacora, **Movimiento** (abstracta), Entrada, Salida, Transferencia, Ajuste, EstadoUsuario, EstadoRegistro, EstadoMovimiento, EstadoAlerta, TipoAlerta, Naturaleza, CausaBaja, CambioStock, TotalPerdida, LineaKardex, LineaConteo, FiltroMovimiento, SugerenciaCompra, OrdenCompra, LineaOrdenCompra |
| `dao` | UsuarioDAO, RolDAO, CategoriaDAO, ProductoDAO, BodegaDAO, ProveedorDAO, TipoMovimientoDAO, ParametroDAO, ExistenciaDAO, MovimientoDAO, AlertaDAO, ReporteDAO, BitacoraDAO, CompraDAO |
| `controlador` | LoginControlador, GestorInventario, MovimientoControlador, AutorizacionControlador, AlertaControlador, ConteoControlador, ReporteControlador, PanelControlador, UsuarioControlador, ProductoControlador, BodegaControlador, ProveedorControlador, ParametroControlador, BitacoraControlador, CompraControlador, Auditoria |
| `vista` | **Ventanas y diálogos:** LoginVista, PrincipalVista, CambiarClaveDialogo, PrimerAdminDialogo · **Pantallas:** InicioPanel, CatalogosPanel, ProductosPanel, CategoriasPanel, BodegasPanel, ProveedoresPanel, UsuariosPanel, MovimientosPanel, EntradaPanel, SalidaPanel, TransferenciaPanel, ConteoPanel, AutorizacionesPanel, ExistenciasPanel, ReportesPanel, SugerenciaCompraPanel, ParametrosPanel, BitacoraPanel · **Base y apoyo:** PanelCatalogo y PanelMovimiento (abstractas), Recargable (interfaz), PanelDesplazable, PanelEnConstruccion, BarraFiltros, CampoFecha, ExportadorCsv, ExportadorPdf, MonitorInactividad, MostrarClave, Tema |

---

## 6. Base de datos

Esquema `aristools` (InnoDB, utf8mb4) con **16 tablas**, 16 llaves primarias, **22 llaves foráneas**, restricciones `UNIQUE` y `CHECK`, 5 índices y 2 disparadores.

| Grupo | Tablas |
|---|---|
| Seguridad | `rol`, `usuario` |
| Catálogos | `categoria`, `producto`, `bodega`, `proveedor`, `tipo_movimiento` |
| Operación | `existencia` (producto × bodega, llave compuesta), `movimiento_inventario` |
| Control | `alerta`, `parametro` |
| Compras | `orden_compra`, `orden_compra_detalle` (llave compuesta) |
| Auditoría | `bitacora` (solo inserción, protegida por 2 disparadores) |

Los valores de estado se guardan en mayúscula (`ACTIVO`, `PENDIENTE`, `AUTORIZADO`…) porque coinciden con las enumeraciones de Java. El stock no se guarda en `producto`: es la suma de `existencia` y solo cambia con movimientos autorizados. Para el kardex valorizado, `producto` guarda el `costo_promedio` y cada movimiento su `costo_unitario`; los datos de prueba se valoran al costo de referencia. Las pérdidas guardan su `causa_baja` y la bodega de cuarentena se marca con `es_cuarentena`.

**Datos obligatorios:** 4 roles, 9 tipos de movimiento (Compra, Devolución, Consumo interno, Merma, Producto dañado o vencido, Devolución a proveedor, Transferencia, Ajuste positivo y Ajuste negativo) y 7 parámetros (nombre del establecimiento, stock mínimo por defecto 10, stock máximo por defecto 100, días de vigencia de la contraseña 0, minutos de inactividad 15, responsable de revisión de los reportes y días de entrega por defecto 5).

**Datos de prueba:** 6 usuarios, 6 categorías, 22 productos (21 activos), 5 bodegas (4 activas, una de cuarentena), 5 proveedores (4 activos), 54 movimientos (6 pendientes de autorización) y 6 alertas activas.

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
| `actualizar_bd_v6.sql` | Averías: `causa_baja` en `movimiento_inventario`, `es_cuarentena` en `bodega` y la bodega de cuarentena |
| `actualizar_bd_v7.sql` | Compras: `dias_entrega` en `proveedor`, tablas `orden_compra` y `orden_compra_detalle` y parámetro `dias_entrega_defecto` |
| `actualizar_bd_v8.sql` | Vencimientos y conteos: `controla_vencimiento` en `producto`, `lote` y `fecha_vencimiento` en `movimiento_inventario`, tablas `conteo` y `conteo_detalle` y parámetro `dias_aviso_vencimiento` |

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
   Se crea `target/Aristools.jar`, con las librerías incluidas (MySQL, BCrypt, FlatLaf, OpenPDF y LGoodDatePicker).
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

## 8. Pruebas automatizadas (84)

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
| `BaseDePruebasTest` | 3 | Las pruebas usan H2 y no MySQL; el script real crea las tablas; el lector separa bien las sentencias de un script |
| `ConexionDBTest` | 2 | `ConexionDB` entrega una conexión válida; existen los 4 roles y los 9 tipos de movimiento |
| `ModeloTest` | 23 | Herencia y polimorfismo, validaciones (cantidad, motivo y causa, proveedor, número de factura y costo en compras, costo inusual), costo promedio ponderado, reglas de la cuarentena, documentos de soporte, totales de pérdidas, sugerencia de compra, orden de compra, segregación de funciones, bloqueo al tercer intento, umbrales y conteo físico |
| `PerdidasTest` | 2 | Flujo completo de una avería: cuarentena, baja con causa, autorización, acta de baja y reporte de pérdidas en pesos; el reporte solo lo ven Supervisor y Administrador |
| `CompraTest` | 2 | Sugerencia de la pintura bajo su mínimo, orden de compra guardada con su total; la sugerencia solo la usan Jefe de Compras y Administrador |
| `AbcYVencimientoTest` | 5 | Clasificación ABC (80 % / 95 %), reparto FEFO del stock entre lotes, estado del lote según los días de aviso, lote y fecha obligatorios en la compra, exactitud del conteo |
| `ConteoYVencimientoTest` | 4 | Compra con lote que aparece en Vencimientos al autorizarse, clasificación ABC de todos los productos, conteo a ciegas guardado en el historial y plantilla cíclica por clase |
| `IndicadoresTest` | 4 | La línea de control separa entradas, salidas, pérdidas y sobrantes; fórmulas de rotación, días de inventario y semáforos; el control del período cuadra con las existencias; el tablero tiene nueve indicadores y respeta los roles |
| `RespaldoBDTest` | 6 | Lectura de la URL de conexión, comando sin contraseña y nombre con fecha, cuándo toca el respaldo automático, qué automáticos y respaldos de seguridad se borran y que solo el Administrador maneja los respaldos |
| `KardexValorizadoTest` | 1 | Flujo completo: costo sugerido de la última compra, compra registrada y autorizada por otro usuario, promedio recalculado, salida valorada y kardex en pesos |
| `FormatoTest` | 3 | Forma de mostrar y de leer valores en pesos |
| `ReportePdfTest` | 3 | El reporte trae encabezado, datos, resumen y responsables; el documento de soporte trae número, datos y responsables; un reporte largo ocupa varias páginas |
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
| 14 | Averías con cuarentena, causas de baja, reporte de pérdidas y documentos de soporte | ✅ |
| 15 | Sugerencia de compra y orden de compra en PDF | ✅ |
| 16 | Clasificación ABC, conteo cíclico y a ciegas, historial de conteos y vencimientos (FEFO) | ✅ |
| 17 | Control de inventario del período y tablero de indicadores (KPI) | ✅ |
| 18 | Respaldo y restauración desde la aplicación y respaldo automático | ✅ |

### Mejoras planeadas para la entrega final

Respaldo automático de la base de datos y pruebas finales.

**Trabajo futuro (fuera del alcance):** base de datos en red o en la nube, control de stock por lote y lector de código de barras.

La documentación completa (requerimientos, casos de uso, diagramas UML, modelo entidad-relación y mockups) está en el documento del Proyecto Integrador.
