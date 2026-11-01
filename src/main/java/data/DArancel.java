package data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla ARANCEL.
 */
public class DArancel extends DBase {

    public static final String[] HEADERS = { "id", "id_oferta", "concepto", "monto_Bs", "tipo_cobro", "estado" };

    private static final String SELECT = "SELECT id_arancel, id_oferta, concepto, monto, tipo_cobro, estado FROM arancel ";

    public ArrayList<String[]> listPorOferta(long idOferta) throws SQLException {
        return consultar(SELECT + "WHERE id_oferta = ? ORDER BY id_arancel", idOferta);
    }

    public List<String[]> get(long idArancel) throws SQLException {
        return consultar(SELECT + "WHERE id_arancel = ?", idArancel);
    }

    public long save(long idOferta, String concepto, BigDecimal monto, String tipoCobro) throws SQLException {
        try (Connection conn = conectar()) {
            return insertar(conn, "INSERT INTO arancel (id_oferta, concepto, monto, tipo_cobro) "
                    + "VALUES (?, ?, ?, ?) RETURNING id_arancel", idOferta, concepto, monto, tipoCobro);
        }
    }

    public void setEstado(long idArancel, String estado) throws SQLException {
        ejecutar("UPDATE arancel SET estado = ? WHERE id_arancel = ?", estado, idArancel);
    }

    /** @return filas { id_arancel, monto, tipo_cobro } */
    static List<String[]> listActivos(Connection conn, long idOferta) throws SQLException {
        return consultar(conn, "SELECT id_arancel, monto, tipo_cobro FROM arancel "
                + "WHERE id_oferta = ? AND estado = 'ACTIVO' ORDER BY id_arancel", idOferta);
    }
}
