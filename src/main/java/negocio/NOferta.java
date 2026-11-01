package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DOferta;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Lógica de negocio para OFERTA_ACADEMICA (CU2).
 */
public class NOferta {

    private final DOferta dOferta = new DOferta();
    private final NMalla nMalla = new NMalla();
    private final DUsuario dUsuario = new DUsuario();

    /** LISOFE[]: quien gestiona el catálogo ve todas; los demás, solo las abiertas. */
    public ArrayList<String[]> list(String sender) throws SQLException {
        return dUsuario.tienePermiso(sender, DPermiso.CATALOGO_GESTIONAR) ? dOferta.listTodas() : dOferta.listAbiertas();
    }

    public ArrayList<String[]> listAbiertasPorPrograma(long idPrograma) throws SQLException {
        return dOferta.listAbiertasPorPrograma(idPrograma);
    }

    public List<String[]> get(long id) throws SQLException {
        List<String[]> oferta = dOferta.get(id);
        if (oferta.isEmpty()) {
            throw new IllegalArgumentException("La oferta " + id + " no existe.");
        }
        return oferta;
    }

    /** INSOFE["codigo","id_malla","modalidad","fecha_inicio","fecha_fin","cupo_maximo","cupo_minimo","gestion"] */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        String codigo = Validar.texto(params, 0, "codigo").toUpperCase();
        long idMalla = Validar.id(params, 1, "id_malla");
        String modalidad = Validar.opcionDe(Validar.texto(params, 2, "modalidad"), "modalidad",
                "PRESENCIAL", "VIRTUAL", "SEMIPRESENCIAL");
        Date inicio = Validar.fecha(Validar.texto(params, 3, "fecha_inicio"), "fecha_inicio");
        Date fin = Validar.fecha(Validar.opcional(params, 4), "fecha_fin");
        int cupoMaximo = Validar.entero(Validar.texto(params, 5, "cupo_maximo"), "cupo_maximo", 1);
        int cupoMinimo = Validar.entero(Validar.texto(params, 6, "cupo_minimo"), "cupo_minimo", 0);
        int gestion = Validar.entero(Validar.texto(params, 7, "gestion"), "gestion", 2000);

        if (fin != null && fin.before(inicio)) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        if (cupoMinimo > cupoMaximo) {
            throw new IllegalArgumentException("El cupo mínimo no puede superar al cupo máximo.");
        }
        if (!nMalla.existe(idMalla)) {
            throw new IllegalArgumentException("La malla " + idMalla + " no existe.");
        }
        if (dOferta.existeCodigo(codigo)) {
            throw new IllegalArgumentException("Ya existe una oferta con el código '" + codigo + "'.");
        }
        return dOferta.get(dOferta.save(codigo, idMalla, modalidad, inicio, fin, cupoMaximo, cupoMinimo, gestion));
    }

    /** MODOFE["id","estado","fecha_fin","cupo_maximo"]; "-" conserva el valor. */
    public List<String[]> update(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long id = Validar.id(params, 0, "id");
        String[] oferta = get(id).get(0);
        String estado = Validar.opcional(params, 1);
        Date fin = Validar.fecha(Validar.opcional(params, 2), "fecha_fin");
        String cupo = Validar.opcional(params, 3);
        Integer cupoMaximo = cupo == null ? null : Validar.entero(cupo, "cupo_maximo", 1);

        if (fin != null && fin.toLocalDate().isBefore(LocalDate.parse(oferta[4]))) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio.");
        }
        dOferta.update(id, estado == null ? null : Validar.opcionDe(estado, "estado", "ABIERTA", "CERRADA"),
                fin, cupoMaximo);
        return dOferta.get(id);
    }

    /** DELOFE["id"]: cierra la oferta para nuevas preinscripciones. */
    public List<String[]> cerrar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.CATALOGO_GESTIONAR);
        long id = Validar.id(params, 0, "id");
        get(id);
        dOferta.update(id, "CERRADA", null, null);
        return dOferta.get(id);
    }
}
