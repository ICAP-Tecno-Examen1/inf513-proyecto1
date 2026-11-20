import static org.junit.jupiter.api.Assertions.*;

import com.mycompany.parcial1.tecnoweb.EmailApp;
import postgresConecction.PagoFacilService;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.zonky.test.db.postgres.embedded.EmbeddedPostgres;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;
import org.json.JSONObject;
import postgresConecction.WebHookServer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import librerias.Email;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import postgresConecction.DBConnection;

/**
 * Recorre el flujo del parcial a través de la misma entrada que usa el correo,
 * contra un PostgreSQL temporal con los scripts reales del proyecto.
 */
class FlujoIcapTest {

    private static final String DIRECTOR = "director@ejemplo.com";
    private static final String SECRETARIA = "secretaria@ejemplo.com";
    private static final String COORDINADOR = "coordinador@ejemplo.com";
    private static final String PROFESOR = "profesor@ejemplo.com";
    private static final long DIPLOMADO = 1;
    private static final long MAESTRIA = 2;

    private static EmbeddedPostgres pg;
    private static final List<Email> enviados = new ArrayList<>();
    private static int contador = 0;

    private final EmailApp app = new EmailApp() {
        @Override
        protected void sendEmail(Email email) {
            enviados.add(email);
        }

        @Override
        protected void sendEmailQr(String to, String subject, String html, byte[] imagen) {
            enviados.add(new Email(to, subject, html));
            qrEnviado = imagen;
        }
    };

    private static byte[] qrEnviado;
    private static HttpServer pagoFacil;
    private static WebHookServer webhook;
    private static final List<JSONObject> pedidosQr = new ArrayList<>();
    private static final List<Email> avisosWebhook = new ArrayList<>();
    private static int loginsPagoFacil = 0;
    private static boolean tokenVencido = false;
    private static boolean pagoFacilCaido = false;

    private static void responder(HttpExchange ex, int status, String body) throws java.io.IOException {
        byte[] bytes = body.getBytes();
        ex.sendResponseHeaders(status, bytes.length);
        ex.getResponseBody().write(bytes);
        ex.close();
    }

    /** Servidor mínimo con la forma de respuesta que espera el proyecto de referencia. */
    private static void iniciarPagoFacilSimulado() throws Exception {
        pagoFacil = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
        pagoFacil.createContext("/login", ex -> {
            loginsPagoFacil++;
            responder(ex, 200, "{\"values\":{\"accessToken\":\"token-" + loginsPagoFacil + "\"}}");
        });
        pagoFacil.createContext("/generate-qr", ex -> {
            String body = new String(ex.getRequestBody().readAllBytes());
            if (pagoFacilCaido) {
                responder(ex, 500, "{}");
            } else if (tokenVencido) {
                tokenVencido = false;
                responder(ex, 401, "{}");
            } else {
                pedidosQr.add(new JSONObject(body));
                responder(ex, 200, "{\"values\":{\"qrBase64\":\"" + Base64.getEncoder().encodeToString(new byte[] { 1, 2, 3 })
                        + "\",\"transactionId\":777,\"expirationDate\":\"2027-01-01\"}}");
            }
        });
        pagoFacil.start();
        PagoFacilService.BASE_URL = "http://localhost:" + pagoFacil.getAddress().getPort();
        PagoFacilService.TC_TOKEN_SERVICE = "servicio";
        PagoFacilService.TC_TOKEN_SECRET = "secreto";
        PagoFacilService.CALLBACK_URL = "https://icap.test/webhook/pagofacil";

        WebHookServer.PORT = 0;
        webhook = new WebHookServer() {
            @Override
            protected void enviarHtml(String to, String subject, String html) {
                avisosWebhook.add(new Email(to, subject, html));
            }
        };
        webhook.start();
    }

    private int callback(String cuerpo) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + webhook.getPort() + "/webhook/pagofacil"))
                .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    private String ultimoPedido() {
        return pedidosQr.get(pedidosQr.size() - 1).getString("paymentNumber");
    }

    @Test
    void pagoFacilConfirmaLaMatriculaPorWebhook() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, DIPLOMADO);
        long c = cuota(mat, 1);

        // Genera el QR por el saldo, a nombre del estudiante, y lo envía por correo
        List<Email> respuesta = enviar(est, "PAGCUO[\"" + c + "\"]");
        assertEquals(1, respuesta.size(), texto(respuesta));
        assertArrayEquals(new byte[] { 1, 2, 3 }, qrEnviado);
        assertTrue(respuesta.get(0).getMessage().contains("cid:qr_image"));
        JSONObject pedido = pedidosQr.get(pedidosQr.size() - 1);
        assertEquals(0, new java.math.BigDecimal("3000").compareTo(pedido.getBigDecimal("amount")));
        assertEquals(est, pedido.getString("email"));
        assertEquals("https://icap.test/webhook/pagofacil", pedido.getString("callbackUrl"));
        assertEquals("PENDIENTE", valor("SELECT estado_conciliacion FROM pago WHERE id_mensualidad = ?", c));
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));

        // Pedir el QR otra vez no crea otro pago
        String pedido1 = ultimoPedido();
        enviar(est, "PAGCUO[\"" + c + "\"]");
        assertEquals("1", valor("SELECT count(*) FROM pago WHERE id_mensualidad = ?", c));
        String idPago = valor("SELECT id_pago FROM pago WHERE id_mensualidad = ?", c);
        enviar(est, "PAGARQR[\"" + idPago + "\"]");
        assertEquals("1", valor("SELECT count(*) FROM pago WHERE id_mensualidad = ?", c));

        // Callbacks que no deben confirmar: otro estado, pedido inventado, JSON inválido
        assertEquals(200, callback("{\"PedidoID\":\"" + pedido1 + "\",\"Estado\":1}"));
        assertEquals(200, callback("{\"PedidoID\":\"CUOTA-" + c + "-" + idPago + "-000000000000-x\",\"Estado\":2}"));
        assertEquals(200, callback("{\"PedidoID\":\"OTRO-1\",\"Estado\":2}"));
        assertEquals(400, callback("no es json"));
        assertEquals("PENDIENTE", valor("SELECT estado_conciliacion FROM pago WHERE id_pago = ?", Long.parseLong(idPago)));

        // El callback real confirma; también vale el de un QR anterior del mismo pago
        avisosWebhook.clear();
        assertEquals(200, callback("{\"PedidoID\":\"" + pedido1 + "\",\"Estado\":2}"));
        assertEquals("CONFIRMADO", valor("SELECT estado_conciliacion FROM pago WHERE id_pago = ?", Long.parseLong(idPago)));
        assertEquals("PAGADA", valor("SELECT estado FROM mensualidad WHERE id_mensualidad = ?", c));
        assertEquals("CONFIRMADA", estadoMatricula(mat));
        assertEquals(1, avisosWebhook.size());
        assertEquals(est, avisosWebhook.get(0).getTo());
        assertTrue(avisosWebhook.get(0).getSubject().contains("Confirmado"));
        assertTrue(avisosWebhook.get(0).getMessage().contains("Pago Confirmado"));
        assertTrue(avisosWebhook.get(0).getMessage().contains("CONFIRMADA"));

        // Un callback repetido no vuelve a procesar ni a avisar
        assertEquals(200, callback("{\"PedidoID\":\"" + pedido1 + "\",\"Estado\":2}"));
        assertEquals(1, avisosWebhook.size());
    }

    @Test
    void pagoFacilSimuladoGeneraQrYConfirmaSinLlamarALaApi() throws Exception {
        boolean simuladoAntes = PagoFacilService.SIMULADO;
        PagoFacilService.SIMULADO = true;
        WebHookServer simulado = new WebHookServer() {
            @Override
            protected void enviarHtml(String to, String subject, String html) {
                avisosWebhook.add(new Email(to, subject, html));
            }
        };
        simulado.start();
        try {
            String est = nuevoEstudiante();
            long mat = matricular(est, DIPLOMADO);
            long c = cuota(mat, 1);
            int pedidosAntes = pedidosQr.size();

            List<Email> respuesta = enviar(est, "PAGCUO[\"" + c + "\"]");
            assertEquals(pedidosAntes, pedidosQr.size(), "no debe llamar a Pago Fácil");
            assertEquals((byte) 0x89, qrEnviado[0]);
            assertEquals('P', qrEnviado[1]);
            assertTrue(respuesta.get(0).getSubject().contains("SIMULADO"), respuesta.get(0).getSubject());
            assertTrue(respuesta.get(0).getMessage().contains("/webhook/simular?pago="));
            long idPago = Long.parseLong(valor("SELECT id_pago FROM pago WHERE id_mensualidad = ?", c));

            avisosWebhook.clear();
            assertEquals(400, simular(simulado, "abc"));
            assertEquals(404, simular(simulado, "999999"));
            assertEquals(200, simular(simulado, String.valueOf(idPago)));
            assertEquals("CONFIRMADO", valor("SELECT estado_conciliacion FROM pago WHERE id_pago = ?", idPago));
            assertEquals("CONFIRMADA", estadoMatricula(mat));
            assertEquals(1, avisosWebhook.size());
            assertTrue(avisosWebhook.get(0).getSubject().contains("Confirmado"));
            assertEquals(409, simular(simulado, String.valueOf(idPago)));
        } finally {
            PagoFacilService.SIMULADO = simuladoAntes;
            simulado.stop();
        }
    }

    private int simular(WebHookServer servidor, String idPago) throws Exception {
        return HttpClient.newHttpClient().send(HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + servidor.getPort() + "/webhook/simular?pago=" + idPago)).GET().build(),
                HttpResponse.BodyHandlers.ofString()).statusCode();
    }

    @Test
    void webhookAvisaQueFaltanCuotasEnMensualidades() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, MAESTRIA);
        enviar(est, "PAGCUO[\"" + cuota(mat, 1) + "\"]");
        avisosWebhook.clear();
        assertEquals(200, callback("{\"PedidoID\":\"" + ultimoPedido() + "\",\"Estado\":2}"));
        assertEquals("CONFIRMADA", estadoMatricula(mat));
        assertEquals(1, avisosWebhook.size());
        assertTrue(avisosWebhook.get(0).getSubject().contains("Cuota #1 Pagada"));
        assertTrue(avisosWebhook.get(0).getMessage().contains("Aún faltan cuotas"));
    }

    @Test
    void pagoFacilConTerceroYPermisos() throws Exception {
        String est = nuevoEstudiante();
        String otro = nuevoEstudiante();
        long mat = matricular(est, MAESTRIA);
        long c = cuota(mat, 1);

        assertTrue(texto(enviar(otro, "PAGCUO[\"" + c + "\"]")).contains("no es tuya"));
        assertTrue(texto(enviar(est, "PAGCUO[\"999999\"]")).contains("no existe"));
        assertTrue(texto(enviar(est, "INSPAG[\"" + c + "\",\"3\",\"1000\",\"X\"]")).contains("PAGCUO"));

        List<Email> r = enviar(est, "PAGCUO[\"" + c + "\",\"Luis\",\"Paz\",\"CI\",\"987\",\"7111\",\"luis@mail.com\",\"Padre\"]");
        assertEquals(2, r.size(), texto(r));
        assertEquals("Padre", valor("SELECT parentesco_pagador FROM pago WHERE id_mensualidad = ?", c));
        assertEquals("Luis", valor("SELECT pa.nombres FROM pago p JOIN pagador pa USING (id_pagador) WHERE id_mensualidad = ?", c));

        // Un QR pendiente reserva el saldo y puede rechazarse si no se pagó
        String idPago = valor("SELECT id_pago FROM pago WHERE id_mensualidad = ?", c);
        assertTrue(texto(enviar(est, "INSPAG[\"" + c + "\",\"1\",\"1\",\"QR-RESERVA\"]")).contains("supera el saldo"));
        enviar(SECRETARIA, "RECPAG[\"" + idPago + "\",\"QR vencido\"]");
        assertTrue(texto(enviar(est, "PAGARQR[\"" + idPago + "\"]")).contains("RECHAZADO"));

        // La Secretaria también puede confirmarlo si el callback no llega
        enviar(est, "PAGCUO[\"" + c + "\"]");
        String nuevo = valor("SELECT id_pago FROM pago WHERE id_mensualidad = ? AND estado_conciliacion = 'PENDIENTE'", c);
        enviar(SECRETARIA, "CONPAG[\"" + nuevo + "\"]");
        assertEquals("CONFIRMADA", estadoMatricula(mat));
        assertTrue(texto(enviar(est, "PAGCUO[\"" + c + "\"]")).contains("no tiene saldo"));
    }

    @Test
    void respagPedpagYFallosDePagoFacil() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, MAESTRIA);

        assertTrue(texto(enviar(est, "PEDPAG[\"" + mat + "\"]")).contains("Acceso denegado"));
        List<Email> pedido = enviar(SECRETARIA, "PEDPAG[\"" + mat + "\"]");
        assertEquals(est, pedido.get(0).getTo());
        assertTrue(pedido.get(0).getMessage().contains("RESPAG"));

        assertTrue(texto(enviar(est, "RESPAG[\"" + mat + "\",\"TARJETA\"]")).contains("forma de pago"));
        assertTrue(texto(enviar(est, "respag[\"" + mat + "\",\"fisico\"]")).contains("Secretar"));
        assertEquals("0", valor("SELECT count(*) FROM pago WHERE id_matricula = ?", mat));

        // Si Pago Fácil falla, no queda un pago que reserve el saldo
        pagoFacilCaido = true;
        assertTrue(texto(enviar(est, "RESPAG[\"" + mat + "\",\"QR\"]")).contains("No se pudo generar"));
        pagoFacilCaido = false;
        assertEquals("RECHAZADO", valor("SELECT estado_conciliacion FROM pago WHERE id_matricula = ?", mat));

        // Token vencido: inicia sesión de nuevo y completa el pedido
        tokenVencido = true;
        int logins = loginsPagoFacil;
        assertTrue(texto(enviar(est, "RESPAG[\"" + mat + "\",\"QR\"]")).contains("cid:qr_image"));
        assertEquals(logins + 1, loginsPagoFacil);
        assertEquals("1", valor("SELECT count(*) FROM pago WHERE id_matricula = ? AND estado_conciliacion = 'PENDIENTE'", mat));
    }

    @BeforeAll
    static void iniciarBase() throws Exception {
        pg = EmbeddedPostgres.builder().start();
        DBConnection.database = "postgres";
        DBConnection.server = "localhost";
        DBConnection.port = String.valueOf(pg.getPort());
        DBConnection.user = "postgres";
        DBConnection.password = "postgres";
        try (Connection c = pg.getPostgresDatabase().getConnection(); Statement s = c.createStatement()) {
            s.execute(Files.readString(Path.of("database_script.sql")));
            s.execute(Files.readString(Path.of("datos_iniciales.sql")));
        }
        iniciarPagoFacilSimulado();
    }

    @AfterAll
    static void detenerBase() throws Exception {
        webhook.stop();
        pagoFacil.stop(0);
        pg.close();
    }

    private List<Email> enviar(String from, String comando) {
        enviados.clear();
        app.onReceiptEmail(List.of(new Email(from, comando)));
        return new ArrayList<>(enviados);
    }

    private String texto(List<Email> emails) {
        StringBuilder sb = new StringBuilder();
        for (Email e : emails) {
            sb.append(e.getTo()).append(" | ").append(e.getSubject()).append(" | ").append(e.getMessage()).append('\n');
        }
        return sb.toString();
    }

    private String valor(String sql, Object... params) throws SQLException {
        try (Connection c = pg.getPostgresDatabase().getConnection();
                var ps = c.prepareStatement(sql)) {
            for (int i = 0; i < params.length; i++) {
                ps.setObject(i + 1, params[i]);
            }
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString(1) : null;
            }
        }
    }

    /** Registra a un estudiante nuevo. */
    private String nuevoEstudiante() {
        contador++;
        String correo = "est" + contador + "@mail.com";
        String respuesta = texto(enviar(correo, "INSUSU[\"Eva\",\"Paz\",\"90" + contador + "\",\"CI\",\"" + correo
                + "\",\"eva" + contador + "\",\"ClaveSegura1\",\"70000000\",\"-\",\"2000-01-15\"]"));
        assertTrue(respuesta.contains("Usuario registrado"), respuesta);
        return correo;
    }

    private long preinscribir(String correo, long oferta) throws SQLException {
        String r = texto(enviar(correo, "INSPRE[\"" + oferta + "\",\"Quiero inscribirme\"]"));
        assertTrue(r.contains("pendiente de revisión"), r);
        return Long.parseLong(valor("SELECT max(id_preinscripcion) FROM preinscripcion"));
    }

    private long matricular(String correo, long oferta) throws SQLException {
        long pre = preinscribir(correo, oferta);
        String r = texto(enviar(SECRETARIA, "APRPRE[\"" + pre + "\",\"Datos correctos\"]"));
        assertTrue(r.contains("aprobada"), r);
        return Long.parseLong(valor("SELECT id_matricula FROM matricula m JOIN usuario u USING (id_usuario) "
                + "WHERE u.correo = ? AND m.id_oferta = ?", correo, oferta));
    }

    private long cuota(long matricula, int numero) throws SQLException {
        return Long.parseLong(valor("SELECT id_mensualidad FROM mensualidad WHERE id_matricula = ? AND numero_cuota = ?",
                matricula, numero));
    }

    private String estadoMatricula(long id) throws SQLException {
        return valor("SELECT estado FROM matricula WHERE id_matricula = ?", id);
    }

    @Test
    void flujoCompletoConBecaYPagoDeTercero() throws Exception {
        // 1-2. Consulta el catálogo sin registrarse
        String catalogo = texto(enviar("visitante@mail.com", "LISOFE[]"));
        assertTrue(catalogo.contains("DIP-EDS-2027-1") && catalogo.contains("3000.00"), catalogo);

        // 3-4. Se registra y se preinscribe
        String estudiante = nuevoEstudiante();
        long pre = preinscribir(estudiante, DIPLOMADO);
        assertEquals("PENDIENTE", valor("SELECT estado FROM preinscripcion WHERE id_preinscripcion = ?", pre));

        // 5-6. La Secretaria aprueba y el estudiante recibe el costo y la modalidad
        List<Email> aprobacion = enviar(SECRETARIA, "APRPRE[\"" + pre + "\",\"Datos correctos\"]");
        assertEquals(estudiante, aprobacion.get(1).getTo());
        assertTrue(aprobacion.get(1).getMessage().contains("UNICO"));
        long mat = Long.parseLong(valor("SELECT max(id_matricula) FROM matricula"));
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));

        // 7. Beca del 30 %: de Bs 3000 a Bs 2100
        enviar(SECRETARIA, "INSBES[\"" + mat + "\",\"1\"]");
        assertEquals("2100.00", valor("SELECT monto_final FROM matricula WHERE id_matricula = ?", mat));
        assertEquals("900.00", valor("SELECT monto_descuento FROM mensualidad WHERE id_matricula = ?", mat));

        // 8. Un tercero paga por QR; el pago queda pendiente
        String r = texto(enviar(estudiante, "INSPAG[\"" + cuota(mat, 1) + "\",\"1\",\"2100\",\"QR-1\",\"https://x/c.jpg\","
                + "\"Luis\",\"Paz\",\"CI\",\"555\",\"7111\",\"luis@mail.com\",\"Padre\"]"));
        assertTrue(r.contains("pendiente de verificación"), r);
        assertEquals("PENDIENTE", valor("SELECT estado_conciliacion FROM pago WHERE referencia = 'QR-1'"));
        assertEquals("Padre", valor("SELECT parentesco_pagador FROM pago WHERE referencia = 'QR-1'"));
        assertEquals("Luis", valor("SELECT pa.nombres FROM pago p JOIN pagador pa USING (id_pagador) WHERE referencia = 'QR-1'"));
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));

        // 9-11. El personal autorizado confirma y la matrícula se actualiza
        long pago = Long.parseLong(valor("SELECT id_pago FROM pago WHERE referencia = 'QR-1'"));
        List<Email> confirmacion = enviar(SECRETARIA, "CONPAG[\"" + pago + "\"]");
        assertEquals("CONFIRMADA", estadoMatricula(mat));
        assertEquals("PAGADA", valor("SELECT estado FROM mensualidad WHERE id_matricula = ?", mat));
        assertEquals(estudiante, confirmacion.get(1).getTo());

        // 12. El estudiante consulta su inscripción y su historial
        assertTrue(texto(enviar(estudiante, "LISMAT[]")).contains("CONFIRMADA"));
        assertTrue(texto(enviar(estudiante, "LISPAG[]")).contains("CONFIRMADO"));
    }

    @Test
    void mensualidadesConfirmanLaMatriculaConLaPrimeraCuota() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, MAESTRIA);
        // Marzo 2027 a febrero 2028: 12 cuotas de Bs 1000
        assertEquals("12", valor("SELECT count(*) FROM mensualidad WHERE id_matricula = ?", mat));
        assertEquals("12000.00", valor("SELECT monto_final FROM matricula WHERE id_matricula = ?", mat));
        assertEquals("MENSUAL", valor("SELECT tipo_cobro FROM matricula WHERE id_matricula = ?", mat));
        assertEquals("2027-04-01", valor("SELECT fecha_vencimiento FROM mensualidad WHERE id_matricula = ? AND numero_cuota = 2", mat));

        // Efectivo: lo registra la Secretaria, no el estudiante
        String denegado = texto(enviar(est, "INSPAG[\"" + cuota(mat, 1) + "\",\"2\",\"1000\",\"REC-1\"]"));
        assertTrue(denegado.contains("Secretar"), denegado);
        enviar(SECRETARIA, "INSPAG[\"" + cuota(mat, 1) + "\",\"2\",\"1000\",\"REC-1\"]");
        enviar(SECRETARIA, "CONPAG[\"" + valor("SELECT id_pago FROM pago WHERE referencia = 'REC-1'") + "\"]");

        assertEquals("CONFIRMADA", estadoMatricula(mat));
        assertEquals("1", valor("SELECT count(*) FROM mensualidad WHERE id_matricula = ? AND estado = 'PAGADA'", mat));
    }

    @Test
    void pagoRechazadoSePuedeRegistrarDeNuevoYNoSeExcedeElSaldo() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, DIPLOMADO);
        long c = cuota(mat, 1);

        assertTrue(texto(enviar(est, "INSPAG[\"" + c + "\",\"1\",\"3000.01\",\"QR-A\"]")).contains("supera el saldo"));

        enviar(est, "INSPAG[\"" + c + "\",\"1\",\"3000\",\"QR-B\"]");
        // El saldo ya está comprometido por el pago pendiente
        assertTrue(texto(enviar(est, "INSPAG[\"" + c + "\",\"1\",\"1\",\"QR-C\"]")).contains("supera el saldo"));

        String idPago = valor("SELECT id_pago FROM pago WHERE referencia = 'QR-B'");
        List<Email> rechazo = enviar(SECRETARIA, "RECPAG[\"" + idPago + "\",\"Comprobante ilegible\"]");
        assertTrue(texto(rechazo).contains("Comprobante ilegible"));
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));

        // No se puede confirmar lo ya rechazado
        assertTrue(texto(enviar(SECRETARIA, "CONPAG[\"" + idPago + "\"]")).contains("RECHAZADO"));
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));

        // Pagos parciales: la cuota se salda con la suma de los confirmados
        enviar(est, "INSPAG[\"" + c + "\",\"1\",\"1000\",\"QR-D\"]");
        enviar(SECRETARIA, "CONPAG[\"" + valor("SELECT id_pago FROM pago WHERE referencia = 'QR-D'") + "\"]");
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));
        enviar(est, "INSPAG[\"" + c + "\",\"1\",\"2000\",\"QR-E\"]");
        enviar(SECRETARIA, "CONPAG[\"" + valor("SELECT id_pago FROM pago WHERE referencia = 'QR-E'") + "\"]");
        assertEquals("CONFIRMADA", estadoMatricula(mat));
    }

    @Test
    void referenciaRepetidaSeRechaza() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, MAESTRIA);
        enviar(est, "INSPAG[\"" + cuota(mat, 1) + "\",\"1\",\"1000\",\"QR-UNICA\"]");
        String r = texto(enviar(est, "INSPAG[\"" + cuota(mat, 2) + "\",\"1\",\"1000\",\"qr-unica\"]"));
        assertTrue(r.contains("Ya existe un pago"), r);
    }

    @Test
    void becaTotalConfirmaSinPagoYSePuedeRevertir() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, DIPLOMADO);
        enviar(SECRETARIA, "INSBES[\"" + mat + "\",\"2\",\"100\"]");
        assertEquals("0.00", valor("SELECT monto_final FROM matricula WHERE id_matricula = ?", mat));
        assertEquals("CONFIRMADA", estadoMatricula(mat));

        assertTrue(texto(enviar(SECRETARIA, "INSBES[\"" + mat + "\",\"1\"]")).contains("ya tiene una beca"));

        enviar(SECRETARIA, "DELBES[\"" + mat + "\"]");
        assertEquals("3000.00", valor("SELECT monto_final FROM matricula WHERE id_matricula = ?", mat));
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));
        assertEquals("PENDIENTE", valor("SELECT estado FROM mensualidad WHERE id_matricula = ?", mat));
    }

    @Test
    void becaNoCambiaConPagosVigentes() throws Exception {
        String est = nuevoEstudiante();
        long mat = matricular(est, DIPLOMADO);
        enviar(est, "INSPAG[\"" + cuota(mat, 1) + "\",\"1\",\"3000\",\"QR-BV\"]");
        assertTrue(texto(enviar(SECRETARIA, "INSBES[\"" + mat + "\",\"1\"]")).contains("ya tiene pagos"));
        assertEquals("3000.00", valor("SELECT monto_final FROM matricula WHERE id_matricula = ?", mat));
    }

    @Test
    void rolesYPermisosSeRespetan() throws Exception {
        String est = nuevoEstudiante();
        String otro = nuevoEstudiante();

        assertTrue(texto(enviar(est, "LISUSU[]")).contains("Acceso denegado"));
        assertTrue(texto(enviar(est, "INSPRO[\"X\",\"X\",\"X\",\"X\"]")).contains("Acceso denegado"));
        assertTrue(texto(enviar(PROFESOR, "INSPRE[\"1\"]")).contains("Acceso denegado"));
        assertTrue(texto(enviar(DIRECTOR, "CONPAG[\"1\"]")).contains("Acceso denegado"));

        // Datos y matrículas ajenas
        String ciOtro = valor("SELECT ci FROM usuario WHERE correo = ?", otro);
        assertTrue(texto(enviar(est, "LISUSU[\"" + ciOtro + "\"]")).contains("Acceso denegado"));
        assertTrue(texto(enviar(est, "MODUSU[\"" + ciOtro + "\",\"Hack\"]")).contains("Acceso denegado"));
        long matOtro = matricular(otro, DIPLOMADO);
        assertTrue(texto(enviar(est, "LISMAT[\"" + matOtro + "\"]")).contains("Acceso denegado"));
        assertTrue(texto(enviar(est, "LISCUO[\"" + matOtro + "\"]")).contains("Acceso denegado"));
        assertTrue(texto(enviar(est, "INSPAG[\"" + cuota(matOtro, 1) + "\",\"1\",\"1\",\"QR-AJENO\"]")).contains("no es tuya"));

        // El permiso se concede y se retira desde la base de datos
        assertTrue(texto(enviar(est, "INSBEC[\"B\",\"-\",\"10\",\"-\"]")).contains("Acceso denegado"));
        enviar(DIRECTOR, "INSPER[\"Estudiante\",\"BECA_GESTIONAR\"]");
        assertTrue(texto(enviar(est, "INSBEC[\"B\",\"-\",\"10\",\"-\"]")).contains("Beca registrada"));
        enviar(DIRECTOR, "DELPER[\"Estudiante\",\"BECA_GESTIONAR\"]");
        assertTrue(texto(enviar(est, "INSBEC[\"B\",\"-\",\"10\",\"-\"]")).contains("Acceso denegado"));

        // Registro de personal con rol
        String r = texto(enviar(DIRECTOR, "INSUSU[\"Sol\",\"Vaca\",\"777\",\"CI\",\"sol@mail.com\",\"sol\",\"ClaveSegura1\",\"Coordinador\"]"));
        assertTrue(r.contains("Coordinador Académico"), r);
        assertEquals(1, Integer.parseInt(valor("SELECT count(*) FROM usuario_rol ur JOIN usuario u USING (id_usuario) WHERE u.ci = '777'")));
        enviar(DIRECTOR, "INSROL[\"777\",\"Secretaria\"]");
        enviar(DIRECTOR, "DELROL[\"777\",\"Secretaria\"]");
        assertTrue(texto(enviar(DIRECTOR, "LISUSU[\"777\"]")).contains("Coordinador Académico"));

        // Siempre queda un Director
        assertTrue(texto(enviar(DIRECTOR, "DELROL[\"1000001\",\"Director\"]")).contains("al menos un Director"));
        assertTrue(texto(enviar(DIRECTOR, "DELUSU[\"1000001\"]")).contains("propia cuenta"));

        // Baja de usuario: pierde el acceso
        enviar(DIRECTOR, "DELUSU[\"" + ciOtro + "\"]");
        assertTrue(texto(enviar(otro, "LISMAT[]")).contains("inactiva"));
    }

    @Test
    void accesoPorCorreoYRegistroSeValidan() throws Exception {
        String est = nuevoEstudiante();

        // Como en la referencia, basta el correo registrado
        assertTrue(texto(enviar(est, "LISPRE[]")).contains("No hay registros"));
        assertTrue(texto(enviar(est, "HELP")).contains("INSPRE"));
        // Sin registro solo hay comandos públicos
        assertTrue(texto(enviar("nadie@mail.com", "LISMAT[]")).contains("no está registrado"));
        for (String cmd : new String[] { "LISMAT[]", "INSPRE[\"1\",\"x\"]", "LISCUO[\"1\"]", "PAGCUO[\"1\"]",
                "RESPAG[\"1\",\"QR\"]", "LISPAG[]", "MODCLA[\"a\",\"b\"]", "lispre[]" }) {
            String r = texto(enviar("nadie@mail.com", cmd));
            String nombre = cmd.substring(0, 6).toUpperCase();
            assertTrue(r.contains("No puedes usar el comando " + nombre), cmd + " -> " + r);
            assertTrue(r.contains("regístrate") && r.contains("INSUSU"), r);
            assertTrue(r.contains("HELP, INSUSU, LISPRO, LISOFE, LISARA"), r);
        }
        // Lo público sigue disponible sin registro
        assertTrue(texto(enviar("nadie@mail.com", "LISOFE[]")).contains("DIP-EDS-2027-1"));
        assertTrue(texto(enviar("nadie@mail.com", "HELP")).contains("regístrate"));
        assertTrue(texto(enviar(est, "LISOFE[]")).contains("DIP-EDS-2027-1"));

        // La clave se guarda solo como hash y no vuelve en las respuestas
        assertNull(valor("SELECT password FROM usuario WHERE correo = ?", est));
        assertTrue(valor("SELECT password_hash FROM usuario WHERE correo = ?", est).startsWith("$2a$"));
        assertFalse(texto(enviar(est, "LISUSU[\"-\"]")).contains("$2a$"));

        // Cambio de contraseña
        assertTrue(texto(enviar(est, "MODCLA[\"incorrecta\",\"OtraClave123\"]")).contains("incorrecta"));
        enviar(est, "MODCLA[\"ClaveSegura1\",\"OtraClave123\"]");
        assertTrue(texto(enviar(est, "MODCLA[\"OtraClave123\",\"OtraClave456\"]")).contains("Contraseña actualizada"));

        // Registro: remitente, duplicados, formatos y rol
        String base = "\"Ana\",\"Rios\",\"801\",\"CI\",\"ana@mail.com\",\"ana\",\"ClaveSegura1\"";
        assertTrue(texto(enviar("otra@mail.com", "INSUSU[" + base + "]")).contains("mismo"));
        assertTrue(texto(enviar("ana@mail.com", "INSUSU[" + base + ",\"Director\"]")).contains("solo el Director puede asignar roles"));
        assertTrue(texto(enviar("ana@mail.com", "INSUSU[\"Ana\",\"Rios\",\"801\",\"CI\",\"ana@mail.com\",\"ana\",\"corta\"]")).contains("8 caracteres"));
        assertTrue(texto(enviar("ana@mail.com", "INSUSU[" + base + ",\"-\",\"-\",\"15/01/2000\"]")).contains("AAAA-MM-DD"));
        assertTrue(texto(enviar("ana@mail.com", "INSUSU[" + base + "]")).contains("Usuario registrado"));
        assertTrue(texto(enviar("ana2@mail.com", "INSUSU[\"Ana\",\"Rios\",\"801\",\"CI\",\"ana2@mail.com\",\"ana2\",\"ClaveSegura1\"]")).contains("documento"));
        assertEquals("Estudiante", valor("SELECT r.nombre FROM usuario_rol ur JOIN rol r USING (id_rol) JOIN usuario u USING (id_usuario) WHERE u.correo = 'ana@mail.com'"));
    }

    @Test
    void preinscripcionRechazadaCanceladaYCupos() throws Exception {
        // Cierre, rechazo y reintento
        String est = nuevoEstudiante();
        long pre = preinscribir(est, DIPLOMADO);
        assertTrue(texto(enviar(est, "INSPRE[\"1\"]")).contains("vigente"));
        assertTrue(texto(enviar(est, "APRPRE[\"" + pre + "\"]")).contains("Acceso denegado"));
        List<Email> rechazo = enviar(SECRETARIA, "RECPRE[\"" + pre + "\",\"Falta título\"]");
        assertTrue(texto(rechazo).contains("Falta título"));
        assertTrue(texto(enviar(SECRETARIA, "APRPRE[\"" + pre + "\"]")).contains("RECHAZADA"));

        // Cancelar una matrícula sin pagos libera el cupo y permite volver a inscribirse
        long pre2 = preinscribir(est, DIPLOMADO);
        enviar(SECRETARIA, "APRPRE[\"" + pre2 + "\"]");
        long mat = Long.parseLong(valor("SELECT id_matricula FROM matricula m JOIN usuario u USING (id_usuario) WHERE u.correo = ?", est));
        String numero = valor("SELECT numero_registro FROM matricula WHERE id_matricula = ?", mat);
        enviar(est, "CANPRE[\"" + pre2 + "\"]");
        assertEquals("CANCELADA", estadoMatricula(mat));
        assertTrue(texto(enviar(est, "INSPAG[\"" + cuota(mat, 1) + "\",\"1\",\"1\",\"QR-CAN\"]")).contains("cancelada"));

        long pre3 = preinscribir(est, DIPLOMADO);
        enviar(SECRETARIA, "APRPRE[\"" + pre3 + "\"]");
        assertEquals("PENDIENTE_PAGO", estadoMatricula(mat));
        assertEquals(numero, valor("SELECT numero_registro FROM matricula WHERE id_matricula = ?", mat));
        assertEquals("1", valor("SELECT count(*) FROM mensualidad WHERE id_matricula = ?", mat));

        // Con un pago vigente ya no se cancela
        enviar(est, "INSPAG[\"" + cuota(mat, 1) + "\",\"1\",\"100\",\"QR-VIG\"]");
        assertTrue(texto(enviar(est, "CANPRE[\"" + pre3 + "\"]")).contains("No se puede cancelar"));

        // Cupo: una oferta de 1 cupo admite una sola matrícula
        enviar(COORDINADOR, "INSOFE[\"OF-1CUPO\",\"1\",\"virtual\",\"2027-03-01\",\"2027-06-30\",\"1\",\"0\",\"2027\"]");
        long oferta = Long.parseLong(valor("SELECT id_oferta FROM oferta_academica WHERE codigo = 'OF-1CUPO'"));
        assertTrue(texto(enviar(COORDINADOR, "INSARA[\"" + oferta + "\",\"Único\",\"500\",\"unico\"]")).contains("Arancel registrado"));
        String a = nuevoEstudiante();
        String b = nuevoEstudiante();
        long preA = preinscribir(a, oferta);
        long preB = preinscribir(b, oferta);
        assertTrue(texto(enviar(SECRETARIA, "APRPRE[\"" + preA + "\"]")).contains("aprobada"));
        assertTrue(texto(enviar(SECRETARIA, "APRPRE[\"" + preB + "\"]")).contains("no tiene cupos"));
        assertEquals("PENDIENTE", valor("SELECT estado FROM preinscripcion WHERE id_preinscripcion = ?", preB));

        // Oferta cerrada, y oferta sin aranceles
        String c = nuevoEstudiante();
        enviar(COORDINADOR, "DELOFE[\"" + oferta + "\"]");
        assertTrue(texto(enviar(c, "INSPRE[\"" + oferta + "\"]")).contains("no está abierta"));
        assertFalse(texto(enviar("visitante@mail.com", "LISOFE[]")).contains("OF-1CUPO"));

        enviar(COORDINADOR, "INSOFE[\"OF-SINARA\",\"1\",\"virtual\",\"2027-03-01\",\"-\",\"5\",\"0\",\"2027\"]");
        long sinArancel = Long.parseLong(valor("SELECT id_oferta FROM oferta_academica WHERE codigo = 'OF-SINARA'"));
        long preC = preinscribir(c, sinArancel);
        assertTrue(texto(enviar(SECRETARIA, "APRPRE[\"" + preC + "\"]")).contains("no tiene aranceles"));
        assertTrue(texto(enviar(COORDINADOR, "INSARA[\"" + sinArancel + "\",\"Mes\",\"100\",\"MENSUAL\"]")).contains("fecha de fin"));
    }

    @Test
    void entradasInvalidasRespondenConMensajeClaro() {
        String est = "visitante@mail.com";
        assertTrue(texto(enviar(est, "hola")).contains("no está registrado"));
        assertTrue(texto(enviar(DIRECTOR, "XXXYYY[]")).contains("no reconocido"));
        assertTrue(texto(enviar(DIRECTOR, "CONUSU[\"1\"]")).contains("no disponible"));
        assertTrue(texto(enviar(DIRECTOR, "LISOFE[\"abc\"]")).contains("debe ser un número"));
        assertTrue(texto(enviar(COORDINADOR, "INSPRO[\"A\"]")).contains("falta un valor obligatorio"));
        assertTrue(texto(enviar(COORDINADOR, "INSARA[\"1\",\"X\",\"-5\",\"UNICO\"]")).contains("monto"));
        // "*" lista todo, como en el enunciado
        assertTrue(texto(enviar(est, "LISOFE[\"*\"]")).contains("DIP-EDS-2027-1"));
        assertTrue(texto(enviar(DIRECTOR, "LISPER[\"*\"]")).contains("USUARIO_GESTIONAR"));
        assertTrue(texto(enviar(DIRECTOR, "LISUSU[\"*\"]")).contains("Lista de usuarios"));
        // Comillas tipográficas, minúsculas y HTML del usuario
        assertTrue(texto(enviar(est, "lisofe[“1”]")).contains("DIP-EDS-2027-1"));
        assertFalse(texto(enviar(COORDINADOR, "INSPRO[\"XSS\",\"<script>x</script>\",\"-\",\"T\"]")).contains("<script>"));
    }

    @Test
    void ayudaSegunRolYCorreosEspeciales() {
        assertFalse(texto(enviar("visitante@mail.com", "HELP")).contains("CONPAG"));
        assertTrue(texto(enviar(SECRETARIA, "help")).contains("CONPAG"));
        assertFalse(texto(enviar(SECRETARIA, "HELP")).contains("INSROL"));
        assertTrue(texto(enviar(DIRECTOR, "HELP")).contains("INSROL"));
        // Los rebotes no reciben respuesta
        assertTrue(enviar("", "HELP").isEmpty());
        assertTrue(enviar("MAILER-DAEMON@x.com", "HELP").isEmpty());
    }

    @Test
    void lecturaDeCabeceras() {
        String crudo = "Return-Path: <juan@mail.com>\nReceived: from a\n by b\nFrom: Juan <juan@mail.com>\n"
                + "Subject: =?UTF-8?B?TElTT0ZF?=\n =?UTF-8?B?W10=?=\nTo: icap@x\n\nSubject: cuerpo\nFrom: otro\n";
        Email e = Email.getEmail(crudo);
        assertEquals("juan@mail.com", e.getFrom());
        assertEquals("=?UTF-8?B?TElTT0ZF?= =?UTF-8?B?W10=?=", e.getSubject());
        assertEquals("", Email.getEmail("Return-Path: <>\nSubject: x\n\n").getFrom());
        assertEquals("a@b.com", Email.getEmail("From: A <a@b.com>\nSubject: x\n\n").getFrom());
        // Asunto en MIME partido en dos líneas: llega como comando válido
        assertTrue(texto(enviar("visitante@mail.com", e.getSubject())).contains("DIP-EDS-2027-1"));
    }
}
