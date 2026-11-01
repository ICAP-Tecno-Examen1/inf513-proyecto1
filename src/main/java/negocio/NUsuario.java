package negocio;

import data.DPermiso;
import data.DUsuario;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.mindrot.jbcrypt.BCrypt;

/**
 * Lógica de negocio para USUARIO (CU1).
 */
public class NUsuario {

    private final DUsuario dUsuario = new DUsuario();
    private final NRol nRol = new NRol();
    private final NUsuarioRol nUsuarioRol = new NUsuarioRol();

    /**
     * Director: INSUSU["nombres","apellidos","ci","tipo_doc","correo","username","password","rol",
     * "telefono","direccion","fecha_nacimiento"], con cualquiera de los cinco roles.
     * Correo no registrado: INSUSU["nombres","apellidos","ci","tipo_doc","correo","username","password",
     * "telefono","direccion","fecha_nacimiento"]: se registra a sí mismo como Estudiante.
     */
    public List<String[]> save(List<String> params, String sender) throws SQLException {
        String nombres = Validar.texto(params, 0, "nombres");
        String apellidos = Validar.texto(params, 1, "apellidos");
        String ci = Validar.texto(params, 2, "ci");
        String tipoDocumento = Validar.opcionDe(Validar.texto(params, 3, "tipo_doc"), "tipo_doc", "CI", "PASAPORTE");
        String correo = Validar.texto(params, 4, "correo").toLowerCase(Locale.ROOT);
        String username = Validar.texto(params, 5, "username");
        String password = Validar.texto(params, 6, "password");
        String rolIndicado = NRol.buscar(Validar.opcional(params, 7));
        int inicio = rolIndicado != null ? 8 : 7;
        String telefono = Validar.opcional(params, inicio);
        String direccion = Validar.opcional(params, inicio + 1);
        java.sql.Date fechaNacimiento = Validar.fecha(Validar.opcional(params, inicio + 2), "fecha_nacimiento");
        String rol;

        Validar.correo(correo);
        Validar.clave(password);

        if (dUsuario.tienePermiso(sender, DPermiso.USUARIO_GESTIONAR)) {
            if (rolIndicado == null) {
                throw new IllegalArgumentException("Indica el rol después de la contraseña: Director, Secretaria, "
                        + "Coordinador, Profesor o Estudiante.");
            }
            rol = nRol.normalizar(rolIndicado);
        } else {
            if (dUsuario.existeCorreo(sender)) {
                throw new IllegalArgumentException("Tu correo ya está registrado. Solo el Director registra "
                        + "a otros usuarios.");
            }
            if (!correo.equalsIgnoreCase(sender)) {
                throw new IllegalArgumentException("Para registrarte, el correo indicado debe ser el mismo "
                        + "desde el que envías este mensaje.");
            }
            if (rolIndicado != null && !NRol.ESTUDIANTE.equals(rolIndicado)) {
                throw new IllegalArgumentException("Solo el Director puede registrar usuarios con el rol "
                        + rolIndicado + ". Por tu cuenta puedes registrarte como Estudiante.");
            }
            rol = NRol.ESTUDIANTE;
        }

        if (dUsuario.existeCorreo(correo)) {
            throw new IllegalArgumentException("Ya existe un usuario con el correo '" + correo + "'.");
        }
        if (dUsuario.existeCi(ci)) {
            throw new IllegalArgumentException("Ya existe un usuario con el documento '" + ci + "'.");
        }
        if (dUsuario.existeUsername(username)) {
            throw new IllegalArgumentException("El username '" + username + "' ya está en uso.");
        }

        dUsuario.save(nombres, apellidos, ci, tipoDocumento, correo, username, password, telefono, direccion,
                fechaNacimiento, rol);
        return dUsuario.getPorCi(ci);
    }

    public ArrayList<String[]> list(String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.USUARIO_VER);
        return dUsuario.list();
    }

    /** Datos del propio remitente. */
    public List<String[]> getPropio(String sender) throws SQLException {
        return dUsuario.getPorCorreo(sender);
    }

    public List<String[]> getPorCi(String ci, String sender) throws SQLException {
        List<String[]> usuario = buscar(ci);
        if (!esPropio(usuario, sender)) {
            dUsuario.exigirPermiso(sender, DPermiso.USUARIO_VER);
        }
        return usuario;
    }

    /** MODUSU["ci","nombres","apellidos","telefono","direccion","fecha_nacimiento"]; "-" conserva el valor. */
    public List<String[]> update(List<String> params, String sender) throws SQLException {
        String ci = Validar.texto(params, 0, "ci");
        List<String[]> usuario = buscar(ci);
        if (!esPropio(usuario, sender)) {
            dUsuario.exigirPermiso(sender, DPermiso.USUARIO_GESTIONAR);
        }
        dUsuario.update(ci, Validar.opcional(params, 1), Validar.opcional(params, 2), Validar.opcional(params, 3),
                Validar.opcional(params, 4), Validar.fecha(Validar.opcional(params, 5), "fecha_nacimiento"));
        return dUsuario.getPorCi(ci);
    }

    /** DELUSU["ci"]: baja lógica, para conservar el historial académico y de pagos. */
    public List<String[]> desactivar(List<String> params, String sender) throws SQLException {
        dUsuario.exigirPermiso(sender, DPermiso.USUARIO_GESTIONAR);
        String ci = Validar.texto(params, 0, "ci");
        List<String[]> usuario = buscar(ci);
        if (esPropio(usuario, sender)) {
            throw new IllegalArgumentException("No puedes desactivar tu propia cuenta.");
        }
        nUsuarioRol.exigirOtroDirector(ci);
        dUsuario.setEstado(ci, "INACTIVO");
        return dUsuario.getPorCi(ci);
    }

    /** MODCLA["clave_actual","clave_nueva"] */
    public void cambiarClave(List<String> params, String sender) throws SQLException {
        String actual = Validar.texto(params, 0, "clave actual");
        String nueva = Validar.texto(params, 1, "clave nueva");
        Validar.clave(nueva);
        String[] cuenta = dUsuario.getCredenciales(sender);
        if (cuenta == null || cuenta[1] == null || !BCrypt.checkpw(actual, cuenta[1])) {
            throw new IllegalArgumentException("La clave actual es incorrecta.");
        }
        dUsuario.cambiarClave(sender, nueva);
    }

    private List<String[]> buscar(String ci) throws SQLException {
        List<String[]> usuario = dUsuario.getPorCi(ci);
        if (usuario.isEmpty()) {
            throw new IllegalArgumentException("No existe un usuario con el documento '" + ci + "'.");
        }
        return usuario;
    }

    private boolean esPropio(List<String[]> usuario, String sender) {
        return usuario.get(0)[5].equalsIgnoreCase(sender);
    }
}
