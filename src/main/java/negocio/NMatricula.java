package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DMatricula;
import java.sql.SQLException;
import java.util.List;

/**
 * Lógica de negocio para MATRICULA (CU3).
 */
public class NMatricula {

    private static final int CORREO = 4;

    private final DMatricula dMatricula = new DMatricula();
    private final DUsuario dUsuario = new DUsuario();

    /** LISMAT[]: propias, o todas para el personal autorizado. LISMAT["id"]: una. */
    public List<String[]> list(List<String> params, String sender) throws SQLException {
        if (Validar.opcional(params, 0) != null) {
            return getAutorizada(Validar.id(params, 0, "id"), sender);
        }
        return dUsuario.tienePermiso(sender, DPermiso.INSCRIPCION_VER) ? dMatricula.listTodas()
                : dMatricula.listPorCorreo(sender);
    }

    /** La matrícula, si es del remitente o este tiene permiso para verla. */
    List<String[]> getAutorizada(long id, String sender) throws SQLException {
        List<String[]> matricula = dMatricula.get(id);
        if (matricula.isEmpty()) {
            throw new IllegalArgumentException("La matrícula " + id + " no existe.");
        }
        if (!matricula.get(0)[CORREO].equalsIgnoreCase(sender)) {
            dUsuario.exigirPermiso(sender, DPermiso.INSCRIPCION_VER);
        }
        return matricula;
    }
}
