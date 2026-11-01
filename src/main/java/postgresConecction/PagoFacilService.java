package postgresConecction;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Base64;

import org.json.JSONObject;
import org.json.JSONArray;
import io.github.cdimascio.dotenv.Dotenv;

/**
 * Cliente HTTP para la API MasterQR de Pago Fácil (Bolivia).
 * Usa java.net.http.HttpClient (Java 11+).
 *
 * CONFIGURACIÓN: Variables de entorno requeridas:
 *   - PAGOFACIL_TOKEN_SERVICE: Token de servicio de Pago Fácil
 *   - PAGOFACIL_TOKEN_SECRET: Secret de Pago Fácil
 *   - PAGOFACIL_CALLBACK_URL: URL del webhook (ej: https://xxx.ngrok-free.app/webhook/pagofacil)
 */
public class PagoFacilService {

    private static final Dotenv dotenv;
    public static String TC_TOKEN_SERVICE;
    public static String TC_TOKEN_SECRET;
    public static String BASE_URL = "https://masterqr.pagofacil.com.bo/api/services/v2";
    public static String CALLBACK_URL;

    static {
        dotenv = Dotenv.configure().ignoreIfMissing().load();
        TC_TOKEN_SERVICE = dotenv.get("PAGOFACIL_TOKEN_SERVICE");
        TC_TOKEN_SECRET = dotenv.get("PAGOFACIL_TOKEN_SECRET");
        CALLBACK_URL = dotenv.get("PAGOFACIL_CALLBACK_URL");
    }

    private final HttpClient httpClient;
    private String bearerToken;

    public PagoFacilService() {
        this.httpClient = HttpClient.newHttpClient();
    }

    /**
     * Login contra la API de Pago Fácil.
     *
     * @return Bearer Token para autenticación
     * @throws Exception si falla el login
     */
    public String login() throws Exception {
        if (TC_TOKEN_SERVICE == null || TC_TOKEN_SECRET == null
                || TC_TOKEN_SERVICE.isBlank() || TC_TOKEN_SECRET.isBlank()) {
            throw new Exception("Variables de entorno PAGOFACIL_TOKEN_SERVICE y PAGOFACIL_TOKEN_SECRET deben estar configuradas.");
        }

        String auth = TC_TOKEN_SERVICE + ":" + TC_TOKEN_SECRET;
        String encodedAuth = Base64.getEncoder().encodeToString(auth.getBytes());

        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/login"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Basic " + encodedAuth)
                .header("tctokenservice", TC_TOKEN_SERVICE)
                .header("tctokensecret", TC_TOKEN_SECRET)
                .POST(HttpRequest.BodyPublishers.ofString("{}"))
                .build();

        HttpResponse<String> response = httpClient.send(request,
                HttpResponse.BodyHandlers.ofString());

        System.out.println("=== LOGIN RESPONSE ===");
        System.out.println("Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            throw new Exception("Login falló. Status: " + response.statusCode()
                    + ". Body: " + response.body());
        }

        JSONObject jsonResponse = new JSONObject(response.body());

        if (jsonResponse.has("token")) {
            this.bearerToken = jsonResponse.getString("token");
        } else if (jsonResponse.has("values") && jsonResponse.getJSONObject("values").has("accessToken")) {
            this.bearerToken = jsonResponse.getJSONObject("values").getString("accessToken");
        } else {
            throw new Exception("Login response no contiene 'token'.");
        }

        System.out.println("=== BEARER TOKEN OBTENIDO ===");
        return this.bearerToken;
    }

    /**
     * Genera un QR para una cuota de una matrícula.
     *
     * @param pagoData    Array con datos del pago en formato:
     *                    [id, cuota_id, estudiante_ci, metodo_pago_id,
     *                    monto, concepto, estado, referencia]
     * @param usuarioData Array con datos del estudiante en formato:
     *                    [ci, nombre, correo, rol, telefono]
     * @param cuotaId     ID de la cuota (MENSUALIDAD) que se paga
     * @return JSONObject con values.qrBase64 (String) y values.transactionId (int)
     * @throws Exception si falla la generación o el login
     */
    public JSONObject generarQR(String[] pagoData, String[] usuarioData, Integer cuotaId) throws Exception {
        if (CALLBACK_URL == null || CALLBACK_URL.isEmpty()) {
            throw new Exception("Variable de entorno PAGOFACIL_CALLBACK_URL debe estar configurada.");
        }

        if (this.bearerToken == null) {
            login();
        }

        int pagoId = Integer.parseInt(pagoData[0]);
        String montoStr = pagoData[4];
        double monto = Double.parseDouble(montoStr);
        String estudianteCi = pagoData[2];
        String estudianteNombre = usuarioData[1];
        String estudianteEmail = usuarioData[2];
        String telefono = usuarioData[4] != null ? usuarioData[4] : "70000000";

        // CUOTA-{cuotaId}-{pagoId}-{referencia}-{marca de tiempo}: WebHookServer valida la referencia.
        String paymentNumber = "CUOTA-" + cuotaId + "-" + pagoId + "-" + pagoData[7] + "-"
                + Long.toString(System.currentTimeMillis(), 36);

        JSONObject orderItem = new JSONObject();
        orderItem.put("serial", 1);
        orderItem.put("product", pagoData[5]);
        orderItem.put("quantity", 1);
        orderItem.put("price", monto);
        orderItem.put("discount", 0);
        orderItem.put("total", monto);

        JSONArray orderDetail = new JSONArray();
        orderDetail.put(orderItem);

        JSONObject body = new JSONObject();
        body.put("paymentMethod", 34);
        body.put("clientName", estudianteNombre);
        body.put("documentType", 1);
        body.put("documentId", estudianteCi);
        body.put("phoneNumber", telefono);
        body.put("email", estudianteEmail);
        body.put("paymentNumber", paymentNumber);
        body.put("amount", monto);
        body.put("currency", 2);
        body.put("clientCode", estudianteCi);
        body.put("callbackUrl", CALLBACK_URL);
        body.put("orderDetail", orderDetail);

        System.out.println("=== GENERATE QR REQUEST ===");
        System.out.println("URL: " + BASE_URL + "/generate-qr");
        System.out.println("Body: " + body.toString());

        HttpResponse<String> response = enviarGenerarQR(body);

        // El token venció: se renueva una vez.
        if (response.statusCode() == 401) {
            login();
            response = enviarGenerarQR(body);
        }

        System.out.println("=== GENERATE QR RESPONSE ===");
        System.out.println("Status: " + response.statusCode());

        if (response.statusCode() != 200) {
            throw new Exception("Generación QR falló. Status: " + response.statusCode()
                    + ". Body: " + response.body());
        }

        return new JSONObject(response.body());
    }

    private HttpResponse<String> enviarGenerarQR(JSONObject body) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(BASE_URL + "/generate-qr"))
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer " + bearerToken)
                .POST(HttpRequest.BodyPublishers.ofString(body.toString()))
                .build();

        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
