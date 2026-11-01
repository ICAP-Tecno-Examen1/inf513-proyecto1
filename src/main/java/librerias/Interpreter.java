package librerias;

import interfaces.ICasoUsoListener;
import librerias.analex.Token;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Clase Interpreter se encarga de interpretar los comandos entrantes con formato
 * AAAEEE["p1","p2",...] y enviar las acciones al manejador correspondiente.
 */
public class Interpreter implements Runnable {

    private static final Pattern FORMATO = Pattern.compile("^([A-Za-z]{3})([A-Za-z]{3})\\s*\\[(.*)\\]$");
    private static final Pattern AYUDA = Pattern.compile("^(?i)HELP(\\s*\\[\\s*\\])?$");
    // Comandos de pago dedicados: no siguen el formato acción + entidad.
    private static final Pattern PAGO_QR = Pattern.compile("^(?i)(PAGARQR|PEDPAG|PAGCUO|RESPAG)\\s*\\[(.*)\\]$");

    private ICasoUsoListener casoUsoListener;
    private String instruccion; // Comando recibido
    private final String sender; // Remitente del comando

    public Interpreter(String instruccion, String sender) {
        this.instruccion = instruccion;
        this.sender = sender;
    }

    public void setCasoUsoListener(ICasoUsoListener casoUsoListener) {
        this.casoUsoListener = casoUsoListener;
    }

    /** Une líneas y cambia las comillas tipográficas que insertan algunos clientes de correo. */
    public static String normalizar(String comando) {
        if (comando == null) {
            return "";
        }
        return comando.replaceAll("[\\r\\n]+", "").replace('“', '"').replace('”', '"').trim();
    }

    @Override
    public void run() {
        instruccion = normalizar(instruccion);

        if (AYUDA.matcher(instruccion).matches()) {
            casoUsoListener.help(new ParamsAction(this, sender, instruccion));
            return;
        }

        Matcher pagoQr = PAGO_QR.matcher(instruccion);
        if (pagoQr.matches()) {
            ParamsAction paramsPago = new ParamsAction(this, sender, instruccion);
            for (String param : parseParams(pagoQr.group(2))) {
                paramsPago.addParams(param);
            }
            System.out.println("Comando " + pagoQr.group(1).toUpperCase() + " de " + sender);
            dispatchPago(pagoQr.group(1).toUpperCase(), paramsPago);
            return;
        }

        Matcher matcher = FORMATO.matcher(instruccion);
        int action = matcher.matches() ? mapAction(matcher.group(1).toUpperCase()) : -1;
        int casoUso = action != -1 ? mapEntity(matcher.group(2).toUpperCase()) : -1;

        ParamsAction paramsAction = new ParamsAction(this, sender, instruccion);
        if (casoUso == -1) {
            paramsAction.setAction(Token.ERROR_COMMAND);
            casoUsoListener.error(paramsAction);
            return;
        }

        paramsAction.setAction(action);
        List<String> params = parseParams(matcher.group(3));
        // LISPER["*"] equivale a LISPER[]: listar todo.
        if (action == Token.GET && params.size() == 1 && "*".equals(params.get(0))) {
            params.clear();
        }
        for (String param : params) {
            paramsAction.addParams(param);
        }
        // No se registran los parámetros: pueden contener contraseñas.
        System.out.println("Comando " + matcher.group(1).toUpperCase() + matcher.group(2).toUpperCase()
                + " de " + sender);
        dispatchCasoUso(casoUso, paramsAction);
    }

    private int mapAction(String code) {
        switch (code) {
            case "INS":
                return Token.ADD;
            case "LIS":
                return Token.GET;
            case "MOD":
                return Token.MODIFY;
            case "DEL":
                return Token.DELETE;
            case "CAN":
                return Token.CANCEL;
            case "APR":
                return Token.APPROVE;
            case "REC":
                return Token.REJECT;
            case "CON":
                return Token.CONFIRM;
            default:
                return -1;
        }
    }

    private int mapEntity(String code) {
        switch (code) {
            case "USU":
                return Token.USUARIO;
            case "CLA":
                return Token.CLAVE;
            case "ROL":
                return Token.ROL;
            case "PER":
                return Token.PERMISO;
            case "PRO":
                return Token.PROGRAMA;
            case "MAL":
                return Token.MALLA;
            case "OFE":
                return Token.OFERTA;
            case "PRE":
                return Token.PREINSCRIPCION;
            case "MAT":
                return Token.MATRICULA;
            case "ARA":
                return Token.ARANCEL;
            case "CUO":
                return Token.MENSUALIDAD;
            case "BEC":
                return Token.BECA;
            case "BES":
                return Token.BECA_ESTUDIANTE;
            case "MET":
                return Token.METODO_PAGO;
            case "PAG":
                return Token.PAGO;
            default:
                return -1;
        }
    }

    /**
     * Extrae los parámetros de la cadena dentro de corchetes, eliminando las
     * comillas dobles de cada valor.
     * Ejemplo de entrada: "\"4715292\",\"Juan Carlos\""
     * Salida: ["4715292", "Juan Carlos"]
     */
    private List<String> parseParams(String raw) {
        List<String> result = new ArrayList<>();
        if (raw == null || raw.trim().isEmpty()) {
            return result;
        }
        // Separar por comas que NO estén dentro de comillas dobles
        String[] parts = raw.split(",(?=(?:[^\"]*\"[^\"]*\")*[^\"]*$)", -1);
        for (String part : parts) {
            result.add(part.trim().replaceAll("^\"|\"$", "").trim());
        }
        return result;
    }

    private void dispatchPago(String comando, ParamsAction paramsAction) {
        switch (comando) {
            case "PAGARQR":
                paramsAction.setAction(Token.PAGARQR);
                casoUsoListener.pagarQR(paramsAction);
                break;
            case "PEDPAG":
                paramsAction.setAction(Token.PEDPAG);
                casoUsoListener.pedpag(paramsAction);
                break;
            case "PAGCUO":
                paramsAction.setAction(Token.PAGCUO);
                casoUsoListener.pagcuo(paramsAction);
                break;
            default:
                paramsAction.setAction(Token.RESPAG);
                casoUsoListener.respag(paramsAction);
                break;
        }
    }

    private void dispatchCasoUso(int casoUso, ParamsAction paramsAction) {
        switch (casoUso) {
            case Token.USUARIO:
                casoUsoListener.usuario(paramsAction);
                break;
            case Token.CLAVE:
                casoUsoListener.clave(paramsAction);
                break;
            case Token.ROL:
                casoUsoListener.rol(paramsAction);
                break;
            case Token.PERMISO:
                casoUsoListener.permiso(paramsAction);
                break;
            case Token.PROGRAMA:
                casoUsoListener.programa(paramsAction);
                break;
            case Token.MALLA:
                casoUsoListener.malla(paramsAction);
                break;
            case Token.OFERTA:
                casoUsoListener.oferta(paramsAction);
                break;
            case Token.PREINSCRIPCION:
                casoUsoListener.preinscripcion(paramsAction);
                break;
            case Token.MATRICULA:
                casoUsoListener.matricula(paramsAction);
                break;
            case Token.ARANCEL:
                casoUsoListener.arancel(paramsAction);
                break;
            case Token.MENSUALIDAD:
                casoUsoListener.mensualidad(paramsAction);
                break;
            case Token.BECA:
                casoUsoListener.beca(paramsAction);
                break;
            case Token.BECA_ESTUDIANTE:
                casoUsoListener.becaEstudiante(paramsAction);
                break;
            case Token.METODO_PAGO:
                casoUsoListener.metodoPago(paramsAction);
                break;
            case Token.PAGO:
                casoUsoListener.pago(paramsAction);
                break;
            default:
                casoUsoListener.error(paramsAction);
                break;
        }
    }
}
