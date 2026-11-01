package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DBeca;
import data.DBecaEstudiante;
import data.DMatricula;
import java.math.BigDecimal;
import java.sql.SQLException;
import java.util.List;

/**
 * Lógica de negocio para BECA_ESTUDIANTE (CU4): becas aplicadas a una matrícula.
 */
public class NBecaEstudiante {

    private final DBecaEstudiante dBecaEstudiante = new DBecaEstudiante();
    private final DBeca dBeca = new DBeca();
    private final DMatricula dMatricula = new DMatricula();
    private final DUsuario dUsuario = new DUsuario();

    /** INSBES["id_matricula","id_beca","porcentaje"]. Sin porcentaje se usa el de la beca. */
    public List<String[]> asignar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.BECA_ASIGNAR);
        long idMatricula = Validar.id(params, 0, "id_matricula");
        long idBeca = Validar.id(params, 1, "id_beca");
        List<String[]> beca = dBeca.get(idBeca);
        if (beca.isEmpty()) {
            throw new IllegalArgumentException("La beca " + idBeca + " no existe. Consulta LISBEC[].");
        }
        String indicado = Validar.opcional(params, 2);
        BigDecimal porcentaje = indicado != null
                ? Validar.monto(indicado, "porcentaje", BigDecimal.ZERO, NBeca.CIEN)
                : new BigDecimal(beca.get(0)[3]);
        dBecaEstudiante.asignar(idMatricula, idBeca, porcentaje);
        return dMatricula.get(idMatricula);
    }

    /** DELBES["id_matricula"] */
    public List<String[]> quitar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.BECA_ASIGNAR);
        long idMatricula = Validar.id(params, 0, "id_matricula");
        dBecaEstudiante.quitar(idMatricula);
        return dMatricula.get(idMatricula);
    }
}
