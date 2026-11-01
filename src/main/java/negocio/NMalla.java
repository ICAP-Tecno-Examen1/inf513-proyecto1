package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DMalla;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para MALLA_CURRICULAR (CU2). El versionado avanzado queda
 * para un parcial posterior; aquí solo se registra lo que la oferta necesita.
 */
public class NMalla {

    private final DMalla dMalla = new DMalla();
    private final NPrograma nPrograma = new NPrograma();
    private final DUsuario dUsuario = new DUsuario();

    public ArrayList<String[]> list(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        return dMalla.listPorPrograma(Validar.id(params, 0, "id_programa"));
    }

    /** INSMAL["id_programa","version","horas_totales","fecha_vigencia"] */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long idPrograma = Validar.id(params, 0, "id_programa");
        nPrograma.get(idPrograma);
        String version = Validar.texto(params, 1, "version");
        int horas = Validar.entero(Validar.texto(params, 2, "horas_totales"), "horas_totales", 1);
        Date vigencia = Validar.fecha(Validar.opcional(params, 3), "fecha_vigencia");
        return dMalla.get(dMalla.save(idPrograma, version, horas, vigencia));
    }

    boolean existe(long idMalla) throws SQLException {
        return !dMalla.get(idMalla).isEmpty();
    }
}
