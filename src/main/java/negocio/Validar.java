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
            throw new IllegalArgumentException("Falta el campo obligatorio: " + campo + ".");
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
        throw new IllegalArgumentException("El campo " + campo + " debe ser un número entero positivo.");
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
        throw new IllegalArgumentException("El campo " + campo + " debe ser un entero mayor o igual a " + minimo + ".");
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
        throw new IllegalArgumentException("El campo " + campo + " debe ser un número entre " + minimo + " y "
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
            throw new IllegalArgumentException("El campo " + campo + " debe tener el formato AAAA-MM-DD.");
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
        throw new IllegalArgumentException("El campo " + campo + " debe ser uno de: "
                + String.join(", ", opciones) + ".");
    }

    static void correo(String correo) {
        if (!correo.matches("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")) {
            throw new IllegalArgumentException("El correo '" + correo + "' no tiene un formato válido.");
        }
    }

    static void clave(String clave) {
        if (clave.length() < 8) {
            throw new IllegalArgumentException("La contraseña debe tener al menos 8 caracteres.");
        }
    }
}
