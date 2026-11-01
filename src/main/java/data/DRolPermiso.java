package data;

import java.sql.SQLException;

/**
 * DAO para la tabla ROL_PERMISO.
 */
public class DRolPermiso extends DBase {

    public void asignar(String rol, String accion) throws SQLException {
        ejecutar("INSERT INTO rol_permiso (id_rol, id_permiso) "
                + "SELECT r.id_rol, p.id_permiso FROM rol r, permiso p WHERE r.nombre = ? AND p.accion = ? "
                + "ON CONFLICT (id_rol, id_permiso) DO NOTHING", rol, accion);
    }

    public void quitar(String rol, String accion) throws SQLException {
        ejecutar("DELETE FROM rol_permiso rp USING rol r, permiso p "
                + "WHERE rp.id_rol = r.id_rol AND rp.id_permiso = p.id_permiso AND r.nombre = ? AND p.accion = ?",
                rol, accion);
    }
}
