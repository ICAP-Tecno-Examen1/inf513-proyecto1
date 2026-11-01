package librerias.analex;

/**
 * Constantes de casos de uso y acciones que comparten Interpreter y EmailApp.
 */
public class Token {

    // Casos de uso
    public static final int HELP = 100;
    public static final int USUARIO = 102;
    public static final int CLAVE = 103;
    public static final int ROL = 104;
    public static final int PERMISO = 105;
    public static final int PROGRAMA = 110;
    public static final int MALLA = 111;
    public static final int OFERTA = 112;
    public static final int PREINSCRIPCION = 120;
    public static final int MATRICULA = 121;
    public static final int ARANCEL = 130;
    public static final int MENSUALIDAD = 131;
    public static final int BECA = 132;
    public static final int BECA_ESTUDIANTE = 133;
    public static final int METODO_PAGO = 134;
    public static final int PAGO = 135;
    public static final int PAGARQR = 136;
    public static final int PEDPAG = 137;
    public static final int PAGCUO = 138;
    public static final int RESPAG = 139;

    // Acciones
    public static final int ADD = 200;
    public static final int DELETE = 201;
    public static final int MODIFY = 202;
    public static final int GET = 203;
    public static final int CANCEL = 205;
    public static final int APPROVE = 206;
    public static final int REJECT = 207;
    public static final int CONFIRM = 208;

    // Errores
    public static final int ERROR_COMMAND = 300;

    private Token() {
    }
}
