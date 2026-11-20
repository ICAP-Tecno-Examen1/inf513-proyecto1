package postgresConecction;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import com.sun.net.httpserver.HttpServer;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpExchange;

import org.json.JSONObject;
import data.DMensualidad;
import data.DPago;
import negocio.NMetodoPago;
import librerias.HtmlRes;

public class WebHookServer {

    public static int PORT = 8080;
    private static final String CONTEXT = "/webhook/pagofacil";
    /** Solo con PAGOFACIL_SIMULADO=true: simula que Pago Fácil avisa que se pagó un QR. */
    private static final String CONTEXT_SIMULAR = "/webhook/simular";

    private HttpServer server;
    private final DPago dPago;
    private final DMensualidad dMensualidad;
    private final Set<String> processedWebhooks = new HashSet<>();
    private final PagoFacilHandler pagoFacilHandler = new PagoFacilHandler();

    public WebHookServer() {
        this.dPago = new DPago();
        this.dMensualidad = new DMensualidad();
    }

    public void start() {
        try {
            server = HttpServer.create(new InetSocketAddress(PORT), 0);
            server.createContext(CONTEXT, pagoFacilHandler);
            if (PagoFacilService.SIMULADO) {
                server.createContext(CONTEXT_SIMULAR, new SimularHandler());
            }
            server.setExecutor(null);
            server.start();
            System.out.println("=== WebHookServer iniciado en puerto " + getPort() + " ===");
            System.out.println("Escuchando en: http://localhost:" + getPort() + CONTEXT);
            if (PagoFacilService.SIMULADO) {
                System.out.println("PAGO FÁCIL SIMULADO: confirma un pago abriendo http://localhost:" + getPort()
                        + CONTEXT_SIMULAR + "?pago={id_pago}");
            }
        } catch (IOException e) {
            System.err.println("Error al iniciar WebHookServer: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            System.out.println("WebHookServer detenido.");
        }
    }

    public int getPort() {
        return server.getAddress().getPort();
    }

    protected void enviarHtml(String to, String subject, String html) {
        new Thread(() -> new EmailSend().sendEmailHtml(to, subject, html)).start();
    }

    /**
     * GET /webhook/simular?pago={id_pago}: arma el mismo PedidoID que Pago Fácil enviaría y lo
     * procesa como un aviso de pago (Estado 2). Solo acepta pagos QR de Pago Fácil pendientes.
     */
    private class SimularHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            String query = exchange.getRequestURI().getQuery();
            String id = null;
            if (query != null) {
                for (String par : query.split("&")) {
                    if (par.startsWith("pago=")) {
                        id = par.substring(5).trim();
                    }
                }
            }
            if (id == null || !id.matches("[0-9]{1,18}")) {
                html(exchange, 400, "Falta el id del pago", "Usa /webhook/simular?pago={id_pago}.");
                return;
            }
            long idPago = Long.parseLong(id);
            try {
                // { id_mensualidad, monto, referencia, estado, id_metodo_pago }
                String[] pago = dPago.getParaQr(idPago);
                if (pago == null || pago[0] == null || !new NMetodoPago().esPagoFacil(Long.parseLong(pago[4]))) {
                    html(exchange, 404, "Pago no encontrado", "El pago " + idPago + " no existe o no es de Pago Fácil.");
                    return;
                }
                if (!"PENDIENTE".equals(pago[3])) {
                    html(exchange, 409, "Pago ya procesado", "El pago " + idPago + " ya está " + pago[3] + ".");
                    return;
                }
                String pedidoId = "CUOTA-" + pago[0] + "-" + idPago + "-" + pago[2] + "-sim";
                System.out.println("=== Webhook SIMULADO: " + pedidoId + " (Estado 2) ===");
                pagoFacilHandler.procesarWebhook(pedidoId, 2);
                String estado = dPago.getParaQr(idPago)[3];
                html(exchange, 200, "Pago simulado", "Pago " + idPago + " por Bs " + pago[1] + ": " + estado
                        + ". El estudiante recibirá el correo de confirmación.");
            } catch (Exception e) {
                System.err.println("Error simulando el pago " + idPago + ": " + e.getMessage());
                html(exchange, 500, "Error", "No se pudo simular el pago: " + e.getMessage());
            }
        }

        private void html(HttpExchange exchange, int status, String titulo, String mensaje) throws IOException {
            byte[] bytes = HtmlRes.generateInfoMessage(titulo, mensaje).getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }
        }
    }

    private class PagoFacilHandler implements HttpHandler {

        @Override
        public void handle(HttpExchange exchange) throws IOException {
            if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
                sendResponse(exchange, 405, "{\"error\": \"Método no permitido\"}");
                return;
            }

            String body = new String(exchange.getRequestBody().readAllBytes());
            System.out.println("=== Webhook Pago Fácil recibido ===");
            System.out.println("Body: " + body);

            String pedidoId;
            int estado;

            try {
                JSONObject json = new JSONObject(body);
                pedidoId = json.get("PedidoID").toString();
                estado = json.getInt("Estado");
            } catch (Exception e) {
                System.err.println("Error parseando webhook: " + e.getMessage());
                sendResponse(exchange, 400, "{\"error\": \"Invalid JSON\"}");
                return;
            }

            String webhookKey = pedidoId + "_" + estado;
            if (processedWebhooks.contains(webhookKey)) {
                System.out.println("Webhook duplicado ignorado: " + webhookKey);
                sendResponse(exchange, 200, "{\"status\": \"already_processed\"}");
                return;
            }

            // Se procesa antes de responder: si la base falla, Pago Fácil no recibe un "ok" falso.
            try {
                procesarWebhook(pedidoId, estado);
                processedWebhooks.add(webhookKey);
                sendResponse(exchange, 200, "{\"status\": \"ok\"}");
            } catch (Exception e) {
                System.err.println("Error procesando webhook: " + e.getMessage());
                e.printStackTrace();
                sendResponse(exchange, 500, "{\"error\": \"internal\"}");
            }
        }

        private void procesarWebhook(String pedidoId, int estado) throws SQLException {
            if (pedidoId.startsWith("CUOTA-")) {
                String[] parts = pedidoId.split("-");
                if (parts.length < 4 || !parts[1].matches("\\d+") || !parts[2].matches("\\d+")) {
                    System.err.println("Formato de pedidoId no reconocido: " + pedidoId);
                    return;
                }
                procesarPagoCuota(parts[1], Long.parseLong(parts[2]), parts[3], estado);
            } else {
                System.err.println("Formato de pedidoId no reconocido: " + pedidoId);
            }
        }

        private void procesarPagoCuota(String cuotaId, long pagoId, String referencia, int estado) throws SQLException {
            if (estado == 2) {
                // { id_mensualidad, monto, referencia, estado, id_metodo_pago }
                String[] pago = dPago.getParaQr(pagoId);
                if (pago == null || !cuotaId.equals(pago[0]) || !referencia.equals(pago[2])) {
                    System.err.println("El pedido no corresponde al pago " + pagoId + "; se ignora.");
                    return;
                }
                if ("RECHAZADO".equals(pago[3])) {
                    System.err.println("Pago Fácil informó el cobro del pago " + pagoId
                            + ", que estaba RECHAZADO: revisar manualmente.");
                    return;
                }
                if (!"PENDIENTE".equals(pago[3])) {
                    return;
                }

                // { correo, numero_registro, estado de la matrícula, estado de la cuota }
                String[] resultado = dPago.confirmar(pagoId);
                System.out.println("Pago " + pagoId + " marcado como CONFIRMADO");
                if ("PAGADA".equals(resultado[3])) {
                    long idCuota = Long.parseLong(pago[0]);
                    if (dMensualidad.quedanPendientes(idCuota)) {
                        notificarCuotaPagada(idCuota, pagoId, pago[1], resultado);
                    } else {
                        notificarPagoCompletado(idCuota, pagoId, pago[1], resultado);
                    }
                }
            }
        }

        private void notificarCuotaPagada(long idCuota, long pagoId, String monto, String[] resultado) {
            try {
                // { nombre, ci, telefono, correo, concepto, numero_cuota }
                String[] cuota = dMensualidad.getDatosCliente(idCuota);

                String htmlContent = "<!DOCTYPE html>"
                        + "<html lang='es'>"
                        + "<head>"
                        + "<meta charset='UTF-8'>"
                        + "<title>Cuota Pagada</title>"
                        + "<style>"
                        + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }"
                        + ".container { width: 100%; max-width: none; margin: 0; background: white; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }"
                        + ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 20px; text-align: center; }"
                        + ".content { padding: 40px 20px; text-align: center; }"
                        + ".success { background: #d4edda; color: #155724; padding: 20px; border-radius: 10px; margin: 20px 0; }"
                        + ".footer { background: #f8f9fa; padding: 20px; text-align: center; color: #666; font-size: 14px; }"
                        + "</style>"
                        + "</head>"
                        + "<body>"
                        + "<div class='container'>"
                        + "<div class='header'>"
                        + "<h1>✅ Cuota #" + cuota[5] + " Pagada</h1>"
                        + "</div>"
                        + "<div class='content'>"
                        + "<div class='success'>"
                        + "<p>Estimado/a " + HtmlRes.escape(cuota[0]) + "</p>"
                        + "<p>Su cuota #" + cuota[5] + " de " + HtmlRes.escape(cuota[4]) + " por Bs. " + monto
                        + " ha sido registrada como pagada.</p>"
                        + "<p>Matrícula " + resultado[1] + ": " + resultado[2] + ".</p>"
                        + "<p>Aún faltan cuotas pendientes por pagar.</p>"
                        + "</div>"
                        + "</div>"
                        + "<div class='footer'>"
                        + "<p>📧 ICAP-UAGRM</p>"
                        + "</div>"
                        + "</div>"
                        + "</body>"
                        + "</html>";

                enviarHtml(resultado[0],
                        "✅ Cuota #" + cuota[5] + " Pagada - Pago #" + pagoId,
                        htmlContent);

            } catch (Exception e) {
                System.err.println("Error enviando notificación de cuota pagada: " + e.getMessage());
            }
        }

        private void notificarPagoCompletado(long idCuota, long pagoId, String monto, String[] resultado) {
            try {
                String[] cuota = dMensualidad.getDatosCliente(idCuota);

                String htmlContent = "<!DOCTYPE html>"
                        + "<html lang='es'>"
                        + "<head>"
                        + "<meta charset='UTF-8'>"
                        + "<meta name='viewport' content='width=device-width, initial-scale=1.0'>"
                        + "<title>Pago Confirmado</title>"
                        + "<style>"
                        + "body { font-family: 'Segoe UI', Tahoma, Geneva, Verdana, sans-serif; margin: 0; padding: 0; background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); min-height: 100vh; }"
                        + ".container { width: 100%; max-width: none; margin: 0; background: white; box-shadow: 0 10px 30px rgba(0,0,0,0.2); }"
                        + ".header { background: linear-gradient(135deg, #667eea 0%, #764ba2 100%); color: white; padding: 40px 20px; text-align: center; }"
                        + ".header h1 { margin: 0; font-size: 32px; font-weight: 300; }"
                        + ".header h2 { margin: 15px 0 0 0; font-size: 28px; font-weight: 300; opacity: 0.9; }"
                        + ".content { padding: 40px 20px; }"
                        + ".info-box { background: #f5f5f5; padding: 25px; border-radius: 15px; margin: 30px 0; text-align: left; box-shadow: 0 8px 25px rgba(0,0,0,0.1); }"
                        + ".info-box p { margin: 12px 0; color: #333; font-size: 16px; }"
                        + ".info-box strong { color: #667eea; }"
                        + ".status-badge { display: inline-block; background: #4CAF50; color: white; padding: 8px 20px; border-radius: 20px; font-weight: bold; font-size: 14px; text-transform: uppercase; }"
                        + ".footer { background: #f8f9fa; padding: 30px 20px; text-align: center; color: #666; font-size: 14px; border-top: 2px solid #eee; }"
                        + "@media (max-width: 768px) { .header { padding: 30px 15px; } .header h1 { font-size: 28px; } .header h2 { font-size: 24px; } .content { padding: 30px 15px; } }"
                        + "@media (max-width: 480px) { .header { padding: 25px 10px; } .header h1 { font-size: 24px; } .content { padding: 25px 10px; } }"
                        + "</style>"
                        + "</head>"
                        + "<body>"
                        + "<div class='container'>"
                        + "<div class='header'>"
                        + "<h1>🏢 Sistema de Gestión ICAP-UAGRM</h1>"
                        + "<h2>✅ Pago Confirmado</h2>"
                        + "<p>Gracias por tu pago, " + HtmlRes.escape(cuota[0]) + "</p>"
                        + "</div>"
                        + "<div class='content'>"
                        + "<div class='info-box'>"
                        + "<p><strong>ID de Pago:</strong> " + pagoId + "</p>"
                        + "<p><strong>Concepto:</strong> " + HtmlRes.escape(cuota[4]) + "</p>"
                        + "<p><strong>Monto Pagado:</strong> Bs. " + monto + "</p>"
                        + "<p><strong>Fecha:</strong> " + java.time.LocalDate.now() + "</p>"
                        + "<p><strong>Matrícula:</strong> " + resultado[1] + "</p>"
                        + "<p><strong>Estado:</strong> <span class='status-badge'>" + resultado[2] + "</span></p>"
                        + "</div>"
                        + "<p style='color: #4CAF50; text-align: center; font-weight: bold;'>✅ ¡Tu pago ha sido registrado exitosamente!</p>"
                        + "<p style='color: #666; text-align: center;'>Si tienes alguna pregunta, no dudes en contactarnos.</p>"
                        + "</div>"
                        + "<div class='footer'>"
                        + "<p>📧 Sistema de Comunicación por Email | TecnoWeb 2026</p>"
                        + "<p>🕒 "
                        + java.time.LocalDateTime.now()
                                .format(java.time.format.DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss"))
                        + "</p>"
                        + "</div>"
                        + "</div>"
                        + "</body>"
                        + "</html>";

                enviarHtml(resultado[0],
                        "✅ Pago #" + pagoId + " Confirmado - ICAP-UAGRM",
                        htmlContent);

                System.out.println("Notificación de pago completado enviada a: " + resultado[0]);

            } catch (SQLException e) {
                System.err.println("Error al enviar notificación de pago: " + e.getMessage());
            }
        }

        private void sendResponse(HttpExchange exchange, int statusCode, String response)
                throws IOException {
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            byte[] responseBytes = response.getBytes();
            exchange.sendResponseHeaders(statusCode, responseBytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(responseBytes);
            }
        }
    }
}
