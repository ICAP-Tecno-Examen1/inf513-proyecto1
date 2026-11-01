package data;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para METODO_PAGO.
 */
public class DMetodoPago extends DBase {

    public static final String[] HEADERS = { "id", "metodo", "instrucciones" };

    private static final String SELECT = "SELECT id_metodo_pago, nombre, descripcion FROM metodo_pago WHERE activo ";

    public ArrayList<String[]> list() throws SQLException {
        return consultar(SELECT + "ORDER BY id_metodo_pago");
    }

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "AND id_metodo_pago = ?", id);
    }

    public List<String[]> getPorNombre(String nombre) throws SQLException {
        return consultar(SELECT + "AND nombre = ?", nombre);
    }
}
