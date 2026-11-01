# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Proyecto

Sistema de gestión académica ICAP-UAGRM operado por email: el asunto de cada correo es un comando `AAAEEE["p1","p2",...]`, que se lee por POP3, se ejecuta sobre PostgreSQL y se responde con un email HTML. Este parcial cubre CU1 Usuarios, CU2 Catálogo y Ofertas, CU3 Preinscripción y Matrícula, y CU4 Aranceles, Becas y Pagos. El resto de tablas del script (notas, asistencia, certificados, etc.) es de parciales posteriores.

Documentación: `CASOS_DE_USO.md` (reglas de negocio de cada CU) y `GUIA_DE_PRUEBAS.md` (guion de la demostración, comando por comando). `README.md` recoge las decisiones de diseño.

## Comandos

Requiere Java 17, Maven y PostgreSQL. Ejecutar desde la raíz del proyecto: las pruebas leen los scripts SQL con rutas relativas.

```bash
mvn compile exec:java                      # inicia el sistema; Pago Fácil se configura en .env
mvn test                                   # todas las pruebas
mvn test -Dtest=FlujoIcapTest              # una clase
mvn test -Dtest=FlujoIcapTest#nombreMetodo # una prueba
```

Las pruebas descargan un PostgreSQL embebido (zonky) y no usan la base ni el correo reales. No hay linter configurado.

Base de datos nueva: ejecutar `database_script.sql` y luego `datos_iniciales.sql`, en una base exclusiva para ICAP (el script crea tablas como `usuario` y `pago` en `public` y falla si ya existen). La contraseña inicial de los usuarios semilla es `Icap2026*`.

## Arquitectura

```
EmailReceipt (POP3) → EmailApp.onReceiptEmail → Interpreter → ICasoUsoListener (EmailApp)
                                                                     ↓
                     EmailSend (SMTP) ← respuesta HTML ← Negocio (N*) → Datos (D*) → PostgreSQL
```

- `EmailApp` es el controlador único: implementa ambos listeners, filtra los comandos de correos no registrados o inactivos y despacha a las clases `N*`.
- Cada tabla usada tiene su par `negocio.N*` / `data.D*`.
- Los correos se procesan de uno en uno, para que se atiendan en orden de llegada. Las respuestas sí se envían en hilos aparte.

### Convenciones que cruzan varias capas

- **Errores:** todo caso de uso corre dentro de `EmailApp.atender()`. Los mensajes de `IllegalArgumentException` e `IllegalStateException` llegan tal cual al usuario, así que se usan para errores de negocio y deben estar redactados para él. Cualquier otra excepción se registra en el log y el usuario recibe un mensaje genérico.
- **Permisos:** se aplican en la capa de negocio con `dUsuario.exigirPermiso(sender, DPermiso.X)` y se leen de la base (`USUARIO_ROL` → `ROL_PERMISO` → `PERMISO`). La tabla `COMANDOS` de `EmailApp` solo alimenta `HELP`: su columna de permiso no protege nada.
- **Comandos públicos:** `COMANDOS_PUBLICOS` compara por prefijo y deja pasar sin registro comandos completos como `INSUSU` o `LISOFE`. Las operaciones de esas entidades que requieren permiso deben exigirlo en su clase `N*`.
- **Datos:** los DAO devuelven filas como `String[]` y el negocio las lee por posición (por ejemplo, `oferta.get(0)[9]`). Al cambiar las columnas de un `SELECT`, revisar esos índices y los `HEADERS` del DAO.
- **SQL:** los DAO extienden `DBase`, que abre una conexión nueva por operación y usa `PreparedStatement`. Las operaciones sobre varias tablas van dentro de `enTransaccion`.
- **Validación:** los parámetros se validan con `negocio.Validar`. En `MOD`, `-` conserva el valor actual.
- **Bajas:** usuarios, programas, ofertas y aranceles se desactivan, no se borran.
- **Cobros:** toda obligación es una fila de `MENSUALIDAD`; un arancel `UNICO` genera una sola cuota y uno `MENSUAL`, una por cada mes de la oferta.
- **Contraseñas:** se guardan solo en `password_hash` (BCrypt); la columna `password` queda en NULL. Los parámetros no se registran en el log y las respuestas no repiten el asunto, porque pueden contener contraseñas.
- **Identidad:** cada persona se identifica solo por el correo remitente; no hay inicio de sesión.
- **Configuración:** los datos de la base y del buzón están en `DBConnection`, `EmailReceipt` y `EmailSend`, como en la referencia; Gmail está en `run.java` y `.env` guarda Pago Fácil. Las pruebas asignan directamente esos campos estáticos.

### Agregar un comando

1. Acción o entidad nueva: añadir el código en `Interpreter` (`mapAction` / `mapEntity`, `dispatchCasoUso`) y en `Token`.
2. Entidad nueva: añadir el método en `ICasoUsoListener` e implementarlo en `EmailApp`, dentro de `atender()`.
3. Implementar la lógica en `N*`, incluida la comprobación de permiso, y el SQL en `D*`.
4. Añadir la fila a `COMANDOS` para que aparezca en `HELP`.
5. Si es un permiso nuevo, añadir la constante en `DPermiso` y sus filas en `datos_iniciales.sql`.

## Pruebas

- `FlujoIcapTest`: recorre los flujos llamando a `app.onReceiptEmail(...)` contra los scripts SQL reales. Sobrescribe `EmailApp.sendEmail` para capturar las respuestas, y es el patrón a seguir para probar casos de uso nuevos. Si cambian los scripts SQL, estas pruebas los ejecutan directamente.
- `TransporteCorreoTest`: prueba el diálogo POP3 y SMTP contra sockets locales.

## Otros

No se incluye el programador de cuotas ni el cálculo de intereses. Los pagos por QR usan Pago Fácil y su webhook; tampoco se leen los adjuntos del correo.
