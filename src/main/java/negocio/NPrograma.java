package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DPrograma;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para PROGRAMA (CU2). El catálogo se puede consultar sin estar registrado.
 */
public class NPrograma {

    private final DPrograma dPrograma = new DPrograma();
    private final DUsuario dUsuario = new DUsuario();

    public ArrayList<String[]> list(String sender) throws SQLException {
        return dPrograma.list(!dUsuario.tienePermiso(sender, DPermiso.CATALOGO_GESTIONAR));
    }

    public List<String[]> get(long id) throws SQLException {
        List<String[]> programa = dPrograma.get(id);
        if (programa.isEmpty()) {
            throw new IllegalArgumentException("El programa " + id + " no existe.");
        }
        return programa;
    }

    /** INSPRO["codigo","nombre","descripcion","tipo"] */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        String codigo = Validar.texto(params, 0, "codigo").toUpperCase();
        String nombre = Validar.texto(params, 1, "nombre");
        String descripcion = Validar.opcional(params, 2);
        String tipo = Validar.texto(params, 3, "tipo");
        if (dPrograma.existeCodigo(codigo)) {
            throw new IllegalArgumentException("Ya existe un programa con el código '" + codigo + "'.");
        }
        return dPrograma.get(dPrograma.save(codigo, nombre, descripcion, tipo));
    }

    /** MODPRO["id","nombre","descripcion","tipo","estado"]; "-" conserva el valor. */
    public List<String[]> update(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long id = Validar.id(params, 0, "id");
        get(id);
        String estado = Validar.opcional(params, 4);
        dPrograma.update(id, Validar.opcional(params, 1), Validar.opcional(params, 2), Validar.opcional(params, 3),
                estado == null ? null : Validar.opcionDe(estado, "estado", "ACTIVO", "INACTIVO"));
        return dPrograma.get(id);
    }

    /** DELPRO["id"]: lo retira del catálogo sin borrar su historial. */
    public List<String[]> desactivar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long id = Validar.id(params, 0, "id");
        get(id);
        dPrograma.update(id, null, null, null, "INACTIVO");
        return dPrograma.get(id);
    }
}
