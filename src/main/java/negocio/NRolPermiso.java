package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DRol;
import data.DRolPermiso;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para ROL_PERMISO: permisos de cada rol (CU1).
 */
public class NRolPermiso {

    private final DRolPermiso dRolPermiso = new DRolPermiso();
    private final DRol dRol = new DRol();
    private final NRol nRol = new NRol();
    private final NPermiso nPermiso = new NPermiso();
    private final DUsuario dUsuario = new DUsuario();

    /** INSPER["rol","accion"] */
    public ArrayList<String[]> asignar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.ROL_GESTIONAR);
        String rol = nRol.normalizar(Validar.texto(params, 0, "rol"));
        dRolPermiso.asignar(rol, nPermiso.validar(Validar.texto(params, 1, "accion")));
        return dRol.list();
    }

    /** DELPER["rol","accion"]. El Director conserva siempre la gestión de roles. */
    public ArrayList<String[]> quitar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.ROL_GESTIONAR);
        String rol = nRol.normalizar(Validar.texto(params, 0, "rol"));
        String accion = nPermiso.validar(Validar.texto(params, 1, "accion"));
        if (NRol.DIRECTOR.equals(rol) && DPermiso.ROL_GESTIONAR.equals(accion)) {
            throw new IllegalArgumentException("El Director no puede perder el permiso de gestionar roles.");
        }
        dRolPermiso.quitar(rol, accion);
        return dRol.list();
    }
}
