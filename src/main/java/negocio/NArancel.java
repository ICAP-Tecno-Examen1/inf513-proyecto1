package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DArancel;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para ARANCEL (CU4): el costo de una oferta.
 */
public class NArancel {

    private static final BigDecimal MONTO_MAXIMO = new BigDecimal("99999999.99");

    private final DArancel dArancel = new DArancel();
    private final NOferta nOferta = new NOferta();
    private final DUsuario dUsuario = new DUsuario();

    public ArrayList<String[]> list(List<String> params) throws SQLException {
        long idOferta = Validar.id(params, 0, "id_oferta");
        nOferta.get(idOferta);
        return dArancel.listPorOferta(idOferta);
    }

    /** INSARA["id_oferta","concepto","monto","tipo_cobro"]. En MENSUAL, monto es por mes. */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long idOferta = Validar.id(params, 0, "id_oferta");
        String[] oferta = nOferta.get(idOferta).get(0);
        String concepto = Validar.texto(params, 1, "concepto");
        BigDecimal monto = Validar.monto(Validar.texto(params, 2, "monto"), "monto", BigDecimal.ZERO, MONTO_MAXIMO);
        String tipo = Validar.opcionDe(Validar.texto(params, 3, "tipo_cobro"), "tipo_cobro", "UNICO", "MENSUAL");
        if ("MENSUAL".equals(tipo) && oferta[5] == null) {
            throw new IllegalArgumentException("Un arancel mensual requiere que la oferta tenga fecha de fin, "
                    + "para calcular la cantidad de cuotas.");
        }
        return dArancel.get(dArancel.save(idOferta, concepto, monto, tipo));
    }

    /** DELARA["id"]: las matrículas ya creadas conservan sus cuotas. */
    public List<String[]> desactivar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long id = Validar.id(params, 0, "id");
        if (dArancel.get(id).isEmpty()) {
            throw new IllegalArgumentException("El arancel " + id + " no existe.");
        }
        dArancel.setEstado(id, "INACTIVO");
        return dArancel.get(id);
    }
}
