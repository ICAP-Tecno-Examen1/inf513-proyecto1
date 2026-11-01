# Guía de pruebas: demostración del flujo principal

Cada comando se escribe en el **asunto** de un correo enviado al buzón del sistema, desde la cuenta
indicada. Los ejemplos usan los datos de `datos_iniciales.sql`; los ids pueden variar, así que toma
los de cada respuesta.

## Flujo principal

| Paso | Remitente | Asunto | Resultado |
| --- | --- | --- | --- |
| 1 | Estudiante | `LISOFE[]` | Ofertas con modalidad, duración, cupos y costo |
| 2 | Estudiante | `LISPRO["1"]` | Programa elegido y sus ofertas |
| 3 | Estudiante | `INSUSU["Eva","Paz","7654321","CI","eva@correo.com","eva","ClaveSegura1","70000000","Av. Busch","2000-01-15"]` | Cuenta creada con rol Estudiante |
| 4 | Estudiante | `INSPRE["1","Deseo inscribirme"]` | Solicitud `PENDIENTE` |
| 5 | Secretaria | `LISPRE["PENDIENTE"]` | Solicitudes por revisar |
| 5-6 | Secretaria | `APRPRE["1","Datos correctos"]` | Matrícula `PENDIENTE_PAGO`; el estudiante recibe costo y cuotas |
| 7 | Secretaria | `LISBEC[]` y luego `INSBES["1","1"]` | Beca de 30 %: de Bs 3000 a Bs 2100 |
| 8 | Estudiante | `LISCUO["1"]` y `LISMET[]` | Cuota, saldo y métodos de pago |
| 8 | Estudiante | `INSPAG["1","1","2100","QR-0001","https://ejemplo.com/c.jpg","Luis","Paz","CI","555123","71111111","luis@correo.com","Padre"]` | Pago de un tercero, `PENDIENTE` |
| 9 | Secretaria | `LISPAG["PENDIENTE"]` | Pagos por verificar |
| 10 | Secretaria | `CONPAG["1"]` | Pago `CONFIRMADO`; el estudiante recibe un aviso |
| 11-12 | Estudiante | `LISMAT[]` y `LISPAG[]` | Matrícula `CONFIRMADA` e historial de pagos |

## Pago con Pago Fácil

Sustituye a los pasos 8 a 10. Requiere el `.env` de Pago Fácil y el webhook accesible desde internet.

| Paso | Remitente | Asunto | Resultado |
| --- | --- | --- | --- |
| Opcional | Secretaria | `PEDPAG["1"]` | El estudiante recibe la cuota pendiente y las opciones de pago |
| 8 | Estudiante | `RESPAG["1","QR"]` o `PAGCUO["1"]` | Pago `PENDIENTE`; llega un correo con el QR |
| 8 | Estudiante | `PAGCUO["1","Luis","Paz","CI","555123","71111111","luis@correo.com","Padre"]` | Igual, con los datos del tercero, quien también recibe el QR |
| 8 | Estudiante | `PAGARQR["1"]` | Reenvía un QR nuevo del mismo pago |
| 9-10 | Pago Fácil | Escanear el QR y pagar | El webhook confirma el pago; la cuota queda `PAGADA` y el estudiante recibe un aviso |
| 11-12 | Estudiante | `LISMAT[]` y `LISPAG[]` | Matrícula `CONFIRMADA` e historial de pagos |

Si el aviso de Pago Fácil no llega, la Secretaria puede confirmar con `CONPAG["1"]`. Si el QR no se va a
pagar, debe rechazarlo con `RECPAG["1","QR vencido"]` para liberar el saldo.

## Flujos alternativos

| Caso | Remitente | Asunto | Resultado |
| --- | --- | --- | --- |
| Solicitud rechazada | Secretaria | `RECPRE["2","Falta título"]` | Aviso con el motivo al estudiante |
| Solicitud cancelada | Estudiante | `CANPRE["2"]` | `CANCELADA`; libera el cupo si no hay pagos vigentes |
| Pago rechazado | Secretaria | `RECPAG["2","Comprobante ilegible"]` | La cuota sigue pendiente y se puede volver a pagar |
| Pago mayor al saldo | Estudiante | `INSPAG["1","1","9999","QR-X"]` | Rechazado, indica el saldo disponible |
| Efectivo | Estudiante | `INSPAG["1","2","100","REC-1"]` | Rechazado: lo registra la Secretaría |
| Efectivo autorizado | Secretaria | `INSPAG["1","2","100","REC-1"]` | Queda `PENDIENTE` hasta `CONPAG` |
| Sin permiso | Estudiante | `LISUSU[]` | Acceso denegado |
| Sin registro | Correo no registrado | `INSPRE["1"]` | Indica que debe registrarse |

## Mensualidades

Con la oferta 2 (Maestría, Bs 1000 por mes durante 12 meses): `INSPRE["2"]`, y tras `APRPRE` se generan
12 cuotas. La matrícula se confirma al confirmarse el pago de la primera.

## Administración

| Remitente | Asunto | Resultado |
| --- | --- | --- |
| Director | `INSUSU["Sol","Vaca","777","CI","sol@correo.com","sol","ClaveSegura1","Coordinador"]` | Usuario con rol asignado |
| Director | `LISUSU[]`, `DELUSU["777"]` | Administración de usuarios |
| Director | `LISROL[]`, `INSROL["777","Secretaria"]`, `DELROL["777","Secretaria"]` | Asignación de roles |
| Director | `LISPER[]`, `INSPER["Profesor","USUARIO_VER"]`, `DELPER["Profesor","USUARIO_VER"]` | Asignación de permisos |
| Cualquiera | `LISUSU["-"]`, `MODUSU["7654321","-","-","71234567"]` | Consulta y actualización de datos propios |
| Coordinador | `INSPRO["DIP-X","Diplomado X","Descripción","DIPLOMADO"]` | Programa registrado |
| Coordinador | `INSMAL["3","V1","240","2027-01-01"]` | Malla del programa |
| Coordinador | `INSOFE["DIP-X-1","3","VIRTUAL","2027-03-01","2027-06-30","30","5","2027"]` | Oferta registrada |
| Coordinador | `INSARA["3","Colegiatura","3000","UNICO"]` | Costo de la oferta |
