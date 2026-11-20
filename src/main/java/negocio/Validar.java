package negocio;

import java.math.BigDecimal;
import java.sql.Date;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.util.List;

/**
 * Lectura y validación de los parámetros de un comando.
 */
final class Validar {

    private Validar() {
    }

    static String texto(List<String> params, int pos, String campo) {
        String valor = opcional(params, pos);
        if (valor == null) {
            throw new ErrorParametro("Error en el campo " + (pos + 1) + " (" + campo
                    + "): falta un valor obligatorio.");
        }
        return valor;
    }

    /** Devuelve null si el parámetro no vino, está vacío o es "-". */
    static String opcional(List<String> params, int pos) {
        if (params == null || pos >= params.size()) {
            return null;
        }
        String valor = params.get(pos).trim();
        return valor.isEmpty() || "-".equals(valor) ? null : valor;
    }

    static long id(List<String> params, int pos, String campo) {
        String valor = texto(params, pos, campo);
        try {
            long id = Long.parseLong(valor);
            if (id > 0) {
                return id;
            }
        } catch (NumberFormatException ignorado) {
            // se informa abajo
        }
        throw new ErrorParametro("El campo " + campo + " debe ser un número entero positivo.");
    }

    static int entero(String valor, String campo, int minimo) {
        try {
            int n = Integer.parseInt(valor);
            if (n >= minimo) {
                return n;
            }
        } catch (NumberFormatException ignorado) {
            // se informa abajo
        }
        throw new ErrorParametro("El campo " + campo + " debe ser un entero mayor o igual a " + minimo + ".");
    }

    static BigDecimal monto(String valor, String campo, BigDecimal minimo, BigDecimal maximo) {
        try {
            BigDecimal n = new BigDecimal(valor);
            if (n.scale() <= 2 && n.compareTo(minimo) >= 0 && n.compareTo(maximo) <= 0) {
                return n;
            }
        } catch (NumberFormatException ignorado) {
            // se informa abajo
        }
        throw new ErrorParametro("El campo " + campo + " debe ser un número entre " + minimo + " y "
                + maximo + ", con hasta 2 decimales.");
    }

    /** Fecha opcional en formato AAAA-MM-DD. */
    static Date fecha(String valor, String campo) {
        if (valor == null) {
            return null;
        }
        try {
            return Date.valueOf(LocalDate.parse(valor));
        } catch (DateTimeException e) {
            throw new ErrorParametro("El campo " + campo + " debe tener el formato AAAA-MM-DD.");
        }
    }

    /** Valor de una lista cerrada, en mayúsculas. */
    static String opcionDe(String valor, String campo, String... opciones) {
        String normalizado = valor.toUpperCase();
        for (String opcion : opciones) {
            if (opcion.equals(normalizado)) {
                return opcion;
            }
        }
        throw new ErrorParametro("El campo " + campo + " debe ser uno de: "
                + String.join(", ", opciones) + ".");
    }

    /** Error de un campo indicando su posición (1 = primer parámetro que escribe el usuario). */
    static IllegalArgumentException errorCampo(int posicion, String campo, String valor, String regla) {
        return new ErrorParametro("Error en el campo " + posicion + " (" + campo + "), valor '"
                + valor + "': " + regla + ".");
    }

    /** Solo letras (con acentos y ñ) y espacios. */
    static void soloLetras(String valor, int posicion, String campo) {
        if (!valor.matches("[\\p{L} ]+")) {
            throw errorCampo(posicion, campo, valor, "solo puede contener letras y espacios");
        }
    }

    /** Solo números; un valor opcional ausente (null) es válido. */
    static void digitos(String valor, int posicion, String campo) {
        if (valor != null && !valor.matches("[0-9]+")) {
            throw errorCampo(posicion, campo, valor, "solo puede contener números");
        }
    }

    /** CI: solo números. PASAPORTE: letras y números. */
    static void documento(String numero, String tipo, int posicion) {
        if ("CI".equals(tipo) && !numero.matches("[0-9]+")) {
            throw errorCampo(posicion, "ci", numero, "para CI solo puede contener números");
        }
        if ("PASAPORTE".equals(tipo) && !numero.matches("[A-Za-z0-9]+")) {
            throw errorCampo(posicion, "ci", numero, "para PASAPORTE solo puede contener letras y números");
        }
    }

    static boolean esCorreo(String correo) {
        return correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    }

    static void correo(String correo) {
        if (!esCorreo(correo)) {
            throw new ErrorParametro("El correo '" + correo + "' no tiene un formato válido.");
        }
    }

    static void clave(String clave) {
        if (clave.length() < 8) {
            throw new ErrorParametro("La contraseña debe tener al menos 8 caracteres.");
        }
    }
}
