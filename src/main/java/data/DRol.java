package data;

import java.sql.SQLException;
import java.util.ArrayList;

/**
 * DAO para la tabla ROL.
 */
public class DRol extends DBase {

    public static final String[] HEADERS = { "id", "rol", "descripcion", "permisos" };

    public ArrayList<String[]> list() throws SQLException {
        return consultar("SELECT r.id_rol, r.nombre, r.descripcion, "
                + "(SELECT string_agg(p.accion, ', ' ORDER BY p.accion) FROM rol_permiso rp "
                + " JOIN permiso p ON p.id_permiso = rp.id_permiso WHERE rp.id_rol = r.id_rol) "
                + "FROM rol r ORDER BY r.id_rol");
    }

    public boolean existe(String nombre) throws SQLException {
        return !consultar("SELECT 1 FROM rol WHERE nombre = ?", nombre).isEmpty();
    }
}
