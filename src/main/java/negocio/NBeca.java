package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DBeca;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para BECA (CU4).
 */
public class NBeca {

    static final BigDecimal CIEN = new BigDecimal("100");

    private final DBeca dBeca = new DBeca();
    private final DUsuario dUsuario = new DUsuario();

    public ArrayList<String[]> list() throws SQLException {
        return dBeca.list();
    }

    /** INSBEC["nombre","descripcion","porcentaje","institucion"] */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.BECA_GESTIONAR);
        String nombre = Validar.texto(params, 0, "nombre");
        BigDecimal porcentaje = Validar.monto(Validar.texto(params, 2, "porcentaje"), "porcentaje",
                BigDecimal.ZERO, CIEN);
        return dBeca.get(dBeca.save(nombre, Validar.opcional(params, 1), porcentaje, Validar.opcional(params, 3)));
    }
}
