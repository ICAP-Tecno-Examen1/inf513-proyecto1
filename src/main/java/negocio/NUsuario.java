package negocio;

import data.DPermiso;
import data.DUsuario;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Supplier;
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
    /** Formato y ejemplo que se adjuntan a todo error de validación de INSUSU. */
    static final String FORMATO_INSUSU = "Formato correcto: INSUSU[\"nombres\",\"apellidos\",\"ci\",\"CI|PASAPORTE\","
            + "\"correo\",\"username\",\"password\",\"telefono\",\"direccion\",\"AAAA-MM-DD\"]. "
            + "Ejemplo: INSUSU[\"Maria\",\"Perez\",\"1234567\",\"CI\",\"example@example.com\",\"mperez\","
            + "\"ClaveSegura1\",\"70123456\",\"Calle Ejemplo 123\",\"2000-01-15\"]";

    /** Formato para el Director: el rol va en la posición 8 y es obligatorio. */
    static final String FORMATO_INSUSU_DIRECTOR = "Formato correcto: INSUSU[\"nombres\",\"apellidos\",\"ci\","
            + "\"CI|PASAPORTE\",\"correo\",\"username\",\"password\",\"rol\",\"telefono\",\"direccion\","
            + "\"AAAA-MM-DD\"]. Rol: Director, Secretaria, Coordinador, Profesor o Estudiante. "
            + "Ejemplo: INSUSU[\"Maria\",\"Perez\",\"1234567\",\"CI\",\"example@example.com\",\"mperez\","
            + "\"ClaveSegura1\",\"Estudiante\",\"70123456\",\"Calle Ejemplo 123\",\"2000-01-15\"]";

    public List<String[]> save(List<String> params, String sender) throws SQLException {
        // Se revisan todos los campos y se informan juntos, no solo el primero que falla.
        List<String> errores = new ArrayList<>();
        String nombres = revisar(errores, () -> {
            String v = Validar.texto(params, 0, "nombres");
            Validar.soloLetras(v, 1, "nombres");
            return v;
        });
        String apellidos = revisar(errores, () -> {
            String v = Validar.texto(params, 1, "apellidos");
            Validar.soloLetras(v, 2, "apellidos");
            return v;
        });
        String tipoDocumento = revisar(errores, () -> tipoDocumento(Validar.texto(params, 3, "tipo_doc")));
        String ci = revisar(errores, () -> {
            String v = Validar.texto(params, 2, "ci");
            if (tipoDocumento != null) {
                Validar.documento(v, tipoDocumento, 3);
            }
            return v;
        });
        String correo = revisar(errores, () -> {
            String v = Validar.texto(params, 4, "correo").toLowerCase(Locale.ROOT);
            if (!Validar.esCorreo(v)) {
                throw Validar.errorCampo(5, "correo", v, "no tiene un formato válido, por ejemplo nombre@dominio.com");
            }
            return v;
        });
        String username = revisar(errores, () -> Validar.texto(params, 5, "username"));
        String password = revisar(errores, () -> {
            String v = Validar.texto(params, 6, "password");
            if (v.length() < 8) {
                // La contraseña nunca se muestra en el error.
                throw Validar.errorCampo(7, "password", "***", "debe tener al menos 8 caracteres");
            }
            return v;
        });
        // Quien envía decide si la posición 8 es un rol (Director: obligatorio) o el teléfono (registro propio).
        final boolean esDirector = dUsuario.tienePermiso(sender, DPermiso.USUARIO_GESTIONAR);
        final boolean hayRol = esDirector || NRol.buscar(Validar.opcional(params, 7)) != null;
        String rolIndicado = hayRol ? revisar(errores, () -> rolIndicado(params, esDirector)) : null;
        final int inicio = hayRol ? 8 : 7;
        String telefono = revisar(errores, () -> {
            String v = Validar.opcional(params, inicio);
            Validar.digitos(v, inicio + 1, "telefono");
            return v;
        });
        String direccion = Validar.opcional(params, inicio + 1);
        java.sql.Date fechaNacimiento = revisar(errores, () -> {
            String v = Validar.opcional(params, inicio + 2);
            try {
                return Validar.fecha(v, "fecha_nacimiento");
            } catch (IllegalArgumentException e) {
                throw Validar.errorCampo(inicio + 3, "fecha_nacimiento", v, "debe tener el formato AAAA-MM-DD");
            }
        });
        if (!errores.isEmpty()) {
            throw new ErroresValidacion(errores, esDirector ? FORMATO_INSUSU_DIRECTOR : FORMATO_INSUSU);
        }
        String rol;

        if (esDirector) {
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

    /** Ejecuta una regla de un campo; si falla, guarda el mensaje y sigue con los demás campos. */
    private static <T> T revisar(List<String> errores, Supplier<T> regla) {
        try {
            return regla.get();
        } catch (IllegalArgumentException e) {
            errores.add(e.getMessage());
            return null;
        }
    }

    /**
     * Posición 8 del INSUSU. El Director debe indicar un rol válido; quien se registra solo
     * únicamente puede indicar Estudiante (o nada).
     */
    private static String rolIndicado(List<String> params, boolean esDirector) {
        String valor = Validar.opcional(params, 7);
        if (valor == null) {
            throw Validar.errorCampo(8, "rol", "(vacío)", "indica el rol: Director, Secretaria, Coordinador, "
                    + "Profesor o Estudiante");
        }
        String rol = NRol.buscar(valor);
        if (rol == null) {
            throw Validar.errorCampo(8, "rol", valor, "no es un rol del sistema: Director, Secretaria, "
                    + "Coordinador, Profesor o Estudiante");
        }
        if (!esDirector && !NRol.ESTUDIANTE.equals(rol)) {
            throw Validar.errorCampo(8, "rol", valor, "solo el Director puede asignar roles; si te registras "
                    + "por tu cuenta quedas como Estudiante, así que omite este campo");
        }
        return rol;
    }

    /** Solo CI o PASAPORTE, sin distinguir mayúsculas. */
    private static String tipoDocumento(String valor) {
        String normalizado = valor.toUpperCase(Locale.ROOT);
        if ("CI".equals(normalizado) || "PASAPORTE".equals(normalizado)) {
            return normalizado;
        }
        throw Validar.errorCampo(4, "tipo_doc", valor, "solo se acepta CI o PASAPORTE");
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
