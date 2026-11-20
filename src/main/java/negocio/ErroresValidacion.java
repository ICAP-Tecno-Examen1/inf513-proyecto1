package negocio;

import java.util.List;

/**
 * Varios errores de validación de un mismo comando. Se informan todos juntos
 * y se adjunta el formato correcto.
 */
public class ErroresValidacion extends IllegalArgumentException {

    private final List<String> errores;
    private final String formato;

    public ErroresValidacion(List<String> errores, String formato) {
        super(String.join(" ", errores));
        this.errores = List.copyOf(errores);
        this.formato = formato;
    }

    public List<String> getErrores() {
        return errores;
    }

    public String getFormato() {
        return formato;
    }
}
