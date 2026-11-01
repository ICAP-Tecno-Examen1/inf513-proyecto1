package data;

import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla OFERTA_ACADEMICA.
 * Un arancel MENSUAL define el monto por mes; la cantidad de cuotas son los meses
 * que dura la oferta.
 */
public class DOferta extends DBase {

    public static final String[] HEADERS = { "id", "codigo", "programa", "modalidad", "inicio", "fin", "horas",
            "gestion", "cupos_libres", "estado", "tipo_cobro", "cuotas", "costo_total_Bs" };

    private static final String AGE = "age(o.fecha_fin + 1, o.fecha_inicio)";

    /** Meses de la oferta, redondeando hacia arriba. Requiere el alias "o". */
    private static final String MESES = "GREATEST(1, COALESCE((EXTRACT(YEAR FROM " + AGE + ") * 12 + EXTRACT(MONTH FROM " + AGE
            + ") + CASE WHEN EXTRACT(DAY FROM " + AGE + ") > 0 THEN 1 ELSE 0 END)::int, 1))";

    private static final String SELECT = "SELECT o.id_oferta, o.codigo, p.nombre, o.modalidad, o.fecha_inicio, "
            + "o.fecha_fin, m.horas_totales, o.gestion, "
            + "o.cupo_maximo - (SELECT count(*) FROM matricula x WHERE x.id_oferta = o.id_oferta "
            + "  AND x.estado <> 'CANCELADA'), o.estado, "
            + "CASE WHEN EXISTS (SELECT 1 FROM arancel a WHERE a.id_oferta = o.id_oferta AND a.estado = 'ACTIVO' "
            + "  AND a.tipo_cobro = 'MENSUAL') THEN 'MENSUAL' ELSE 'UNICO' END, "
            + "CASE WHEN EXISTS (SELECT 1 FROM arancel a WHERE a.id_oferta = o.id_oferta AND a.estado = 'ACTIVO' "
            + "  AND a.tipo_cobro = 'MENSUAL') THEN " + MESES + " ELSE 1 END, "
            + "(SELECT COALESCE(SUM(CASE WHEN a.tipo_cobro = 'MENSUAL' THEN a.monto * " + MESES
            + "  ELSE a.monto END), 0) FROM arancel a WHERE a.id_oferta = o.id_oferta AND a.estado = 'ACTIVO') "
            + "FROM oferta_academica o JOIN malla_curricular m ON m.id_malla = o.id_malla "
            + "JOIN programa p ON p.id_programa = m.id_programa ";

    public ArrayList<String[]> listAbiertas() throws SQLException {
        return consultar(SELECT + "WHERE o.estado = 'ABIERTA' AND p.estado = 'ACTIVO' ORDER BY o.fecha_inicio");
    }

    public ArrayList<String[]> listTodas() throws SQLException {
        return consultar(SELECT + "ORDER BY o.fecha_inicio DESC");
    }

    public ArrayList<String[]> listAbiertasPorPrograma(long idPrograma) throws SQLException {
        return consultar(SELECT + "WHERE o.estado = 'ABIERTA' AND p.id_programa = ? ORDER BY o.fecha_inicio",
                idPrograma);
    }

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "WHERE o.id_oferta = ?", id);
    }

    public boolean existeCodigo(String codigo) throws SQLException {
        return !consultar("SELECT 1 FROM oferta_academica WHERE upper(codigo) = upper(?)", codigo).isEmpty();
    }

    public long save(String codigo, long idMalla, String modalidad, Date inicio, Date fin, int cupoMaximo,
            int cupoMinimo, int gestion) throws SQLException {
        try (Connection conn = conectar()) {
            return insertar(conn, "INSERT INTO oferta_academica (codigo, id_malla, modalidad, fecha_inicio, fecha_fin, "
                    + "cupo_maximo, cupo_minimo, gestion) VALUES (?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_oferta",
                    codigo, idMalla, modalidad, inicio, fin, cupoMaximo, cupoMinimo, gestion);
        }
    }

    public void update(long id, String estado, Date fin, Integer cupoMaximo) throws SQLException {
        ejecutar("UPDATE oferta_academica SET estado = COALESCE(?, estado), fecha_fin = COALESCE(?, fecha_fin), "
                + "cupo_maximo = COALESCE(?, cupo_maximo) WHERE id_oferta = ?", estado, fin, cupoMaximo, id);
    }

    /**
     * Bloquea la oferta: evita sobrepasar el cupo con aprobaciones simultáneas.
     * @return { estado, fecha_inicio, gestion, cupo_maximo, meses }
     */
    static String[] bloquear(Connection conn, long idOferta) throws SQLException {
        return consultar(conn, "SELECT o.estado, o.fecha_inicio, o.gestion, o.cupo_maximo, " + MESES
                + " FROM oferta_academica o WHERE o.id_oferta = ? FOR UPDATE", idOferta).get(0);
    }
}
