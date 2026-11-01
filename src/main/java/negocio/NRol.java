package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DRol;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Locale;

/**
 * Lógica de negocio para ROL (CU1). Roles del sistema: Director, Secretaria,
 * Coordinador Académico, Profesor y Estudiante.
 */
public class NRol {

    public static final String DIRECTOR = "Director";
    public static final String SECRETARIA = "Secretaria";
    public static final String COORDINADOR = "Coordinador Académico";
    public static final String PROFESOR = "Profesor";
    public static final String ESTUDIANTE = "Estudiante";

    private final DRol dRol = new DRol();
    private final DUsuario dUsuario = new DUsuario();

    public ArrayList<String[]> list(String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.ROL_GESTIONAR);
        return dRol.list();
    }

    /** @return el nombre del rol tal como está definido, o null si no es un rol del sistema. */
    static String buscar(String rol) {
        if (rol == null) {
            return null;
        }
        switch (rol.trim().toLowerCase(Locale.ROOT)) {
            case "director":
                return DIRECTOR;
            case "secretaria":
                return SECRETARIA;
            case "coordinador":
            case "coordinador academico":
            case "coordinador académico":
                return COORDINADOR;
            case "profesor":
                return PROFESOR;
            case "estudiante":
                return ESTUDIANTE;
            default:
                return null;
        }
    }

    /** Devuelve el nombre del rol tal como está definido, o falla si no existe. */
    String normalizar(String rol) throws SQLException {
        String nombre = buscar(rol);
        if (nombre == null || !dRol.existe(nombre)) {
            throw new IllegalArgumentException("El rol '" + rol + "' no existe. Use: Director, Secretaria, "
                    + "Coordinador, Profesor o Estudiante.");
        }
        return nombre;
    }
}
