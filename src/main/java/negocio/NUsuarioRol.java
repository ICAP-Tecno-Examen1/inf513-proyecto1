package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DUsuarioRol;
import java.sql.SQLException;
import java.util.List;

/**
 * Lógica de negocio para USUARIO_ROL: asignación de roles (CU1).
 */
public class NUsuarioRol {

    private final DUsuarioRol dUsuarioRol = new DUsuarioRol();
    private final DUsuario dUsuario = new DUsuario();
    private final NRol nRol = new NRol();

    /** INSROL["ci","rol"] */
    public List<String[]> asignar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.ROL_GESTIONAR);
        String ci = Validar.texto(params, 0, "ci");
        exigirUsuario(ci);
        dUsuarioRol.asignar(ci, nRol.normalizar(Validar.texto(params, 1, "rol")));
        return dUsuario.getPorCi(ci);
    }

    /** DELROL["ci","rol"] */
    public List<String[]> quitar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.ROL_GESTIONAR);
        String ci = Validar.texto(params, 0, "ci");
        String rol = nRol.normalizar(Validar.texto(params, 1, "rol"));
        exigirUsuario(ci);
        if (NRol.DIRECTOR.equals(rol)) {
            exigirOtroDirector(ci);
        }
        dUsuarioRol.quitar(ci, rol);
        return dUsuario.getPorCi(ci);
    }

    /** Impide dejar el sistema sin un Director activo. */
    void exigirOtroDirector(String ci) throws SQLException {
        if (dUsuarioRol.tieneRol(ci, NRol.DIRECTOR) && dUsuarioRol.contarUsuariosActivosConRol(NRol.DIRECTOR) <= 1) {
            throw new IllegalArgumentException("Debe quedar al menos un Director activo en el sistema.");
        }
    }

    private void exigirUsuario(String ci) throws SQLException {
        if (!dUsuario.existeCi(ci)) {
            throw new IllegalArgumentException("No existe un usuario con el documento '" + ci + "'.");
        }
    }
}
