package negocio;

import data.DMetodoPago;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Lógica de negocio para METODO_PAGO (CU4).
 */
public class NMetodoPago {

    private static final String QR_PAGO_FACIL = "QR Pago Facil";

    private final DMetodoPago dMetodoPago = new DMetodoPago();

    public ArrayList<String[]> list() throws SQLException {
        return dMetodoPago.list();
    }

    /** @return true si el método activo es efectivo; falla si no está disponible. */
    boolean esEfectivo(long idMetodo) throws SQLException {
        return nombre(idMetodo).toUpperCase(Locale.ROOT).contains("EFECTIVO");
    }

    public boolean esPagoFacil(long idMetodo) throws SQLException {
        return QR_PAGO_FACIL.equals(nombre(idMetodo));
    }

    long idPagoFacil() throws SQLException {
        List<String[]> metodo = dMetodoPago.getPorNombre(QR_PAGO_FACIL);
        if (metodo.isEmpty()) {
            throw new IllegalStateException("El pago por QR de Pago Fácil no está habilitado.");
        }
        return Long.parseLong(metodo.get(0)[0]);
    }

    private String nombre(long idMetodo) throws SQLException {
        List<String[]> metodo = dMetodoPago.get(idMetodo);
        if (metodo.isEmpty()) {
            throw new IllegalArgumentException("El método de pago " + idMetodo + " no está disponible. Consulta LISMET[].");
        }
        return metodo.get(0)[1];
    }
}
