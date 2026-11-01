package data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para BECA.
 */
public class DBeca extends DBase {

    public static final String[] HEADERS = { "id", "nombre", "descripcion", "porcentaje", "institucion" };

    private static final String SELECT = "SELECT id_beca, nombre, descripcion, porcentaje, institucion_financiadora "
            + "FROM beca ";

    public ArrayList<String[]> list() throws SQLException {
        return consultar(SELECT + "ORDER BY nombre");
    }

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "WHERE id_beca = ?", id);
    }

    public long save(String nombre, String descripcion, BigDecimal porcentaje, String institucion) throws SQLException {
        try (Connection conn = conectar()) {
            return insertar(conn, "INSERT INTO beca (nombre, descripcion, porcentaje, institucion_financiadora) "
                    + "VALUES (?, ?, ?, ?) RETURNING id_beca", nombre, descripcion, porcentaje, institucion);
        }
    }
}
