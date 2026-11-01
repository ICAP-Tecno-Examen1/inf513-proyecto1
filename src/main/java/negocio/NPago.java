package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DMensualidad;
import data.DPago;
import java.math.BigDecimal;
import java.security.SecureRandom;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import org.json.JSONObject;
import postgresConecction.PagoFacilService;

/**
 * Lógica de negocio para PAGO (CU4). Un pago por QR de Pago Fácil se confirma con el
 * callback; el QR manual y el efectivo los verifica un responsable.
 */
public class NPago {

    /** Un QR generado, listo para enviarse por correo. */
    public record QrPago(long idPago, BigDecimal monto, String detalle, byte[] imagen, String transaccion,
            String expira, String correoEstudiante, String correoPagador) {
    }

    private static final BigDecimal CENTAVO = new BigDecimal("0.01");
    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("99999999.99");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final DPago dPago = new DPago();
    private final DMensualidad dMensualidad = new DMensualidad();
    private final NMetodoPago nMetodoPago = new NMetodoPago();
    private final NMatricula nMatricula = new NMatricula();
    private final NPagador nPagador = new NPagador();
    private final DUsuario dUsuario = new DUsuario();
    private final PagoFacilService pagoFacil = new PagoFacilService();

    /**
     * INSPAG["id_cuota","id_metodo","monto","referencia","comprobante_url",
     * "pagador_nombres","pagador_apellidos","pagador_tipo_doc","pagador_num_doc",
     * "pagador_telefono","pagador_correo","parentesco"]
     * Sin datos de pagador, se toma al propio estudiante como quien paga.
     */
    public List<String[]> registrar(List<String> params, String sender) throws SQLException {
        long idCuota = Validar.id(params, 0, "id_cuota");
        long idMetodo = Validar.id(params, 1, "id_metodo");
        BigDecimal monto = Validar.monto(Validar.texto(params, 2, "monto"), "monto", CENTAVO, MONTO_MAXIMO);
        String referencia = Validar.texto(params, 3, "referencia");
        String comprobante = Validar.opcional(params, 4);

        boolean personal = autorizarCuota(idCuota, sender);

        if (nMetodoPago.esPagoFacil(idMetodo)) {
            throw new IllegalArgumentException("Para pagar con Pago Fácil genera tu QR con PAGCUO[\"id_cuota\"].");
        }
        if (nMetodoPago.esEfectivo(idMetodo) && !personal) {
            throw new IllegalArgumentException("El pago en efectivo lo registra la Secretaría al recibir el dinero. "
                    + "Por tu cuenta puedes registrar pagos por QR.");
        }

        return dPago.get(dPago.registrar(idCuota, idMetodo, monto, referencia, comprobante,
                nPagador.leerDatos(params, 5), nPagador.leerParentesco(params, 5)));
    }

    /**
     * PAGCUO["id_cuota","pagador_nombres","pagador_apellidos","pagador_tipo_doc",
     * "pagador_num_doc","pagador_telefono","pagador_correo","parentesco"]: QR por el saldo.
     */
    public QrPago generarQrCuota(List<String> params, String sender) throws SQLException {
        return generarQr(Validar.id(params, 0, "id_cuota"), params, 1, sender);
    }

    /** PAGARQR["id_pago"]: vuelve a generar el QR de un pago de Pago Fácil aún pendiente. */
    public QrPago regenerarQr(List<String> params, String sender) throws SQLException {
        long idPago = Validar.id(params, 0, "id_pago");
        String[] pago = dPago.getParaQr(idPago);
        if (pago == null) {
            throw new IllegalArgumentException("El pago " + idPago + " no existe.");
        }
        if (!"PENDIENTE".equals(pago[3])) {
            throw new IllegalStateException("El pago " + idPago + " ya está " + pago[3] + ".");
        }
        if (pago[0] == null || !nMetodoPago.esPagoFacil(Long.parseLong(pago[4]))) {
            throw new IllegalArgumentException("El pago " + idPago + " no es de Pago Fácil.");
        }
        return generarQr(Long.parseLong(pago[0]), List.of(), 0, sender);
    }

    /** RESPAG["id_matricula","QR|FISICO", pagador...]. @return true si eligió QR. */
    public boolean eligioQr(List<String> params) {
        return "QR".equals(Validar.opcionDe(Validar.texto(params, 1, "forma de pago"), "forma de pago", "QR", "FISICO"));
    }

    /** RESPAG con QR: genera el QR de la siguiente cuota pendiente. */
    public QrPago generarQrSiguienteCuota(List<String> params, String sender) throws SQLException {
        String[] cuota = siguienteCuota(params, sender);
        return generarQr(Long.parseLong(cuota[0]), params, 2, sender);
    }

    /** @return { id_cuota, concepto, numero, vencimiento, monto_final, correo } de la siguiente cuota pendiente. */
    public String[] siguienteCuota(List<String> params, String sender) throws SQLException {
        long idMatricula = Validar.id(params, 0, "id_matricula");
        nMatricula.getAutorizada(idMatricula, sender);
        String[] cuota = dMensualidad.siguientePendiente(idMatricula);
        if (cuota == null) {
            throw new IllegalStateException("La matrícula no tiene cuotas pendientes.");
        }
        return cuota;
    }

    /** PEDPAG["id_matricula"]: el personal solicita al estudiante que elija cómo pagar. */
    public String[] pedirFormaDePago(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.PAGO_CONFIRMAR);
        return siguienteCuota(params, sender);
    }

    private QrPago generarQr(long idCuota, List<String> params, int inicioPagador, String sender) throws SQLException {
        autorizarCuota(idCuota, sender);
        String[] pagador = nPagador.leerDatos(params, inicioPagador);
        String parentesco = nPagador.leerParentesco(params, inicioPagador);
        long idMetodo = nMetodoPago.idPagoFacil();
        // { nombre, ci, telefono, correo, concepto, numero_cuota }
        String[] cuota = dMensualidad.getDatosCliente(idCuota);

        Long pendiente = dPago.pendientePorCuotaYMetodo(idCuota, idMetodo);
        boolean nuevo = pendiente == null;
        long idPago;
        BigDecimal monto;
        String referencia;
        if (nuevo) {
            monto = dPago.saldoDisponible(idCuota);
            if (monto.signum() <= 0) {
                throw new IllegalStateException("Esta cuota no tiene saldo por pagar: está pagada o tiene pagos "
                        + "sin verificar. Consulta LISPAG[].");
            }
            byte[] aleatorio = new byte[8];
            RANDOM.nextBytes(aleatorio);
            referencia = HexFormat.of().formatHex(aleatorio);
            idPago = dPago.registrar(idCuota, idMetodo, monto, referencia, null, pagador, parentesco);
        } else {
            idPago = pendiente;
            String[] pago = dPago.getParaQr(idPago);
            monto = new BigDecimal(pago[1]);
            referencia = pago[2];
        }

        String detalle = "ICAP " + cuota[4] + " - cuota " + cuota[5];
        String[] pagoData = { String.valueOf(idPago), String.valueOf(idCuota), cuota[1], String.valueOf(idMetodo),
                monto.toPlainString(), detalle, "PENDIENTE", referencia };
        String[] usuarioData = { cuota[1], cuota[0], cuota[3], "", cuota[2] };
        try {
            JSONObject qr = pagoFacil.generarQR(pagoData, usuarioData, (int) idCuota).getJSONObject("values");
            String base64 = qr.getString("qrBase64");
            byte[] imagen = Base64.getDecoder().decode(base64.substring(base64.indexOf(',') + 1));
            return new QrPago(idPago, monto, detalle, imagen, qr.optString("transactionId", "-"),
                    qr.optString("expirationDate", "-"), cuota[3], pagador != null ? pagador[5] : null);
        } catch (Exception ex) {
            System.err.println("Error generando QR de Pago Fácil: " + ex);
            // Sin QR no hay cobro posible: se libera el saldo que reservó el pago.
            if (nuevo) {
                dPago.rechazar(idPago);
            }
            throw new IllegalStateException("No se pudo generar el QR con Pago Fácil. Intenta nuevamente en unos "
                    + "minutos o paga en Secretaría.");
        }
    }

    /** @return true si el remitente es personal autorizado; falla si no puede pagar esta cuota. */
    private boolean autorizarCuota(long idCuota, String sender) throws SQLException {
        String duenio = dMensualidad.correoDe(idCuota);
        if (duenio == null) {
            throw new IllegalArgumentException("La cuota " + idCuota + " no existe. Consulta LISCUO[\"id_matricula\"].");
        }
        boolean personal = dUsuario.tienePermiso(sender, DPermiso.PAGO_CONFIRMAR);
        dUsuario.exigirPermiso(sender, DPermiso.PAGO_REGISTRAR);
        if (!duenio.equalsIgnoreCase(sender) && !personal) {
            throw new IllegalArgumentException("Acceso denegado: esa cuota no es tuya.");
        }
        return personal;
    }

    /** LISPAG[]: propios, o todos para el personal autorizado. LISPAG["ESTADO"]: filtro. */
    public ArrayList<String[]> list(List<String> params, String sender) throws SQLException {
        String filtro = Validar.opcional(params, 0);
        String estado = filtro == null ? null
                : Validar.opcionDe(filtro, "estado", "PENDIENTE", "CONFIRMADO", "RECHAZADO");
        return dUsuario.tienePermiso(sender, DPermiso.PAGO_VER) ? dPago.listTodos(estado) : dPago.listPorCorreo(sender, estado);
    }

    /** CONPAG["id"] @return { correo, numero_registro, estado matrícula, estado cuota } */
    public String[] confirmar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.PAGO_CONFIRMAR);
        return dPago.confirmar(Validar.id(params, 0, "id"));
    }

    /** RECPAG["id","motivo"] @return correo del estudiante */
    public String rechazar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.PAGO_CONFIRMAR);
        long id = Validar.id(params, 0, "id");
        Validar.texto(params, 1, "motivo");
        return dPago.rechazar(id);
    }
}
