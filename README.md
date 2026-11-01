# Sistema de Gestión Académica ICAP-UAGRM — por Email

Los usuarios envían comandos en el **asunto** de un correo. El sistema los lee por POP3, ejecuta la
operación en PostgreSQL y responde con un email HTML.

- [CASOS_DE_USO.md](CASOS_DE_USO.md): descripción detallada de los casos de uso.
- [GUIA_DE_PRUEBAS.md](GUIA_DE_PRUEBAS.md): guion de la demostración, comando por comando.

## Estado del proyecto

| Área | Estado |
| --- | --- |
| CU1 Usuarios, roles y permisos | Implementado |
| CU2 Catálogo de programas y ofertas | Implementado |
| CU3 Preinscripción y matrícula | Implementado |
| CU4 Aranceles, becas y pagos | Implementado |
| Pago Fácil (QR y webhook) | Implementado |
| Pruebas automáticas | 18 de 18 correctas (`mvn test`, 01/11/2026) |
| Conexión a `db_grupo15sa` y al buzón `grupo15sa` | Comprobada (01/11/2026). Las 29 tablas de `public` coinciden con el script y aún no tienen datos |
| Flujo con correos y Pago Fácil reales | **Sin comprobar:** las pruebas usan una base temporal y servidores de correo y de Pago Fácil simulados |
| Asistencia, notas, certificados, repositorio histórico y reportes avanzados | **Pendiente** (siguientes parciales) |

## Qué se hizo

**CU1. Usuarios**
- Auto-registro como Estudiante y registro de otros usuarios por el Director.
- Consulta y actualización de datos propios, y cambio de contraseña.
- Administración de usuarios, roles y permisos sin tocar código.

**CU2. Catálogo**
- Consulta pública de programas, ofertas y costos.
- Gestión de programas, mallas y ofertas.

**CU3. Preinscripción y matrícula**
- Solicitud, aprobación, rechazo y cancelación de preinscripciones.
- Creación de la matrícula con sus cuotas, y control de cupos.
- Confirmación automática de la matrícula cuando se paga lo requerido.

**CU4. Aranceles, becas y pagos**
- Aranceles de pago único o mensual, y consulta de cuotas y saldos.
- Becas con recálculo de montos; una beca del 100 % confirma la matrícula sin pago.
- Pagos con QR de Pago Fácil, confirmados automáticamente por el webhook.
- Pagos en efectivo o con QR manual (referencia y URL del comprobante).
- Cualquiera de estos pagos puede hacerlo el estudiante o un tercero.
- Conciliación de pagos: confirmar o rechazar con motivo, e historial por estado.

**Infraestructura**
- Cliente POP3 y envío SMTP por el servidor TecnoWeb o por Gmail, incluido el correo con el QR.
- Todo el paquete `postgresConecction` de la referencia, incluidas sus utilidades de prueba.
- Intérprete de comandos, respuestas HTML y ayuda (`HELP`) según el rol.
- Scripts de la base de datos con datos iniciales de ejemplo.

## Qué falta

### Antes de la demostración

- [ ] Cambiar los correos `@ejemplo.com` de `datos_iniciales.sql` por cuentas reales y ejecutar ese script.
- [ ] Completar en el `.env` las credenciales de Pago Fácil.
- [ ] Exponer el webhook con una URL pública (por ejemplo, ngrok) y ponerla en `PAGOFACIL_CALLBACK_URL`.
- [ ] Probar el flujo completo con correos reales y un pago QR real, siguiendo [GUIA_DE_PRUEBAS.md](GUIA_DE_PRUEBAS.md).
- [ ] Cambiar la contraseña inicial con `MODCLA`.

### Siguientes parciales

Las funciones pendientes son asistencia, notas, certificados, repositorio histórico y reportes
avanzados. Las 12 tablas sin usar ya están en `database_script.sql`, pero todavía no tienen clases de
negocio ni de datos, ni comandos. Esta agrupación es orientativa, deducida de los nombres de las tablas:

| Tema | Tablas |
| --- | --- |
| Módulos y plan de estudios | `MODULO`, `MALLA_MODULO`, `OFERTA_MODULO` |
| Docentes e inscripción a módulos | `ASIGNACION_DOCENTE`, `INSCRIPCION_MODULO` |
| Evaluaciones y notas | `EVALUACION`, `NOTA`, `CORRECCION_NOTA` |
| Asistencia | `ASISTENCIA` |
| Documentos y certificados | `DOCUMENTO`, `EXPEDIENTE_DOCUMENTO`, `CERTIFICADO` |

El rol Profesor ya existe, pero no tiene funciones académicas en este parcial.

### Limitaciones conocidas

- **Remitente sin autenticar:** igual que en la referencia, el sistema confía en el correo remitente, así
  que quien falsifique una dirección puede actuar con los permisos de esa persona. Además, las contraseñas
  viajan en el asunto. Es aceptable para esta demostración, no para datos reales.
- **Sin inicio de sesión:** como en la referencia, el sistema identifica a cada persona solo por su correo, así que el "inicio y cierre de sesión" del alcance de CU1 no está implementado.
- **Posible pérdida de correos:** se borran del buzón antes de procesarse, así que si el sistema se
  detiene justo en ese momento, esos comandos se pierden.
- **Adjuntos:** no se leen; en el QR manual, el comprobante se indica mediante una URL.
- **Webhook sin firma:** la referencia no valida ninguna firma de Pago Fácil, y esta integración tampoco. Para
  que un pago no se confirme con un aviso falso, cada pedido lleva una referencia aleatoria difícil de adivinar.
- **QR sin pagar:** un QR pendiente reserva el saldo de la cuota. Si no se paga, la Secretaria debe rechazarlo
  con `RECPAG` para liberar el saldo, cambiar la beca o cancelar la matrícula. Un QR no caduca solo en el sistema.
- **Pago Fácil confirmado tras un rechazo:** si un QR rechazado se paga después, el sistema no lo acredita y solo
  deja el aviso en la consola; hay que revisarlo a mano.
- **Intereses por mora:** no se calculan. No están en el alcance ni en el esquema, por lo que no se incluyó el
  programador de cuotas de la referencia. Las cuotas vencidas solo se muestran como `VENCIDA`.
- **Motivo de rechazo de un pago:** se envía por correo, pero no se guarda porque `PAGO` no tiene columna para ello.

## Cómo conectarse

Requiere Java 17, Maven y PostgreSQL.

### 1. Preparar la base de datos

La base es `db_grupo15sa`, y las tablas de `database_script.sql` ya están creadas en `public`; no vuelvas a
ejecutar ese script, porque fallaría. Falta cargar los datos iniciales. Primero cambia en `datos_iniciales.sql`
los correos `@ejemplo.com` por cuentas reales, porque el sistema identifica a cada persona por el correo desde
el que escribe. Luego ejecuta:

```bash
psql -h www.tecnoweb.org.bo -U grupo15sa -d db_grupo15sa -f datos_iniciales.sql
```

| Rol | Usuario | Correo de ejemplo | Contraseña inicial |
| --- | --- | --- | --- |
| Director | `director` | `director@ejemplo.com` | `Icap2026*` |
| Secretaria | `secretaria` | `secretaria@ejemplo.com` | `Icap2026*` |
| Coordinador Académico | `coordinador` | `coordinador@ejemplo.com` | `Icap2026*` |
| Profesor | `profesor` | `profesor@ejemplo.com` | `Icap2026*` |

Los estudiantes se registran solos con `INSUSU`.

### 2. Configurar el `.env`

El `.env` ya está creado; faltan los datos de Pago Fácil. Este archivo no se sube al repositorio.

| Variable | Uso |
| --- | --- |
| `PAGOFACIL_TOKEN_SERVICE`, `PAGOFACIL_TOKEN_SECRET` | Credenciales de Pago Fácil |
| `PAGOFACIL_CALLBACK_URL` | URL pública que llega al webhook; debe terminar en `/webhook/pagofacil` |

Como en la referencia, los datos de la base y del buzón del grupo 15 están en `DBConnection`, `EmailReceipt` y `EmailSend`. El modo de envío y la cuenta de Gmail se configuran en `run.java` (`USE_GMAIL`, `GMAIL_USER` y `GMAIL_APP_PASSWORD`); Gmail solo se usa para enviar las respuestas.

Sin las variables de Pago Fácil el sistema arranca igual, pero los comandos de QR responden con un error.
Para recibir el webhook desde tu equipo, ejecuta `ngrok http 8080` y usa la URL que te entregue.

### 3. Iniciar el sistema

```bash
mvn compile exec:java
```

Al iniciar, muestra la base, el buzón y el modo SMTP, y revisa el buzón cada 5 segundos. Si falta una
variable obligatoria, se detiene indicando cuál.

### 4. Enviar el primer comando

Envía un correo a `MAIL_ADDRESS` con `HELP` en el asunto; la respuesta lista los comandos disponibles
para ti. El sistema te reconoce por el correo desde el que escribes, sin pasos adicionales.

### Pruebas automáticas

Descargan un PostgreSQL temporal; no usan tu base, tu correo ni Pago Fácil.

```bash
mvn test
```

## Flujo del sistema

### Recorrido de un comando

```
EmailReceipt (POP3) → EmailApp.onReceiptEmail → Interpreter → ICasoUsoListener (EmailApp)
                                                                     ↓
                     EmailSend (SMTP) ← respuesta HTML ← Negocio (N*) → Datos (D*) → PostgreSQL
```

1. `EmailReceipt` descarga los correos nuevos y los borra del buzón.
2. `EmailApp` ignora los rebotes y comprueba que el remitente esté registrado, salvo en los comandos públicos.
3. `Interpreter` separa la acción, la entidad y los parámetros del asunto.
4. La clase de negocio valida los datos y los permisos, y la de datos ejecuta el SQL.
5. `EmailSend` responde al remitente con el resultado o con un mensaje de error.

### Flujo del estudiante, de la consulta a la matrícula

```
Consulta el catálogo → Se registra → Se preinscribe (PENDIENTE)
                                                            ↓
                                     Secretaria revisa → Rechaza (RECHAZADA, con motivo)
                                                            ↓ Aprueba
                                     Matrícula PENDIENTE_PAGO + cuotas
                                                            ↓ (beca opcional)
                  ┌─────────────────────────┴──────────────────────────┐
         QR de Pago Fácil (PAGCUO o RESPAG)                 Efectivo o QR manual (INSPAG)
         Pago PENDIENTE + correo con el QR                  Pago PENDIENTE
                  ↓ Escanea y paga                                     ↓
         Webhook de Pago Fácil confirma                     Secretaria confirma o rechaza
                  └─────────────────────────┬──────────────────────────┘
                                            ↓
                                  Cuota PAGADA → Matrícula CONFIRMADA → Aviso al estudiante
```

La Secretaria también puede confirmar un pago de Pago Fácil con `CONPAG` si el webhook no llega.

La matrícula se confirma al pagarse los cobros únicos y la primera cuota de cada arancel mensual. Mientras
no tenga pagos vigentes, se puede cancelar, lo que libera el cupo.

### Quién hace qué

| Acción | Estudiante | Secretaria | Coordinador | Director | Profesor |
| --- | --- | --- | --- | --- | --- |
| Datos personales propios | ✓ | ✓ | ✓ | ✓ | ✓ |
| Administrar usuarios y roles | | | | ✓ | |
| Gestionar catálogo y aranceles | | | ✓ | ✓ | |
| Solicitar preinscripción | ✓ | | | | |
| Aprobar o rechazar solicitud | | ✓ | ✓ | | |
| Aplicar beca | | ✓ | ✓ | | |
| Registrar pago QR | ✓ | ✓ | ✓ | | |
| Registrar efectivo y conciliar pagos | | ✓ | ✓ | | |
| Supervisar matrículas y pagos | | ✓ | ✓ | ✓ | |

## Sintaxis de los comandos

```
AAAEEE["param1","param2",...]
```

Acciones: `INS` crear, `LIS` listar, `MOD` modificar, `DEL` eliminar o desactivar, `CAN` cancelar,
`APR` aprobar, `REC` rechazar, `CON` confirmar. En `MOD`, `-` conserva el valor actual. Al listar, `["*"]`
equivale a `[]`: `LISOFE["*"]` muestra todas las ofertas.

| Entidad | Código | Entidad | Código |
| --- | --- | --- | --- |
| Preinscripción | `PRE` | Matrícula | `MAT` |
| Usuario | `USU` | Arancel | `ARA` |
| Contraseña | `CLA` | Cuota | `CUO` |
| Rol | `ROL` | Beca | `BEC` |
| Permiso | `PER` | Beca de un estudiante | `BES` |
| Programa | `PRO` | Método de pago | `MET` |
| Malla | `MAL` | Pago | `PAG` |
| Oferta | `OFE` | | |

Comandos de pago dedicados, como en la referencia:

| Comando | Uso |
| --- | --- |
| `PAGCUO["id_cuota"]` | Genera el QR de Pago Fácil por el saldo de una cuota; admite los datos de un tercero |
| `PAGARQR["id_pago"]` | Vuelve a generar el QR de un pago pendiente |
| `RESPAG["id_matricula","QR\|FISICO"]` | El estudiante elige cómo pagar la siguiente cuota |
| `PEDPAG["id_matricula"]` | El personal pide al estudiante que elija la forma de pago |

No todas las combinaciones existen. Enviar `HELP` muestra los comandos que tu rol puede usar, y
[GUIA_DE_PRUEBAS.md](GUIA_DE_PRUEBAS.md) tiene ejemplos completos.

## Arquitectura

Usa las mismas capas y paquetes que el proyecto de referencia (`sa-proyecto-via-email`):

```
src/main/java/
├── com/mycompany/parcial1/tecnoweb/   run (arranque y .env), EmailApp (controlador)
├── interfaces/                        ICasoUsoListener, IEmailListener
├── librerias/                         Interpreter, ParamsAction, Email, HtmlRes, analex/Token
├── negocio/                           una clase N* por tabla, más Validar
├── data/                              una clase D* por tabla, más DBase
└── postgresConecction/                conexión, correo y Pago Fácil (detalle abajo)
```

| Clase de `postgresConecction` | Uso |
| --- | --- |
| `DBConnection`, `SqlConnection` | Conexión a PostgreSQL |
| `EmailReceipt`, `EmailSend` | Recepción por POP3 y envío por SMTP |
| `PagoFacilService` | Inicio de sesión en la API de Pago Fácil y generación del QR |
| `WebHookServer` | Recibe el aviso de Pago Fácil y confirma el pago |
| `ClientePOP`, `ClienteSMTP`, `PostgresSqlConecction`, `Servidor` | Utilidades de prueba que se ejecutan por separado; fuera del flujo principal |

Cada tabla usada en este parcial tiene su clase de negocio y su DAO:

| Caso de uso | Tabla | Negocio | Datos |
| --- | --- | --- | --- |
| CU1 | `USUARIO` | `NUsuario` | `DUsuario` |
| CU1 | `ROL` | `NRol` | `DRol` |
| CU1 | `PERMISO` | `NPermiso` | `DPermiso` |
| CU1 | `USUARIO_ROL` | `NUsuarioRol` | `DUsuarioRol` |
| CU1 | `ROL_PERMISO` | `NRolPermiso` | `DRolPermiso` |
| CU2 | `PROGRAMA` | `NPrograma` | `DPrograma` |
| CU2 | `MALLA_CURRICULAR` | `NMalla` | `DMalla` |
| CU2 | `OFERTA_ACADEMICA` | `NOferta` | `DOferta` |
| CU3 | `PREINSCRIPCION` | `NPreinscripcion` | `DPreinscripcion` |
| CU3 | `MATRICULA` | `NMatricula` | `DMatricula` |
| CU4 | `ARANCEL` | `NArancel` | `DArancel` |
| CU4 | `MENSUALIDAD` | `NMensualidad` | `DMensualidad` |
| CU4 | `BECA` | `NBeca` | `DBeca` |
| CU4 | `BECA_ESTUDIANTE` | `NBecaEstudiante` | `DBecaEstudiante` |
| CU4 | `METODO_PAGO` | `NMetodoPago` | `DMetodoPago` |
| CU4 | `PAGO` | `NPago` | `DPago` |
| CU4 | `PAGADOR` | `NPagador` | `DPagador` |

Las operaciones que
tocan varias tablas (por ejemplo, aprobar una preinscripción) corren en una sola transacción.

## Decisiones de diseño

| Tema | Decisión |
| --- | --- |
| Identidad | Como en la referencia, cada persona se identifica por el correo remitente. |
| Permisos | Se comprueban con `DUsuario.exigirPermiso` y se leen de `USUARIO_ROL` → `ROL_PERMISO` → `PERMISO`, de modo que el Director los cambia sin tocar código. |
| Contraseñas | Solo en `password_hash` (BCrypt). La columna `password` queda en NULL. |
| Bajas | Usuarios, programas, ofertas y aranceles se desactivan; no se borran, para conservar el historial. |
| Cobros | Toda obligación es una fila de `MENSUALIDAD`; un arancel `UNICO` genera una sola cuota. Un arancel `MENSUAL` indica el monto por mes y genera una cuota por cada mes de la oferta. |
| Estados de matrícula | `PENDIENTE_PAGO` → `CONFIRMADA`, o `CANCELADA`. |
| Estados de pago | `PENDIENTE` → `CONFIRMADO` o `RECHAZADO`. |
| Pago QR | El QR de Pago Fácil se confirma por webhook. Se conserva además el QR manual con referencia y comprobante, como respaldo. |
| Pedido de Pago Fácil | `CUOTA-<id cuota>-<id pago>-<referencia>-<sufijo>`: cada QR tiene un sufijo propio y todos los de un mismo pago lo confirman. |
| Roles | Definidos en `NRol`: Director, Secretaria, Coordinador Académico, Profesor y Estudiante. |
| Crédito | A diferencia de la referencia, el estudiante no elige las cuotas: las define el arancel de la oferta. |
| Orden | Los correos se procesan de uno en uno, para que los comandos de un mismo lote se atiendan en orden. |

## Diferencias con el proyecto de referencia

- No se incluye `CuotaScheduler`: calcula intereses por mora, que no están en este alcance.
- No se incluye el analizador léxico legado (`Analex`); el formato `AAAEEE[...]` lo resuelve `Interpreter`.
- Las respuestas no repiten el asunto, para no devolver contraseñas.
- Se corrigieron fallos de la referencia que afectarían aquí: parámetros vacíos que desplazaban a los
  siguientes, lectura frágil de cabeceras y datos del usuario sin escapar en el HTML.
- En `postgresConecction`, las clases son copia de la referencia con el grupo 15. `PagoFacilService` y
  `WebHookServer` se adaptaron a cuotas de matrícula, y el pedido lleva una referencia aleatoria que el webhook valida.
