package data;

import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.mindrot.jbcrypt.BCrypt;

/**
 * DAO para la tabla USUARIO. La clave se guarda solo como hash en password_hash.
 */
public class DUsuario extends DBase {

    public static final String[] HEADERS = { "id", "nombres", "apellidos", "ci", "tipo_doc", "correo", "username",
            "telefono", "direccion", "fecha_nacimiento", "estado", "fecha_registro", "roles" };

    private static final String SELECT = "SELECT u.id_usuario, u.nombres, u.apellidos, u.ci, u.tipo_documento, "
            + "u.correo, u.username, u.telefono, u.direccion, u.fecha_nacimiento, u.estado, "
            + "to_char(u.fecha_registro, 'YYYY-MM-DD HH24:MI'), "
            + "(SELECT string_agg(r.nombre, ', ' ORDER BY r.nombre) FROM usuario_rol ur "
            + " JOIN rol r ON r.id_rol = ur.id_rol WHERE ur.id_usuario = u.id_usuario AND ur.activo) "
            + "FROM usuario u ";

    public ArrayList<String[]> list() throws SQLException {
        return consultar(SELECT + "ORDER BY u.apellidos, u.nombres");
    }

    public List<String[]> getPorCi(String ci) throws SQLException {
        return consultar(SELECT + "WHERE u.ci = ?", ci);
    }

    public List<String[]> getPorCorreo(String correo) throws SQLException {
        return consultar(SELECT + "WHERE lower(u.correo) = lower(?)", correo);
    }

    public boolean existeCorreo(String correo) throws SQLException {
        return !getPorCorreo(correo).isEmpty();
    }

    public boolean existeCi(String ci) throws SQLException {
        return !getPorCi(ci).isEmpty();
    }

    public boolean existeUsername(String username) throws SQLException {
        return !consultar("SELECT 1 FROM usuario WHERE lower(username) = lower(?)", username).isEmpty();
    }

    public long idPorCorreo(String correo) throws SQLException {
        return Long.parseLong(getPorCorreo(correo).get(0)[0]);
    }

    /** @return { correo, password_hash, estado } o null si no existe. */
    public String[] getCredenciales(String correoOUsername) throws SQLException {
        List<String[]> filas = consultar("SELECT correo, password_hash, estado FROM usuario "
                + "WHERE lower(correo) = lower(?) OR lower(username) = lower(?)", correoOUsername, correoOUsername);
        return filas.isEmpty() ? null : filas.get(0);
    }

    public boolean tienePermiso(String correo, String accion) throws SQLException {
        return !consultar("SELECT 1 FROM usuario u "
                + "JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario AND ur.activo "
                + "JOIN rol_permiso rp ON rp.id_rol = ur.id_rol "
                + "JOIN permiso p ON p.id_permiso = rp.id_permiso "
                + "WHERE lower(u.correo) = lower(?) AND u.estado = 'ACTIVO' AND p.accion = ?",
                correo, accion).isEmpty();
    }

    /** Falla si el correo no tiene el permiso, que se obtiene de sus roles. */
    public void exigirPermiso(String correo, String accion) throws SQLException {
        if (!tienePermiso(correo, accion)) {
            throw new IllegalArgumentException("Acceso denegado: tu rol no permite realizar esta acción.");
        }
    }

    public boolean estaActivo(String correo) throws SQLException {
        String[] cuenta = getCredenciales(correo);
        return cuenta != null && "ACTIVO".equals(cuenta[2]);
    }

    public void save(String nombres, String apellidos, String ci, String tipoDocumento, String correo,
            String username, String password, String telefono, String direccion, Date fechaNacimiento,
            String rol) throws SQLException {
        String hash = BCrypt.hashpw(password, BCrypt.gensalt());
        enTransaccion(conn -> {
            long id = insertar(conn, "INSERT INTO usuario (nombres, apellidos, ci, tipo_documento, correo, "
                    + "username, password_hash, telefono, direccion, fecha_nacimiento) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_usuario",
                    nombres, apellidos, ci, tipoDocumento, correo, username, hash, telefono, direccion,
                    fechaNacimiento);
            ejecutar(conn, "INSERT INTO usuario_rol (id_usuario, id_rol) "
                    + "SELECT ?, id_rol FROM rol WHERE nombre = ?", id, rol);
            return id;
        });
    }

    public void update(String ci, String nombres, String apellidos, String telefono, String direccion,
            Date fechaNacimiento) throws SQLException {
        ejecutar("UPDATE usuario SET nombres = COALESCE(?, nombres), apellidos = COALESCE(?, apellidos), "
                + "telefono = COALESCE(?, telefono), direccion = COALESCE(?, direccion), "
                + "fecha_nacimiento = COALESCE(?, fecha_nacimiento) WHERE ci = ?",
                nombres, apellidos, telefono, direccion, fechaNacimiento, ci);
    }

    public void setEstado(String ci, String estado) throws SQLException {
        ejecutar("UPDATE usuario SET estado = ? WHERE ci = ?", estado, ci);
    }

    public void cambiarClave(String correo, String nueva) throws SQLException {
        ejecutar("UPDATE usuario SET password_hash = ? WHERE lower(correo) = lower(?)",
                BCrypt.hashpw(nueva, BCrypt.gensalt()), correo);
    }
}
