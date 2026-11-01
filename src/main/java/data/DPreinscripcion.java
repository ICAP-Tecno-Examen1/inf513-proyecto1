package data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla PREINSCRIPCION.
 */
public class DPreinscripcion extends DBase {

    public static final String[] HEADERS = { "id", "estudiante", "ci", "correo", "id_oferta", "oferta", "programa",
            "fecha", "estado", "observacion" };

    private static final String SELECT = "SELECT pr.id_preinscripcion, u.nombres || ' ' || u.apellidos, u.ci, u.correo, "
            + "o.id_oferta, o.codigo, p.nombre, pr.fecha, pr.estado, pr.observacion FROM preinscripcion pr "
            + "JOIN usuario u ON u.id_usuario = pr.id_usuario "
            + "JOIN oferta_academica o ON o.id_oferta = pr.id_oferta "
            + "JOIN malla_curricular m ON m.id_malla = o.id_malla "
            + "JOIN programa p ON p.id_programa = m.id_programa ";

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "WHERE pr.id_preinscripcion = ?", id);
    }

    public ArrayList<String[]> listPorCorreo(String correo) throws SQLException {
        return consultar(SELECT + "WHERE lower(u.correo) = lower(?) ORDER BY pr.id_preinscripcion DESC", correo);
    }

    public ArrayList<String[]> listTodas(String estado) throws SQLException {
        return estado == null
                ? consultar(SELECT + "ORDER BY pr.id_preinscripcion DESC")
                : consultar(SELECT + "WHERE pr.estado = ? ORDER BY pr.id_preinscripcion DESC", estado);
    }

    public boolean tieneVigente(long idUsuario, long idOferta) throws SQLException {
        return !consultar("SELECT 1 FROM preinscripcion WHERE id_usuario = ? AND id_oferta = ? "
                + "AND estado IN ('PENDIENTE', 'APROBADA')", idUsuario, idOferta).isEmpty();
    }

    public long save(long idUsuario, long idOferta, String observacion) throws SQLException {
        try (Connection conn = conectar()) {
            return insertar(conn, "INSERT INTO preinscripcion (id_usuario, id_oferta, observacion) "
                    + "VALUES (?, ?, ?) RETURNING id_preinscripcion", idUsuario, idOferta, observacion);
        }
    }

    /** @return false si la solicitud ya no estaba pendiente. */
    public boolean rechazar(long id, String motivo) throws SQLException {
        return ejecutar("UPDATE preinscripcion SET estado = 'RECHAZADA', observacion = ? "
                + "WHERE id_preinscripcion = ? AND estado = 'PENDIENTE'", motivo, id) > 0;
    }

    /** @return { id_usuario, id_oferta, estado } */
    static String[] bloquear(Connection conn, long id) throws SQLException {
        List<String[]> filas = consultar(conn, "SELECT id_usuario, id_oferta, estado FROM preinscripcion "
                + "WHERE id_preinscripcion = ? FOR UPDATE", id);
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("La preinscripción " + id + " no existe.");
        }
        return filas.get(0);
    }

    static void marcarAprobada(Connection conn, long id, String observacion) throws SQLException {
        ejecutar(conn, "UPDATE preinscripcion SET estado = 'APROBADA', observacion = COALESCE(?, observacion) "
                + "WHERE id_preinscripcion = ?", observacion, id);
    }

    static void marcarCancelada(Connection conn, long id) throws SQLException {
        ejecutar(conn, "UPDATE preinscripcion SET estado = 'CANCELADA' WHERE id_preinscripcion = ?", id);
    }
}
