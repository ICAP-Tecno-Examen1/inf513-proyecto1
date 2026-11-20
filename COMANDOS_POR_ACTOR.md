# Comandos por actor y flujos del sistema

Cada comando se escribe en el **asunto** de un correo enviado al buzón del sistema. El sistema reconoce
a la persona por el correo desde el que escribe y responde a ese mismo correo.

```
AAAEEE["param1","param2",...]
```

- `AAA` es la acción (`INS` crear, `LIS` listar, `MOD` modificar, `DEL` desactivar, `CAN` cancelar,
  `APR` aprobar, `REC` rechazar, `CON` confirmar) y `EEE` la entidad (`USU`, `PRO`, `OFE`, `PAG`...).
- Los ids (`id_oferta`, `id_matricula`, `id_cuota`, `id_pago`) salen de las respuestas anteriores.
- Los parámetros finales opcionales se pueden omitir. En `MOD`, `-` conserva el valor actual.
- `HELP` devuelve la lista de comandos que puede usar quien lo envía.

## Actores

| Actor | Quién es | Qué hace |
| --- | --- | --- |
| Visitante | Cualquier correo sin registrar | Consulta el catálogo y se registra |
| Estudiante | Se registra solo con `INSUSU` | Se preinscribe, paga y consulta su matrícula |
| Secretaria | Cuenta creada por el Director | Revisa solicitudes, aplica becas y concilia pagos |
| Coordinador Académico | Cuenta creada por el Director | Gestiona el catálogo y las becas; también hace lo de la Secretaria |
| Director | Cuenta inicial del sistema | Administra usuarios, roles y permisos; supervisa |
| Profesor | Cuenta creada por el Director | Solo sus datos propios en este parcial |

Lo que puede hacer cada rol depende de sus permisos. Esta es la asignación inicial
(`datos_iniciales.sql`); el Director puede cambiarla con `INSPER` y `DELPER`.

| Permiso | Estudiante | Secretaria | Coordinador | Director | Profesor |
| --- | --- | --- | --- | --- | --- |
| `USUARIO_GESTIONAR` | | | | ✓ | |
| `USUARIO_VER` | | ✓ | ✓ | ✓ | |
| `ROL_GESTIONAR` | | | | ✓ | |
| `CATALOGO_GESTIONAR` | | | ✓ | ✓ | |
| `PREINSCRIPCION_CREAR` | ✓ | | | | |
| `PREINSCRIPCION_REVISAR` | | ✓ | ✓ | | |
| `INSCRIPCION_VER` | | ✓ | ✓ | ✓ | |
| `BECA_GESTIONAR` | | | ✓ | ✓ | |
| `BECA_ASIGNAR` | | ✓ | ✓ | | |
| `PAGO_REGISTRAR` | ✓ | ✓ | ✓ | | |
| `PAGO_CONFIRMAR` | | ✓ | ✓ | | |
| `PAGO_VER` | | ✓ | ✓ | ✓ | |

## Comandos por actor

### Visitante (sin registro)

| Comando | Uso |
| --- | --- |
| `HELP` | Ver los comandos disponibles |
| `LISPRO[]` | Ver los programas académicos |
| `LISPRO["id"]` | Ver un programa y sus ofertas abiertas |
| `LISOFE[]` | Ver las ofertas: modalidad, fechas, cupos y costo |
| `LISOFE["id"]` | Ver una oferta |
| `LISARA["id_oferta"]` | Ver el detalle de costos de una oferta |
| `INSUSU["nombres","apellidos","ci","CI\|PASAPORTE","correo","username","password","telefono","direccion","AAAA-MM-DD"]` | Registrarse como Estudiante. El correo debe ser el mismo desde el que se envía |

Cualquier otro comando responde que el correo no está registrado.

### Todos los usuarios registrados

Además de los comandos del visitante, cualquier usuario activo puede usar:

| Comando | Uso |
| --- | --- |
| `LISUSU["-"]` | Ver sus datos personales |
| `MODUSU["ci","nombres","apellidos","telefono","direccion","fecha_nacimiento"]` | Actualizar sus datos |
| `MODCLA["clave_actual","clave_nueva"]` | Cambiar su contraseña |
| `LISBEC[]` | Ver las becas disponibles |
| `LISMET[]` | Ver los métodos de pago |
| `LISPRE[]`, `LISMAT[]`, `LISPAG[]` | Ver sus propias solicitudes, matrículas y pagos |

El **Profesor** solo tiene estos comandos en este parcial.

### Estudiante

| Comando | Uso |
| --- | --- |
| `INSPRE["id_oferta","observacion"]` | Preinscribirse en una oferta abierta y con cupos |
| `LISPRE[]` | Ver sus solicitudes y su estado |
| `CANPRE["id"]` | Cancelar una solicitud propia |
| `LISMAT[]`, `LISMAT["id"]` | Ver sus matrículas |
| `LISCUO["id_matricula"]` | Ver sus cuotas, saldos y vencimientos |
| `PAGCUO["id_cuota"]` | Generar el QR de Pago Fácil por el saldo de una cuota |
| `PAGCUO["id_cuota","nombres","apellidos","CI\|PASAPORTE\|NIT","num_doc","telefono","correo","parentesco"]` | Igual, para que pague un tercero, que también recibe el QR |
| `RESPAG["id_matricula","QR\|FISICO"]` | Elegir cómo pagar la siguiente cuota: recibe el QR o la indicación de pagar en Secretaría |
| `PAGARQR["id_pago"]` | Volver a generar el QR de un pago pendiente |
| `INSPAG["id_cuota","id_metodo","monto","referencia","comprobante_url"]` | Registrar un pago hecho al QR institucional (QR manual) |
| `INSPAG[...,"comprobante_url","nombres","apellidos","CI\|PASAPORTE\|NIT","num_doc","telefono","correo","parentesco"]` | Igual, indicando al tercero que pagó |
| `LISPAG[]`, `LISPAG["PENDIENTE\|CONFIRMADO\|RECHAZADO"]` | Ver su historial de pagos |

El estudiante solo opera sobre sus propias solicitudes, matrículas y cuotas, y no puede registrar pagos
en efectivo.

### Secretaria

| Comando | Uso |
| --- | --- |
| `LISUSU[]`, `LISUSU["ci"]` | Consultar usuarios |
| `LISPRE[]`, `LISPRE["PENDIENTE\|APROBADA\|RECHAZADA\|CANCELADA"]` | Ver todas las solicitudes, o por estado |
| `APRPRE["id","observacion"]` | Aprobar una solicitud: crea la matrícula y sus cuotas |
| `RECPRE["id","motivo"]` | Rechazar una solicitud |
| `CANPRE["id"]` | Cancelar la solicitud de un estudiante |
| `LISMAT[]`, `LISMAT["id"]` | Ver todas las matrículas |
| `LISCUO["id_matricula"]` | Ver las cuotas de cualquier matrícula |
| `INSBES["id_matricula","id_beca","porcentaje"]` | Aplicar una beca; sin porcentaje usa el de la beca |
| `DELBES["id_matricula"]` | Quitar la beca |
| `PEDPAG["id_matricula"]` | Pedir al estudiante que elija la forma de pago |
| `INSPAG["id_cuota","id_metodo","monto","referencia"]` | Registrar un pago, incluido el efectivo |
| `PAGCUO`, `RESPAG`, `PAGARQR` | Generar un QR a nombre de un estudiante |
| `LISPAG[]`, `LISPAG["PENDIENTE"]` | Ver todos los pagos, o por estado |
| `CONPAG["id"]` | Confirmar un pago |
| `RECPAG["id","motivo"]` | Rechazar un pago y liberar el saldo de la cuota |

### Coordinador Académico

Puede usar **todos los comandos de la Secretaria** y además:

| Comando | Uso |
| --- | --- |
| `INSPRO["codigo","nombre","descripcion","tipo"]` | Registrar un programa |
| `MODPRO["id","nombre","descripcion","tipo","ACTIVO\|INACTIVO"]` | Actualizar un programa |
| `DELPRO["id"]` | Retirar un programa del catálogo |
| `INSMAL["id_programa","version","horas_totales","fecha_vigencia"]` | Registrar una malla |
| `LISMAL["id_programa"]` | Ver las mallas de un programa |
| `INSOFE["codigo","id_malla","PRESENCIAL\|VIRTUAL\|SEMIPRESENCIAL","inicio","fin","cupo_max","cupo_min","gestion"]` | Registrar una oferta |
| `MODOFE["id","ABIERTA\|CERRADA","fecha_fin","cupo_maximo"]` | Actualizar una oferta |
| `DELOFE["id"]` | Cerrar una oferta |
| `INSARA["id_oferta","concepto","monto","UNICO\|MENSUAL"]` | Registrar un costo; en `MENSUAL`, el monto es por mes |
| `DELARA["id"]` | Desactivar un costo |
| `INSBEC["nombre","descripcion","porcentaje","institucion"]` | Registrar una beca |

Con `LISPRO[]` y `LISOFE[]` ve también los programas inactivos y las ofertas cerradas.

### Director

| Comando | Uso |
| --- | --- |
| `INSUSU["nombres","apellidos","ci","CI\|PASAPORTE","correo","username","password","Director\|Secretaria\|Coordinador\|Profesor\|Estudiante","telefono","direccion","AAAA-MM-DD"]` | Registrar un usuario con su rol |
| `LISUSU[]`, `LISUSU["ci"]` | Consultar usuarios |
| `MODUSU["ci",...]` | Actualizar los datos de cualquier usuario |
| `DELUSU["ci"]` | Desactivar un usuario |
| `LISROL[]` | Ver los roles y sus permisos |
| `INSROL["ci","rol"]`, `DELROL["ci","rol"]` | Asignar o quitar un rol a un usuario |
| `LISPER[]` | Ver los permisos disponibles |
| `INSPER["rol","permiso"]`, `DELPER["rol","permiso"]` | Dar o quitar un permiso a un rol |
| Catálogo y aranceles | Los mismos comandos del Coordinador: `INSPRO`, `MODPRO`, `DELPRO`, `INSMAL`, `LISMAL`, `INSOFE`, `MODOFE`, `DELOFE`, `INSARA`, `DELARA` |
| `INSBEC[...]` | Registrar una beca |
| `LISMAT[]`, `LISCUO["id_matricula"]`, `LISPAG[]` | Supervisar todas las matrículas, cuotas y pagos |

Con los permisos iniciales, el Director no revisa solicitudes, no aplica becas y no registra ni confirma
pagos. Tampoco puede desactivar su propia cuenta.

## Flujos

### 1. Preparar el catálogo (Coordinador o Director)

```
INSPRO → INSMAL → INSOFE → INSARA
```

1. `INSPRO` registra el programa.
2. `INSMAL` le agrega una malla (versión y horas).
3. `INSOFE` abre una oferta de esa malla, con modalidad, fechas y cupos.
4. `INSARA` define sus costos. Un arancel `UNICO` genera una sola cuota; uno `MENSUAL` genera una cuota
   por cada mes de la oferta.

Desde ese momento, la oferta aparece en `LISOFE[]` para cualquier visitante.

### 2. Registro del estudiante (Visitante)

1. Consulta el catálogo con `LISPRO[]`, `LISOFE[]` y `LISARA["id_oferta"]`.
2. Envía `INSUSU` con sus datos, usando el mismo correo desde el que escribe.
3. La cuenta queda creada con rol Estudiante; no hay inicio de sesión.

El resto del personal lo registra el Director con `INSUSU` indicando el rol.

### 3. Preinscripción y matrícula (Estudiante y Secretaria)

```
INSPRE (PENDIENTE) → Secretaria revisa con LISPRE["PENDIENTE"]
                          ├─ RECPRE → RECHAZADA, el estudiante recibe el motivo
                          └─ APRPRE → APROBADA, matrícula PENDIENTE_PAGO + cuotas
```

1. El estudiante envía `INSPRE["id_oferta"]`. La oferta debe estar abierta y con cupos, y no puede tener
   otra solicitud vigente en ella.
2. La Secretaria o el Coordinador la revisan con `LISPRE["PENDIENTE"]`.
3. Con `APRPRE` se crea la matrícula en `PENDIENTE_PAGO` y se generan sus cuotas. El estudiante recibe un
   correo con el total y el `id_cuota` de cada cuota.
4. Con `RECPRE` la solicitud queda `RECHAZADA` y el estudiante recibe el motivo.

**Cancelación:** el estudiante (o el personal) puede cancelar con `CANPRE["id"]` mientras la matrícula no
tenga pagos vigentes; esto libera el cupo.

### 4. Beca (Secretaria o Coordinador), opcional

1. El Coordinador o el Director registran la beca con `INSBEC`.
2. La Secretaria o el Coordinador la aplican con `INSBES["id_matricula","id_beca"]`.
3. Los montos de las cuotas se recalculan y el estudiante recibe el nuevo total.
4. Una beca del 100 % confirma la matrícula sin pago. `DELBES` quita la beca.

### 5. Pago con QR de Pago Fácil (Estudiante)

```
PAGCUO o RESPAG[...,"QR"] → pago PENDIENTE + correo con el QR
        → el estudiante o un tercero escanea y paga
        → el webhook confirma → cuota PAGADA → matrícula CONFIRMADA
```

1. Opcional: la Secretaria envía `PEDPAG["id_matricula"]` y el estudiante recibe la cuota pendiente y las
   opciones de pago.
2. El estudiante consulta sus cuotas con `LISCUO["id_matricula"]` y envía `PAGCUO["id_cuota"]`, o
   `RESPAG["id_matricula","QR"]` para la siguiente cuota pendiente.
3. Llega un correo con el QR por el saldo de la cuota. Si se indicaron los datos de un tercero, él también
   lo recibe.
4. Al pagarse, Pago Fácil avisa al webhook, que confirma el pago automáticamente y avisa al estudiante.
5. `PAGARQR["id_pago"]` genera un QR nuevo para el mismo pago si el anterior se perdió o venció.

Casos especiales:

- Si el aviso de Pago Fácil no llega, la Secretaria confirma con `CONPAG["id_pago"]`.
- Un QR sin pagar reserva el saldo de la cuota. Si no se va a pagar, la Secretaria lo libera con
  `RECPAG["id_pago","motivo"]`.

### 6. Pago con QR manual o en efectivo (Estudiante y Secretaria)

```
INSPAG (PENDIENTE) → Secretaria revisa con LISPAG["PENDIENTE"]
                          ├─ CONPAG → CONFIRMADO, cuota PAGADA
                          └─ RECPAG → RECHAZADO, la cuota se puede volver a pagar
```

- **QR manual:** el estudiante paga al QR institucional y registra el pago con `INSPAG`, indicando la
  referencia y, si quiere, la URL del comprobante.
- **Efectivo:** el estudiante elige `RESPAG["id_matricula","FISICO"]` y paga en Secretaría. Solo el personal
  registra el efectivo, con `INSPAG` y el método `EFECTIVO`.
- En ambos casos el pago queda `PENDIENTE` hasta que la Secretaria o el Coordinador lo confirman con
  `CONPAG` o lo rechazan con `RECPAG`. El estudiante recibe un aviso con el resultado.
- El monto no puede superar el saldo de la cuota.

### 7. Confirmación de la matrícula

La matrícula pasa de `PENDIENTE_PAGO` a `CONFIRMADA` de forma automática cuando están pagados los cobros
únicos y la primera cuota de cada arancel mensual. El estudiante lo comprueba con `LISMAT[]` y `LISPAG[]`.

En una oferta con mensualidades, las demás cuotas se pagan después con los mismos comandos de los flujos
5 y 6. Las cuotas vencidas se muestran como `VENCIDA`; no se calculan intereses.

### 8. Administración de usuarios y permisos (Director)

1. `INSUSU` con rol registra al personal; `DELUSU["ci"]` desactiva una cuenta sin borrar su historial.
2. `INSROL` y `DELROL` asignan o quitan roles a un usuario.
3. `INSPER` y `DELPER` cambian los permisos de un rol, y con ellos lo que cada actor puede hacer, sin
   tocar el código. `LISROL[]` y `LISPER[]` muestran el estado actual.

Ejemplos completos de cada comando, con datos de prueba, en [GUIA_DE_PRUEBAS.md](GUIA_DE_PRUEBAS.md).
