package negocio;

import data.DPermiso;
import data.DUsuario;
import data.DMatricula;
import data.DOferta;
import data.DPreinscripcion;
import java.sql.SQLException;
import java.util.List;

/**
 * Lógica de negocio para PREINSCRIPCION (CU3).
 */
public class NPreinscripcion {

    private static final int CORREO = 3;

    private final DPreinscripcion dPreinscripcion = new DPreinscripcion();
    private final DMatricula dMatricula = new DMatricula();
    private final DOferta dOferta = new DOferta();
    private final DUsuario dUsuario = new DUsuario();

    /** INSPRE["id_oferta","observacion"] */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.PREINSCRIPCION_CREAR);
        long idOferta = Validar.id(params, 0, "id_oferta");
        String observacion = Validar.opcional(params, 1);

        List<String[]> oferta = dOferta.get(idOferta);
        if (oferta.isEmpty()) {
            throw new IllegalArgumentException("La oferta " + idOferta + " no existe. Consulta LISOFE[].");
        }
        if (!"ABIERTA".equals(oferta.get(0)[9])) {
            throw new IllegalStateException("La oferta " + oferta.get(0)[1] + " no está abierta a inscripciones.");
        }
        if (Integer.parseInt(oferta.get(0)[8]) <= 0) {
            throw new IllegalStateException("La oferta " + oferta.get(0)[1] + " no tiene cupos disponibles.");
        }
        long idUsuario = dUsuario.idPorCorreo(sender);
        if (dPreinscripcion.tieneVigente(idUsuario, idOferta)) {
            throw new IllegalStateException("Ya tienes una preinscripción vigente en esta oferta. "
                    + "Consulta su estado con LISPRE[].");
        }
        return dPreinscripcion.get(dPreinscripcion.save(idUsuario, idOferta, observacion));
    }

    /** LISPRE[]: propias, o todas para quien revisa. LISPRE["id"] o LISPRE["PENDIENTE"]: filtro. */
    public List<String[]> list(List<String> params, String sender) throws SQLException {
        boolean revisor = dUsuario.tienePermiso(sender, DPermiso.PREINSCRIPCION_REVISAR);
        String filtro = Validar.opcional(params, 0);
        if (filtro != null && filtro.matches("\\d+")) {
            long id = Validar.id(params, 0, "id");
            List<String[]> pre = get(id);
            if (!revisor && !pre.get(0)[CORREO].equalsIgnoreCase(sender)) {
                throw new IllegalArgumentException("Acceso denegado: esa preinscripción no es tuya.");
            }
            return pre;
        }
        if (!revisor) {
            return dPreinscripcion.listPorCorreo(sender);
        }
        return dPreinscripcion.listTodas(filtro == null ? null
                : Validar.opcionDe(filtro, "estado", "PENDIENTE", "APROBADA", "RECHAZADA", "CANCELADA"));
    }

    /** APRPRE["id","observacion"]: crea la matrícula. @return la matrícula creada. */
    public List<String[]> aprobar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.PREINSCRIPCION_REVISAR);
        long id = Validar.id(params, 0, "id");
        return dMatricula.get(dMatricula.aprobar(id, Validar.opcional(params, 1)));
    }

    /** RECPRE["id","motivo"] */
    public List<String[]> rechazar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.PREINSCRIPCION_REVISAR);
        long id = Validar.id(params, 0, "id");
        String motivo = Validar.texto(params, 1, "motivo");
        List<String[]> pre = get(id);
        if (!dPreinscripcion.rechazar(id, motivo)) {
            throw new IllegalStateException("La preinscripción ya está " + pre.get(0)[8] + ".");
        }
        return dPreinscripcion.get(id);
    }

    /** CANPRE["id"]: el propio estudiante o quien revisa. */
    public List<String[]> cancelar(List<String> params, String sender) throws SQLException {
        long id = Validar.id(params, 0, "id");
        List<String[]> pre = get(id);
        if (!pre.get(0)[CORREO].equalsIgnoreCase(sender)) {
            dUsuario.exigirPermiso(sender, DPermiso.PREINSCRIPCION_REVISAR);
        }
        dMatricula.cancelar(id);
        return dPreinscripcion.get(id);
    }

    private List<String[]> get(long id) throws SQLException {
        List<String[]> pre = dPreinscripcion.get(id);
        if (pre.isEmpty()) {
            throw new IllegalArgumentException("La preinscripción " + id + " no existe.");
        }
        return pre;
    }
}
