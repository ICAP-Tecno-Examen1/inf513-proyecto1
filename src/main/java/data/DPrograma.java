package data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla PROGRAMA.
 */
public class DPrograma extends DBase {

    public static final String[] HEADERS = { "id", "codigo", "nombre", "descripcion", "tipo", "estado" };

    private static final String SELECT = "SELECT id_programa, codigo, nombre, descripcion, tipo, estado FROM programa ";

    public ArrayList<String[]> list(boolean soloActivos) throws SQLException {
        return consultar(SELECT + (soloActivos ? "WHERE estado = 'ACTIVO' " : "") + "ORDER BY nombre");
    }

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "WHERE id_programa = ?", id);
    }

    public boolean existeCodigo(String codigo) throws SQLException {
        return !consultar("SELECT 1 FROM programa WHERE upper(codigo) = upper(?)", codigo).isEmpty();
    }

    public long save(String codigo, String nombre, String descripcion, String tipo) throws SQLException {
        try (Connection conn = conectar()) {
            return insertar(conn, "INSERT INTO programa (codigo, nombre, descripcion, tipo) "
                    + "VALUES (?, ?, ?, ?) RETURNING id_programa", codigo, nombre, descripcion, tipo);
        }
    }

    public void update(long id, String nombre, String descripcion, String tipo, String estado) throws SQLException {
        ejecutar("UPDATE programa SET nombre = COALESCE(?, nombre), descripcion = COALESCE(?, descripcion), "
                + "tipo = COALESCE(?, tipo), estado = COALESCE(?, estado) WHERE id_programa = ?",
                nombre, descripcion, tipo, estado, id);
    }
}
