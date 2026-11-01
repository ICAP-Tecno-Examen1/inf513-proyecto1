package negocio;

import data.DUsuario;
import data.DPermiso;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Lógica de negocio para PERMISO (CU1).
 */
public class NPermiso {

    private final DPermiso dPermiso = new DPermiso();
    private final DUsuario dUsuario = new DUsuario();

    public ArrayList<String[]> list(String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.ROL_GESTIONAR);
        return dPermiso.list();
    }

    /** Devuelve la acción del permiso en mayúsculas, o falla si no existe. */
    String validar(String accion) throws SQLException {
        String normalizada = accion.toUpperCase(Locale.ROOT);
        if (!dPermiso.existe(normalizada)) {
            throw new IllegalArgumentException("El permiso '" + accion + "' no existe. Consulta LISPER[].");
        }
        return normalizada;
    }
}
