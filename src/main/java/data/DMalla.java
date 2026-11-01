package data;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla MALLA_CURRICULAR.
 */
public class DMalla extends DBase {

    public static final String[] HEADERS = { "id", "id_programa", "programa", "version", "estado",
            "fecha_vigencia", "horas_totales" };

    private static final String SELECT = "SELECT m.id_malla, m.id_programa, p.nombre, m.version, m.estado, "
            + "m.fecha_vigencia, m.horas_totales FROM malla_curricular m "
            + "JOIN programa p ON p.id_programa = m.id_programa ";

    public ArrayList<String[]> listPorPrograma(long idPrograma) throws SQLException {
        return consultar(SELECT + "WHERE m.id_programa = ? ORDER BY m.id_malla", idPrograma);
    }

    public List<String[]> get(long idMalla) throws SQLException {
        return consultar(SELECT + "WHERE m.id_malla = ?", idMalla);
    }

    public long save(long idPrograma, String version, int horas, Date fechaVigencia) throws SQLException {
        try (Connection conn = conectar()) {
            return insertar(conn, "INSERT INTO malla_curricular (id_programa, version, horas_totales, fecha_vigencia) "
                    + "VALUES (?, ?, ?, ?) RETURNING id_malla", idPrograma, version, horas, fechaVigencia);
        }
    }
}
