# Descripción de casos de uso — Primer parcial ICAP-UAGRM

Alcance: el flujo de un estudiante desde que consulta los programas hasta que la administración
confirma su matrícula. Asistencia, notas, certificados, repositorio histórico y reportes avanzados
quedan para los siguientes parciales.

**Precondición común:** salvo que se indique otra cosa, el actor escribe desde el correo de su cuenta, que
es lo que lo identifica ante el sistema.

---

## CU1. Gestión de Usuarios

### CU1.1 Registrar usuario

- **Objetivo:** crear una cuenta con datos personales y un rol.
- **Actor principal:** Estudiante (auto-registro) o Director.
- **Actores secundarios:** ninguno.
- **Precondiciones:** el documento, el correo y el username no están registrados. El auto-registro debe enviarse desde el correo que se registra.
- **Flujo principal:**
  1. El actor envía sus datos personales.
  2. El sistema valida los datos y guarda la contraseña cifrada.
  3. El sistema crea la cuenta: con rol Estudiante en el auto-registro, o con el rol que el Director debe indicar (Director, Secretaria, Coordinador Académico, Profesor o Estudiante).
  4. El sistema responde con los datos de la cuenta.
- **Flujos alternativos:**
  - Falta un dato o tiene formato inválido: el sistema indica cuál y no crea la cuenta.
  - Documento, correo o username repetidos: el sistema rechaza el registro.
  - El correo no coincide con el remitente, o un no Director indica un rol: el sistema rechaza el registro.
- **Postcondiciones:** usuario `ACTIVO` con un rol asignado.

### CU1.2 Administrar usuarios, roles y permisos

- **Objetivo:** mantener las cuentas y definir qué puede hacer cada rol.
- **Actor principal:** Director.
- **Actores secundarios:** Secretaria y Coordinador Académico (solo consultan usuarios).
- **Precondiciones:** el actor tiene el permiso correspondiente.
- **Flujo principal:**
  1. El Director consulta usuarios, roles o permisos.
  2. Registra, modifica o desactiva un usuario; asigna o quita un rol; o asigna o quita un permiso a un rol.
  3. El sistema aplica el cambio, que rige desde el siguiente comando.
- **Flujos alternativos:**
  - Sin permiso: acceso denegado.
  - Usuario, rol o permiso inexistente: se informa el error.
  - Dejaría al sistema sin Director activo o al Director sin gestión de roles: se rechaza.
  - El Director intenta desactivarse a sí mismo: se rechaza.
- **Postcondiciones:** cuentas y permisos actualizados. Un usuario desactivado pierde el acceso y conserva su historial.

### CU1.3 Consultar y actualizar datos personales

- **Objetivo:** mantener al día los datos propios.
- **Actor principal:** cualquier usuario, incluido el Profesor.
- **Actores secundarios:** ninguno.
- **Precondiciones:** cuenta activa.
- **Flujo principal:**
  1. El actor consulta sus datos.
  2. Envía los campos a cambiar; `-` conserva el valor actual.
  3. El sistema guarda y devuelve los datos actualizados. También puede cambiar su contraseña.
- **Flujos alternativos:**
  - Intenta consultar o cambiar datos ajenos sin permiso: acceso denegado.
  - Fecha inválida o clave actual incorrecta: se rechaza sin cambios.
- **Postcondiciones:** datos personales actualizados.

---

## CU2. Catálogo de Programas y Ofertas Académicas

### CU2.1 Consultar catálogo

- **Objetivo:** conocer los programas y las ofertas disponibles para elegir una.
- **Actor principal:** Estudiante o visitante.
- **Actores secundarios:** ninguno.
- **Precondiciones:** ninguna; no requiere estar registrado.
- **Flujo principal:**
  1. El actor solicita programas u ofertas.
  2. El sistema muestra nombre, descripción, modalidad, duración, cupos, tipo de cobro y costo.
  3. El actor consulta el detalle y elige la oferta en la que se preinscribirá.
- **Flujos alternativos:**
  - Id inexistente: se informa el error.
  - No hay ofertas abiertas: se indica que no hay registros.
- **Postcondiciones:** sin cambios en el sistema.

### CU2.2 Gestionar programas y ofertas

- **Objetivo:** definir lo que se muestra en el catálogo.
- **Actor principal:** Coordinador Académico.
- **Actores secundarios:** Director.
- **Precondiciones:** permiso de gestión del catálogo.
- **Flujo principal:**
  1. El Coordinador registra o actualiza un programa.
  2. Registra una malla con su versión y horas, que la oferta necesita.
  3. Registra la oferta con modalidad, fechas, cupos y gestión.
  4. Registra sus aranceles.
- **Flujos alternativos:**
  - Código repetido, fechas o cupos incoherentes: se rechaza.
  - Arancel mensual en una oferta sin fecha de fin: se rechaza.
  - Retirar un programa o cerrar una oferta: deja de mostrarse y conserva su historial.
- **Postcondiciones:** catálogo actualizado.

---

## CU3. Preinscripción y Ciclo de Vida del Estudiante

### CU3.1 Realizar preinscripción

- **Objetivo:** solicitar un cupo en una oferta.
- **Actor principal:** Estudiante.
- **Actores secundarios:** ninguno.
- **Precondiciones:** rol Estudiante; oferta abierta y con cupo; sin otra solicitud vigente en esa oferta.
- **Flujo principal:**
  1. El estudiante selecciona la oferta y envía su solicitud.
  2. El sistema la registra en estado `PENDIENTE`.
  3. El estudiante consulta su estado cuando lo necesite.
- **Flujos alternativos:**
  - Oferta cerrada, sin cupo o inexistente: se rechaza.
  - Ya existe una solicitud vigente: se rechaza.
  - El estudiante cancela: pasa a `CANCELADA`.
- **Postcondiciones:** solicitud `PENDIENTE` asociada al estudiante.

### CU3.2 Revisar solicitud

- **Objetivo:** validar los datos y habilitar el pago.
- **Actor principal:** Secretaria.
- **Actores secundarios:** Coordinador Académico; Estudiante, que recibe el resultado.
- **Precondiciones:** solicitud `PENDIENTE`; permiso de revisión.
- **Flujo principal:**
  1. La Secretaria consulta las solicitudes pendientes y los datos del estudiante.
  2. Aprueba la solicitud.
  3. El sistema reserva el cupo y crea la matrícula en `PENDIENTE_PAGO` con su número de registro y sus cuotas.
  4. El sistema envía al estudiante el costo, la modalidad y las cuotas.
- **Flujos alternativos:**
  - Datos incorrectos: la Secretaria rechaza con un motivo y el estudiante lo recibe por correo.
  - Oferta sin cupo, cerrada o sin aranceles: no se aprueba y la solicitud sigue `PENDIENTE`.
  - Solicitud ya procesada: se informa su estado.
- **Postcondiciones:** solicitud `APROBADA` con matrícula creada, o `RECHAZADA`.

### CU3.3 Formalizar matrícula

- **Objetivo:** confirmar la inscripción cuando el pago requerido está verificado.
- **Actor principal:** Sistema, a partir de la confirmación del pago.
- **Actores secundarios:** Secretaria y Estudiante.
- **Precondiciones:** matrícula en `PENDIENTE_PAGO`.
- **Flujo principal:**
  1. Se confirma un pago (CU4.3), o una beca deja el saldo requerido en Bs 0 (CU4.2).
  2. El sistema verifica que estén pagados los cobros únicos y la primera cuota de cada arancel mensual.
  3. El sistema cambia la matrícula a `CONFIRMADA`.
  4. El estudiante consulta su inscripción confirmada.
- **Flujos alternativos:**
  - Pago parcial o faltan cobros requeridos: la matrícula sigue en `PENDIENTE_PAGO`.
  - Cancelación sin pagos vigentes: matrícula `CANCELADA` y cupo liberado; el estudiante puede volver a solicitar.
  - Cancelación con pagos pendientes o confirmados: se rechaza.
- **Postcondiciones:** matrícula `CONFIRMADA` o `CANCELADA`.

---

## CU4. Aranceles, Becas y Conciliación de Pagos

### CU4.1 Consultar costos y plan de pagos

- **Objetivo:** conocer cuánto y cómo se paga.
- **Actor principal:** Estudiante.
- **Actores secundarios:** ninguno.
- **Precondiciones:** para el costo de una oferta, ninguna; para las cuotas, ser dueño de la matrícula o tener permiso.
- **Flujo principal:**
  1. El estudiante consulta los aranceles de la oferta, de pago único o mensual.
  2. Con la matrícula creada, consulta sus cuotas, vencimientos y saldos.
- **Flujos alternativos:**
  - Matrícula ajena sin permiso: acceso denegado.
  - Cuota vencida: se muestra como `VENCIDA`.
- **Postcondiciones:** sin cambios en el sistema.

### CU4.2 Aplicar beca

- **Objetivo:** descontar una beca del costo de la matrícula.
- **Actor principal:** Secretaria o Coordinador Académico.
- **Actores secundarios:** Estudiante, que recibe el aviso.
- **Precondiciones:** matrícula no cancelada, sin otra beca activa y sin pagos pendientes o confirmados.
- **Flujo principal:**
  1. El actor elige la matrícula y la beca, y puede ajustar el porcentaje.
  2. El sistema recalcula cuotas y totales.
  3. El sistema avisa al estudiante el nuevo monto.
- **Flujos alternativos:**
  - Ya tiene beca activa o tiene pagos vigentes: se rechaza.
  - Beca del 100 %: las cuotas quedan pagadas y la matrícula se confirma sin pago.
  - Retirar la beca: se restauran los montos.
- **Postcondiciones:** beca registrada y montos actualizados.

### CU4.3 Registrar y conciliar pago

- **Objetivo:** registrar un pago y verificarlo antes de darlo por válido.
- **Actor principal:** Estudiante (QR) o Secretaria (efectivo).
- **Actores secundarios:** tercero que paga; Pago Fácil, que avisa del cobro; Secretaria o Coordinador Académico que concilia; Director, que supervisa.
- **Precondiciones:** cuota no pagada de una matrícula no cancelada.
- **Flujo principal (QR de Pago Fácil):**
  1. El estudiante elige pagar por QR o solicita el QR de una cuota.
  2. El sistema registra el pago en `PENDIENTE` por el saldo, con los datos de quien pagará, y solicita el QR a Pago Fácil.
  3. El sistema envía el QR por correo.
  4. El estudiante o un tercero lo escanea y paga.
  5. Pago Fácil avisa al sistema, que confirma el pago.
  6. El sistema actualiza la cuota y la matrícula, y avisa al estudiante.
- **Flujo principal (efectivo o QR manual):**
  1. El estudiante o un tercero paga en efectivo autorizado o por el QR institucional.
  2. Se registran el monto, la referencia, el comprobante y los datos de quien pagó.
  3. El sistema deja el pago en `PENDIENTE`.
  4. El personal autorizado verifica y confirma el pago.
  5. El sistema actualiza la cuota y la matrícula, y avisa al estudiante.
- **Flujos alternativos:**
  - Pago Fácil no responde: se informa el error y no queda saldo reservado.
  - El aviso de Pago Fácil no llega: el personal autorizado confirma el pago manualmente.
  - El QR no se paga: el personal autorizado rechaza el pago y se libera el saldo.
  - Se pide de nuevo el QR de un pago pendiente: se genera otro para ese mismo pago, sin duplicarlo.
  - Un aviso repetido o con un pedido desconocido: se ignora sin cambios.
  - Monto mayor al saldo o referencia repetida: no se registra.
  - El estudiante intenta registrar efectivo: se indica que lo hace la Secretaría.
  - Comprobante inválido: el pago se rechaza con un motivo y el estudiante lo recibe; puede registrarlo de nuevo.
  - Pago parcial: la cuota sigue pendiente hasta completar su monto.
  - Pago ya conciliado: se informa su estado.
- **Postcondiciones:** pago `CONFIRMADO` o `RECHAZADO`, con cuota y matrícula actualizadas según corresponda.

### CU4.4 Consultar historial de pagos

- **Objetivo:** revisar los pagos y su estado.
- **Actor principal:** Estudiante.
- **Actores secundarios:** Secretaria, Coordinador Académico y Director, que ven todos los pagos.
- **Precondiciones:** cuenta activa.
- **Flujo principal:**
  1. El actor solicita los pagos, con un filtro por estado si lo desea.
  2. El sistema muestra monto, método, referencia, pagador y estado.
- **Flujos alternativos:**
  - Sin pagos: se indica que no hay registros.
  - Filtro inválido: se indican los estados posibles.
- **Postcondiciones:** sin cambios en el sistema.

---

## Quién hace qué

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
