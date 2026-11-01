package negocio;

import data.DMensualidad;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para MENSUALIDAD (CU4): el plan de pagos de una matrícula.
 */
public class NMensualidad {

    private final DMensualidad dMensualidad = new DMensualidad();
    private final NMatricula nMatricula = new NMatricula();

    /** LISCUO["id_matricula"]: plan de pagos, propio o para el personal autorizado. */
    public ArrayList<String[]> list(List<String> params, String sender) throws SQLException {
        long idMatricula = Validar.id(params, 0, "id_matricula");
        nMatricula.getAutorizada(idMatricula, sender);
        return dMensualidad.listPorMatricula(idMatricula);
    }

    /** Cuotas para avisar al estudiante cuando se aprueba su solicitud. */
    public ArrayList<String[]> listPorMatricula(long idMatricula) throws SQLException {
        return dMensualidad.listPorMatricula(idMatricula);
    }
}
