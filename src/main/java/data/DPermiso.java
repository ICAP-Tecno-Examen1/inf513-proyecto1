package data;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * DAO para la tabla PERMISO.
 */
public class DPermiso extends DBase {

    public static final String USUARIO_GESTIONAR = "USUARIO_GESTIONAR";
    public static final String USUARIO_VER = "USUARIO_VER";
    public static final String ROL_GESTIONAR = "ROL_GESTIONAR";
    public static final String CATALOGO_GESTIONAR = "CATALOGO_GESTIONAR";
    public static final String PREINSCRIPCION_CREAR = "PREINSCRIPCION_CREAR";
    public static final String PREINSCRIPCION_REVISAR = "PREINSCRIPCION_REVISAR";
    public static final String INSCRIPCION_VER = "INSCRIPCION_VER";
    public static final String BECA_GESTIONAR = "BECA_GESTIONAR";
    public static final String BECA_ASIGNAR = "BECA_ASIGNAR";
    public static final String PAGO_REGISTRAR = "PAGO_REGISTRAR";
    public static final String PAGO_CONFIRMAR = "PAGO_CONFIRMAR";
    public static final String PAGO_VER = "PAGO_VER";

    public static final String[] HEADERS = { "id", "accion", "modulo", "descripcion" };

    public ArrayList<String[]> list() throws SQLException {
        return consultar("SELECT id_permiso, accion, modulo, descripcion FROM permiso ORDER BY modulo, accion");
    }

    public boolean existe(String accion) throws SQLException {
        return !consultar("SELECT 1 FROM permiso WHERE accion = ?", accion).isEmpty();
    }
}
