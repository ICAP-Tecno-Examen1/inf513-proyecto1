package data;

import java.sql.SQLException;

/**
 * DAO para la tabla USUARIO_ROL.
 */
public class DUsuarioRol extends DBase {

    public boolean tieneRol(String ci, String rol) throws SQLException {
        return !consultar("SELECT 1 FROM usuario_rol ur JOIN usuario u ON u.id_usuario = ur.id_usuario "
                + "JOIN rol r ON r.id_rol = ur.id_rol WHERE u.ci = ? AND r.nombre = ? AND ur.activo", ci, rol)
                .isEmpty();
    }

    public int contarUsuariosActivosConRol(String rol) throws SQLException {
        return Integer.parseInt(consultar("SELECT count(DISTINCT u.id_usuario) FROM usuario_rol ur "
                + "JOIN usuario u ON u.id_usuario = ur.id_usuario JOIN rol r ON r.id_rol = ur.id_rol "
                + "WHERE r.nombre = ? AND ur.activo AND u.estado = 'ACTIVO'", rol).get(0)[0]);
    }

    public void asignar(String ci, String rol) throws SQLException {
        enTransaccion(conn -> {
            int reactivados = ejecutar(conn, "UPDATE usuario_rol ur SET activo = TRUE, fecha_asignacion = NOW() "
                    + "FROM usuario u, rol r WHERE ur.id_usuario = u.id_usuario AND ur.id_rol = r.id_rol "
                    + "AND u.ci = ? AND r.nombre = ?", ci, rol);
            if (reactivados == 0) {
                ejecutar(conn, "INSERT INTO usuario_rol (id_usuario, id_rol) "
                        + "SELECT u.id_usuario, r.id_rol FROM usuario u, rol r WHERE u.ci = ? AND r.nombre = ?",
                        ci, rol);
            }
            return reactivados;
        });
    }

    public void quitar(String ci, String rol) throws SQLException {
        ejecutar("UPDATE usuario_rol ur SET activo = FALSE FROM usuario u, rol r "
                + "WHERE ur.id_usuario = u.id_usuario AND ur.id_rol = r.id_rol AND u.ci = ? AND r.nombre = ?",
                ci, rol);
    }
}
