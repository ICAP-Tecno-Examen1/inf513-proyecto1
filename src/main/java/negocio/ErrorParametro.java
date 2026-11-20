package negocio;

/**
 * Un parámetro del comando está mal escrito (vacío, formato incorrecto, valor fuera de la lista).
 * EmailApp responde con el formato correcto y un ejemplo del comando.
 */
public class ErrorParametro extends IllegalArgumentException {

    public ErrorParametro(String message) {
        super(message);
    }
}
