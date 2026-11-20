# Flujo de pagos — ICAP-UAGRM

Cada comando se escribe en el **asunto** de un correo enviado al buzón del sistema. Los ids (`1`, `5`, …) son
de ejemplo: toma siempre los que vienen en cada respuesta.

La integración con Pago Fácil es la misma que la de la referencia (`sa-proyecto-via-email-grupo19sa`):
mismo `PagoFacilService` (login + `generate-qr` en MasterQR v2), mismo `WebHookServer` en el puerto 8080 y
mismo correo con el QR incrustado (`cid:qr_image`). La diferencia es el dominio: aquí no se paga una cita,
se paga una **cuota (MENSUALIDAD) de una matrícula**, y las cuotas ya existen desde que se aprueba la
preinscripción, así que no hay que elegir "Contado/Crédito".

| Referencia (clínica) | ICAP |
| --- | --- |
| `MODCIT[..., "finalizado"]` crea el pago | `APRPRE` crea la matrícula y sus cuotas |
| Email "Selección de forma de pago" | `PEDPAG["id_matricula"]` (Secretaría) |
| `RESPAG["QR","Contado"]` | `RESPAG["id_matricula","QR"]` |
| `RESPAG["Fisico","Contado"]` | `RESPAG["id_matricula","FISICO"]` |
| `PAGCUO["id_cuota"]` | `PAGCUO["id_cuota"]` (igual) |
| `LISCUO[]` | `LISCUO["id_matricula"]` |
| `MODCUO[..., "Pagado"]` (pago físico) | `INSPAG` + `CONPAG` (Secretaría) |
| Webhook `CUOTA-{cuota}-{pago}` | Webhook `CUOTA-{cuota}-{pago}-{referencia}-{marca}` |
| Pago `Completado` | Pago `CONFIRMADO`, cuota `PAGADA`, matrícula `CONFIRMADA` |

---

## 0. Antes de empezar (una sola vez)

1. **Base de datos** con `database_script.sql` y `datos_iniciales.sql` cargados (correos reales en los
   usuarios semilla). Para probar con 1 Bs en la base local: `crear_oferta_prueba_1bs.sql`.
   `DBConnection.USE_LOCAL` elige base local (`true`) o la del ing (`false`).
2. **`.env`** en la raíz del proyecto:
   ```
   PAGOFACIL_TOKEN_SERVICE=...
   PAGOFACIL_TOKEN_SECRET=...
   PAGOFACIL_CALLBACK_URL=https://<tu-subdominio>.ngrok-free.app/webhook/pagofacil
   ```
3. **Comprobar las credenciales** (debe responder `200` con un `accessToken`):
   ```bash
   curl -X POST https://masterqr.pagofacil.com.bo/api/services/v2/login \
        -H "Content-Type: application/json" \
        -H "tcTokenService: <TOKEN_SERVICE>" -H "tcTokenSecret: <TOKEN_SECRET>" -d "{}"
   ```
   Si responde `403 "The company is not enabled for this service."`, el código está bien pero **esa
   cuenta de Pago Fácil no está habilitada**: hay que usar credenciales de un comercio habilitado (por
   ejemplo, las que usa la referencia) o pedir la habilitación a Pago Fácil.
4. **Exponer el webhook**: el sistema escucha en `http://localhost:8080/webhook/pagofacil`.
   ```bash
   ngrok http 8080
   ```
   Copia la URL `https://...ngrok-free.app` en `PAGOFACIL_CALLBACK_URL` (terminada en `/webhook/pagofacil`).
   Si ngrok cambia de URL, actualiza el `.env` y reinicia el sistema.
5. **Arrancar**: `mvn compile exec:java`. En consola debe aparecer
   `=== WebHookServer iniciado en puerto 8080 ===`.

---

## 1. Llegar a una cuota por pagar

| Paso | Remitente | Asunto | Resultado |
| --- | --- | --- | --- |
| 1 | Estudiante | `LISOFE[]` | Ofertas abiertas con su costo |
| 2 | Estudiante | `INSUSU["Eva","Paz","7654321","CI","<su correo>","eva","ClaveSegura1","70000000","Av. Busch","2000-01-15"]` | Cuenta de estudiante (solo la primera vez) |
| 3 | Estudiante | `INSPRE["<id_oferta>","Deseo inscribirme"]` | Preinscripción `PENDIENTE` |
| 4 | Secretaria | `LISPRE["PENDIENTE"]` | Ve la solicitud |
| 5 | Secretaria | `APRPRE["<id_preinscripcion>","Datos correctos"]` | Matrícula `PENDIENTE_PAGO` y sus cuotas; el estudiante recibe el detalle |
| 6 | Estudiante | `LISCUO["<id_matricula>"]` | Ve sus cuotas: id, monto, saldo y vencimiento |

Un arancel `UNICO` genera una cuota; uno `MENSUAL`, una por cada mes de la oferta.

---

## 2. Elegir la forma de pago (opcional, como en la referencia)

| Remitente | Asunto | Resultado |
| --- | --- | --- |
| Secretaria | `PEDPAG["<id_matricula>"]` | El estudiante recibe "Elige tu forma de pago" con la cuota pendiente |
| Estudiante | `RESPAG["<id_matricula>","QR"]` | Se genera el QR de la **siguiente cuota pendiente** → ir a la sección 3 |
| Estudiante | `RESPAG["<id_matricula>","FISICO"]` | Responde "Pasa por Secretaría a pagar Bs X…" → ir a la sección 4 |

---

## 3. Pago con QR (Pago Fácil)

### 3.1 Pedir el QR

Cualquiera de estos (el resultado es el mismo):

| Asunto | Para qué |
| --- | --- |
| `RESPAG["<id_matricula>","QR"]` | QR de la siguiente cuota pendiente |
| `PAGCUO["<id_cuota>"]` | QR de una cuota concreta |
| `PAGCUO["<id_cuota>","Luis","Paz","CI","555123","71111111","luis@correo.com","Padre"]` | Lo paga un tercero; él también recibe el QR |
| `PAGARQR["<id_pago>"]` | Volver a generar el QR de un pago QR que sigue `PENDIENTE` (por ejemplo, si expiró) |

**Qué hace el sistema:**
1. Verifica que la cuota es del remitente (o que es personal con permiso).
2. Crea un `PAGO` en estado `PENDIENTE` por el saldo de la cuota, con método "QR Pago Facil" y una
   referencia aleatoria. Si ya había uno pendiente para esa cuota, lo reutiliza.
3. Llama a Pago Fácil (`/login` y `/generate-qr`) con
   `paymentNumber = CUOTA-{id_cuota}-{id_pago}-{referencia}-{marca}` y `callbackUrl` del `.env`.
4. Envía el correo **"Código QR para tu pago"** con la imagen del QR, el id de pago, concepto, monto,
   transacción y fecha de expiración.

Si Pago Fácil falla, el pago se rechaza solo (libera el saldo) y el usuario recibe:
"No se pudo generar el QR con Pago Fácil…". El motivo exacto queda en la consola
(`Error generando QR de Pago Fácil: ...`).

### 3.2 Pagar

El estudiante (o el tercero) escanea el QR con su app bancaria y paga.

### 3.3 Confirmación automática (webhook)

Pago Fácil envía `POST /webhook/pagofacil` con `{"PedidoID":"CUOTA-…","Estado":2}`. El sistema:

1. Valida que la cuota, el pago y la referencia del `PedidoID` coincidan con la base (un aviso falso se ignora).
2. Marca el pago `CONFIRMADO`; si cubre el monto, la cuota pasa a `PAGADA`.
3. Recalcula la matrícula (`CONFIRMADA` cuando se paga lo requerido).
4. Envía el correo:
   - **"✅ Cuota #N Pagada"** si aún quedan cuotas pendientes, o
   - **"✅ Pago #X Confirmado"** si era la última.

Los avisos repetidos se ignoran (`already_processed`) y `Estado` distinto de `2` no cambia nada.

### 3.4 Verificar

| Remitente | Asunto | Esperado |
| --- | --- | --- |
| Estudiante | `LISPAG[]` | El pago en `CONFIRMADO` |
| Estudiante | `LISCUO["<id_matricula>"]` | La cuota en `PAGADA` |
| Estudiante | `LISMAT[]` | Matrícula `CONFIRMADA` |

### 3.5 Si algo no llega

| Situación | Qué hacer |
| --- | --- |
| Se pagó pero no llegó el webhook (ngrok caído, URL vieja) | Secretaria: `LISPAG["PENDIENTE"]` y `CONPAG["<id_pago>"]` |
| El QR no se va a pagar | Secretaria: `RECPAG["<id_pago>","QR vencido"]` para liberar el saldo |
| El QR expiró y se quiere pagar igual | Estudiante: `PAGARQR["<id_pago>"]` |
| Se pagó un QR ya rechazado | No se acredita solo; la consola lo avisa y hay que revisarlo a mano |

### 3.6 Modo simulación (sin Pago Fácil real)

Sirve para probar todo el flujo cuando las credenciales de Pago Fácil no funcionan. En el `.env`:

```
PAGOFACIL_SIMULADO=true
```

Reinicia con `mvn compile exec:java`. En la consola aparece
`PAGO FÁCIL SIMULADO: confirma un pago abriendo http://localhost:8080/webhook/simular?pago={id_pago}`.

1. El estudiante pide su QR igual que siempre (`RESPAG[...,"QR"]` o `PAGCUO[...]`). No se llama a Pago Fácil:
   llega el correo **"Código QR para tu pago (SIMULADO)"** con un QR de prueba (no se puede escanear) y una
   fila "Simulación" con el enlace exacto. El enlace también sale en la consola.
2. Para "pagar", abre en el navegador de la PC donde corre el sistema
   `http://localhost:8080/webhook/simular?pago=<id_pago>`. El sistema procesa el mismo aviso que enviaría
   Pago Fácil (`CUOTA-...`, `Estado 2`): pago `CONFIRMADO`, cuota `PAGADA`, matrícula `CONFIRMADA` y correo
   "✅ Pago #X Confirmado".
3. Verifica con `LISPAG[]` y `LISMAT[]`.

No necesita ngrok ni credenciales. Para la demostración con Pago Fácil real, vuelve a poner
`PAGOFACIL_SIMULADO=false`.

---

## 4. Pago físico (efectivo en Secretaría)

| Paso | Remitente | Asunto | Resultado |
| --- | --- | --- | --- |
| 1 | Estudiante | `RESPAG["<id_matricula>","FISICO"]` | Le indica el monto y que pase por Secretaría |
| 2 | Secretaria | `LISMET[]` | Ve el id del método `EFECTIVO` (con los datos iniciales: `QR`=1, `EFECTIVO`=2, `QR Pago Facil`=3) |
| 3 | Secretaria | `INSPAG["<id_cuota>","<id_efectivo>","<monto>","REC-0001"]` | Pago `PENDIENTE` (la referencia es el nro. de recibo) |
| 4 | Secretaria | `CONPAG["<id_pago>"]` | Pago `CONFIRMADO`; el estudiante recibe el aviso |

El estudiante no puede registrar efectivo por su cuenta. Sí puede registrar un **QR manual** (transferencia)
con `INSPAG["<id_cuota>","<id_metodo_qr_manual>","<monto>","<nro_transaccion>","<url_comprobante>"]`, que
luego la Secretaría confirma (`CONPAG`) o rechaza (`RECPAG`).

---

## 5. Estados

| Entidad | Estados |
| --- | --- |
| Pago | `PENDIENTE` → `CONFIRMADO` o `RECHAZADO` |
| Cuota (MENSUALIDAD) | `PENDIENTE` → `PAGADA` (se muestra `VENCIDA` si pasó la fecha) |
| Matrícula | `PENDIENTE_PAGO` → `CONFIRMADA` (o `CANCELADA`) |

## 6. Diagrama

```
APRPRE ──► Matrícula PENDIENTE_PAGO + cuotas
              │
   (opcional) PEDPAG ──► correo "Elige tu forma de pago"
              │
     ┌────────┴──────────────────────────┐
RESPAG[..,"QR"] / PAGCUO            RESPAG[..,"FISICO"]
     │                                    │
 PAGO PENDIENTE + QR por correo      "Pasa por Secretaría"
     │                                    │
 Estudiante paga en su banco         Secretaria INSPAG (efectivo)
     │                                    │
 Webhook Pago Fácil (Estado 2)       Secretaria CONPAG
     │                                    │
     └──────────► PAGO CONFIRMADO ◄───────┘
                    cuota PAGADA
              ¿quedan cuotas pendientes?
               sí: "Cuota #N Pagada"
               no: "Pago #X Confirmado", matrícula CONFIRMADA
```

## 7. Comandos de pago

| Comando | Quién | Descripción |
| --- | --- | --- |
| `LISCUO["id_matricula"]` | Estudiante / personal | Cuotas, saldos y vencimientos |
| `LISMET[]` | Todos | Métodos de pago |
| `PEDPAG["id_matricula"]` | Secretaría | Pide al estudiante elegir forma de pago |
| `RESPAG["id_matricula","QR\|FISICO"]` | Estudiante | Elige forma de pago de la siguiente cuota |
| `PAGCUO["id_cuota", pagador...]` | Estudiante | QR de Pago Fácil de una cuota |
| `PAGARQR["id_pago"]` | Estudiante | Regenera el QR de un pago pendiente |
| `INSPAG["id_cuota","id_metodo","monto","referencia","url", pagador...]` | Estudiante (QR manual) / Secretaría (efectivo) | Registra un pago a verificar |
| `LISPAG[]`, `LISPAG["PENDIENTE\|CONFIRMADO\|RECHAZADO"]` | Todos | Historial de pagos |
| `CONPAG["id_pago"]` | Secretaría | Confirma un pago |
| `RECPAG["id_pago","motivo"]` | Secretaría | Rechaza un pago |
