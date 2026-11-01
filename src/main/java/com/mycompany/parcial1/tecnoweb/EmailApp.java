package com.mycompany.parcial1.tecnoweb;

import data.*;
import interfaces.ICasoUsoListener;
import interfaces.IEmailListener;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;

import javax.mail.internet.MimeUtility;

import librerias.Email;
import librerias.HtmlRes;
import librerias.Interpreter;
import librerias.ParamsAction;
import librerias.analex.Token;
import negocio.*;
import postgresConecction.EmailReceipt;
import postgresConecction.EmailSend;

public class EmailApp implements ICasoUsoListener, IEmailListener {

    // Comandos disponibles sin estar registrado: registro, ayuda y consulta del catálogo.
    private static final String[] COMANDOS_PUBLICOS = { "HELP", "INSUSU", "LISPRO", "LISOFE", "LISARA" };

    private static final String CID_QR = "qr_image";
    private static final String PUBLICO = "";
    private static final String REGISTRADO = "*";

    // { módulo, comando, descripción, permiso necesario }
    private static final String[][] COMANDOS = {
            { "Usuario", "INSUSU[\"nombres\",\"apellidos\",\"ci\",\"CI|PASAPORTE\",\"correo\",\"username\",\"password\",\"telefono\",\"direccion\",\"AAAA-MM-DD\"]", "Registrarte como Estudiante (el correo debe ser el remitente)", PUBLICO },
            { "Usuario", "INSUSU[\"nombres\",\"apellidos\",\"ci\",\"CI|PASAPORTE\",\"correo\",\"username\",\"password\",\"Director|Secretaria|Coordinador|Profesor|Estudiante\",\"telefono\",\"direccion\",\"AAAA-MM-DD\"]", "Registrar un usuario indicando su rol", DPermiso.USUARIO_GESTIONAR },
            { "Usuario", "LISUSU[\"-\"]", "Ver tus datos personales", REGISTRADO },
            { "Usuario", "MODUSU[\"ci\",\"nombres\",\"apellidos\",\"telefono\",\"direccion\",\"fecha_nacimiento\"]", "Actualizar datos (\"-\" conserva el valor)", REGISTRADO },
            { "Usuario", "MODCLA[\"clave_actual\",\"clave_nueva\"]", "Cambiar tu contraseña", REGISTRADO },
            { "Usuario", "LISUSU[]", "Listar usuarios", DPermiso.USUARIO_VER },
            { "Usuario", "LISUSU[\"ci\"]", "Ver un usuario", DPermiso.USUARIO_VER },
            { "Usuario", "DELUSU[\"ci\"]", "Desactivar un usuario", DPermiso.USUARIO_GESTIONAR },
            { "Roles", "LISROL[]", "Ver roles y sus permisos", DPermiso.ROL_GESTIONAR },
            { "Roles", "INSROL[\"ci\",\"rol\"]", "Asignar un rol a un usuario", DPermiso.ROL_GESTIONAR },
            { "Roles", "DELROL[\"ci\",\"rol\"]", "Quitar un rol a un usuario", DPermiso.ROL_GESTIONAR },
            { "Permisos", "LISPER[]", "Ver permisos disponibles", DPermiso.ROL_GESTIONAR },
            { "Permisos", "INSPER[\"rol\",\"permiso\"]", "Dar un permiso a un rol", DPermiso.ROL_GESTIONAR },
            { "Permisos", "DELPER[\"rol\",\"permiso\"]", "Quitar un permiso a un rol", DPermiso.ROL_GESTIONAR },
            { "Catálogo", "LISPRO[]", "Ver programas académicos", PUBLICO },
            { "Catálogo", "LISPRO[\"id\"]", "Ver un programa y sus ofertas abiertas", PUBLICO },
            { "Catálogo", "LISOFE[]", "Ver ofertas: modalidad, duración, cupos y costo", PUBLICO },
            { "Catálogo", "LISOFE[\"id\"]", "Ver una oferta", PUBLICO },
            { "Catálogo", "LISARA[\"id_oferta\"]", "Ver el detalle de costos de una oferta", PUBLICO },
            { "Catálogo", "INSPRO[\"codigo\",\"nombre\",\"descripcion\",\"tipo\"]", "Registrar programa", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "MODPRO[\"id\",\"nombre\",\"descripcion\",\"tipo\",\"ACTIVO|INACTIVO\"]", "Actualizar programa", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "DELPRO[\"id\"]", "Retirar programa del catálogo", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "INSMAL[\"id_programa\",\"version\",\"horas_totales\",\"fecha_vigencia\"]", "Registrar malla", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "LISMAL[\"id_programa\"]", "Ver mallas de un programa", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "INSOFE[\"codigo\",\"id_malla\",\"PRESENCIAL|VIRTUAL|SEMIPRESENCIAL\",\"inicio\",\"fin\",\"cupo_max\",\"cupo_min\",\"gestion\"]", "Registrar oferta", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "MODOFE[\"id\",\"ABIERTA|CERRADA\",\"fecha_fin\",\"cupo_maximo\"]", "Actualizar oferta", DPermiso.CATALOGO_GESTIONAR },
            { "Catálogo", "DELOFE[\"id\"]", "Cerrar oferta", DPermiso.CATALOGO_GESTIONAR },
            { "Aranceles", "INSARA[\"id_oferta\",\"concepto\",\"monto\",\"UNICO|MENSUAL\"]", "Registrar costo (en MENSUAL, monto por mes)", DPermiso.CATALOGO_GESTIONAR },
            { "Aranceles", "DELARA[\"id\"]", "Desactivar un costo", DPermiso.CATALOGO_GESTIONAR },
            { "Inscripción", "INSPRE[\"id_oferta\",\"observacion\"]", "Preinscribirte en una oferta", DPermiso.PREINSCRIPCION_CREAR },
            { "Inscripción", "LISPRE[]", "Ver tus solicitudes y su estado", REGISTRADO },
            { "Inscripción", "CANPRE[\"id\"]", "Cancelar una solicitud", REGISTRADO },
            { "Inscripción", "LISPRE[\"PENDIENTE\"]", "Ver solicitudes por estado", DPermiso.PREINSCRIPCION_REVISAR },
            { "Inscripción", "APRPRE[\"id\",\"observacion\"]", "Aprobar solicitud y crear matrícula", DPermiso.PREINSCRIPCION_REVISAR },
            { "Inscripción", "RECPRE[\"id\",\"motivo\"]", "Rechazar solicitud", DPermiso.PREINSCRIPCION_REVISAR },
            { "Matrícula", "LISMAT[]", "Ver matrículas y su estado", REGISTRADO },
            { "Matrícula", "LISMAT[\"id\"]", "Ver una matrícula", REGISTRADO },
            { "Pagos", "LISCUO[\"id_matricula\"]", "Ver cuotas, saldos y vencimientos", REGISTRADO },
            { "Pagos", "LISMET[]", "Ver métodos de pago", REGISTRADO },
            { "Pagos", "RESPAG[\"id_matricula\",\"QR|FISICO\"]", "Elegir forma de pago de la siguiente cuota", DPermiso.PAGO_REGISTRAR },
            { "Pagos", "PAGCUO[\"id_cuota\"]", "Generar el QR de Pago Fácil de una cuota", DPermiso.PAGO_REGISTRAR },
            { "Pagos", "PAGCUO[\"id_cuota\",\"nombres\",\"apellidos\",\"CI|PASAPORTE|NIT\",\"num_doc\",\"telefono\",\"correo\",\"parentesco\"]", "Generar el QR para que pague un tercero", DPermiso.PAGO_REGISTRAR },
            { "Pagos", "PAGARQR[\"id_pago\"]", "Volver a generar el QR de un pago pendiente", DPermiso.PAGO_REGISTRAR },
            { "Pagos", "PEDPAG[\"id_matricula\"]", "Pedir al estudiante que elija la forma de pago", DPermiso.PAGO_CONFIRMAR },
            { "Pagos", "INSPAG[\"id_cuota\",\"id_metodo\",\"monto\",\"referencia\",\"comprobante_url\"]", "Registrar pago con comprobante o efectivo", DPermiso.PAGO_REGISTRAR },
            { "Pagos", "INSPAG[...,\"comprobante_url\",\"nombres\",\"apellidos\",\"CI|PASAPORTE|NIT\",\"num_doc\",\"telefono\",\"correo\",\"parentesco\"]", "Registrar pago hecho por un tercero", DPermiso.PAGO_REGISTRAR },
            { "Pagos", "LISPAG[]", "Ver historial de pagos", REGISTRADO },
            { "Pagos", "LISPAG[\"PENDIENTE\"]", "Ver pagos por estado", REGISTRADO },
            { "Pagos", "CONPAG[\"id\"]", "Confirmar un pago", DPermiso.PAGO_CONFIRMAR },
            { "Pagos", "RECPAG[\"id\",\"motivo\"]", "Rechazar un pago", DPermiso.PAGO_CONFIRMAR },
            { "Becas", "LISBEC[]", "Ver becas", REGISTRADO },
            { "Becas", "INSBEC[\"nombre\",\"descripcion\",\"porcentaje\",\"institucion\"]", "Registrar beca", DPermiso.BECA_GESTIONAR },
            { "Becas", "INSBES[\"id_matricula\",\"id_beca\",\"porcentaje\"]", "Aplicar beca (porcentaje opcional)", DPermiso.BECA_ASIGNAR },
            { "Becas", "DELBES[\"id_matricula\"]", "Quitar beca", DPermiso.BECA_ASIGNAR },
    };

    private final EmailReceipt emailReceipt;
    private final DUsuario dUsuario = new DUsuario();
    private final NUsuario nUsuario = new NUsuario();
    private final NRol nRol = new NRol();
    private final NPermiso nPermiso = new NPermiso();
    private final NUsuarioRol nUsuarioRol = new NUsuarioRol();
    private final NRolPermiso nRolPermiso = new NRolPermiso();
    private final NPrograma nPrograma = new NPrograma();
    private final NMalla nMalla = new NMalla();
    private final NOferta nOferta = new NOferta();
    private final NArancel nArancel = new NArancel();
    private final NPreinscripcion nPreinscripcion = new NPreinscripcion();
    private final NMatricula nMatricula = new NMatricula();
    private final NMensualidad nMensualidad = new NMensualidad();
    private final NBeca nBeca = new NBeca();
    private final NBecaEstudiante nBecaEstudiante = new NBecaEstudiante();
    private final NMetodoPago nMetodoPago = new NMetodoPago();
    private final NPago nPago = new NPago();

    public EmailApp() {
        this.emailReceipt = new EmailReceipt();
        this.emailReceipt.setEmailListener(this);
    }

    public void start() {
        Thread thread = new Thread(emailReceipt);
        thread.setName("Mail Receipt");
        thread.start();
    }

    /** Un caso de uso a ejecutar; los errores de todos se resuelven en atender(). */
    private interface Accion {
        void ejecutar() throws SQLException;
    }

    private void atender(ParamsAction event, Accion accion) {
        String sender = event.getSender();
        try {
            accion.ejecutar();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            sendEmail(new Email(sender, "Solicitud no procesada",
                    HtmlRes.generateErrorMessage("Solicitud no procesada", ex.getMessage())));
        } catch (SQLException | RuntimeException ex) {
            // El detalle técnico queda en el log; al usuario solo se le informa del fallo.
            System.err.println("Error procesando comando de " + sender + ": " + ex);
            sendEmail(new Email(sender, "Error del sistema", HtmlRes.generateErrorMessage("Error del sistema",
                    "No se pudo procesar tu solicitud. Intenta nuevamente en unos minutos.")));
        }
    }

    private void noDisponible(ParamsAction event) {
        simpleNotify(event.getSender(), "Comando no disponible",
                "Esa acción no existe para este módulo. Envía HELP para ver tus comandos.");
    }

    // ── Infraestructura ──────────────────────────────────────────────────────

    @Override
    public void error(ParamsAction event) {
        simpleNotify(event.getSender(), "Comando no reconocido",
                "Usa el formato AAAEEE[\"param1\",\"param2\"] en el asunto. Envía HELP para ver tus comandos.");
    }

    @Override
    public void help(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            boolean activo = dUsuario.estaActivo(sender);
            ArrayList<String[]> data = new ArrayList<>();
            for (String[] c : COMANDOS) {
                String permiso = c[3];
                boolean visible = PUBLICO.equals(permiso)
                        || (activo && (REGISTRADO.equals(permiso) || dUsuario.tienePermiso(sender, permiso)));
                if (visible) {
                    data.add(new String[] { c[0], c[1], c[2] });
                }
            }
            String titulo = activo ? "Tus comandos" : "Bienvenido al ICAP-UAGRM: regístrate para ver más comandos";
            tableNotifySuccess(sender, titulo, new String[] { "Módulo", "Comando", "Descripción" }, data);
        });
    }

    // ── CU1: Gestión de Usuarios ─────────────────────────────────────────────

    @Override
    public void usuario(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            List<String> params = event.getParams();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Usuario registrado", DUsuario.HEADERS, nUsuario.save(params, sender));
                    break;
                case Token.GET:
                    if (params.isEmpty()) {
                        tabla(sender, "Lista de usuarios", DUsuario.HEADERS, nUsuario.list(sender));
                    } else if ("-".equals(params.get(0))) {
                        tabla(sender, "Tus datos personales", DUsuario.HEADERS, nUsuario.getPropio(sender));
                    } else {
                        tabla(sender, "Datos del usuario", DUsuario.HEADERS, nUsuario.getPorCi(params.get(0), sender));
                    }
                    break;
                case Token.MODIFY:
                    tabla(sender, "Datos actualizados", DUsuario.HEADERS, nUsuario.update(params, sender));
                    break;
                case Token.DELETE:
                    tabla(sender, "Usuario desactivado", DUsuario.HEADERS, nUsuario.desactivar(params, sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    @Override
    public void clave(ParamsAction event) {
        atender(event, () -> {
            if (event.getAction() != Token.MODIFY) {
                noDisponible(event);
                return;
            }
            nUsuario.cambiarClave(event.getParams(), event.getSender());
            simpleNotifySuccess(event.getSender(), "Contraseña actualizada", "Tu contraseña se cambió correctamente.");
        });
    }

    @Override
    public void rol(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Rol asignado", DUsuario.HEADERS, nUsuarioRol.asignar(event.getParams(), sender));
                    break;
                case Token.GET:
                    tabla(sender, "Roles del sistema", DRol.HEADERS, nRol.list(sender));
                    break;
                case Token.DELETE:
                    tabla(sender, "Rol retirado", DUsuario.HEADERS, nUsuarioRol.quitar(event.getParams(), sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    @Override
    public void permiso(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Permiso asignado", DRol.HEADERS, nRolPermiso.asignar(event.getParams(), sender));
                    break;
                case Token.GET:
                    tabla(sender, "Permisos disponibles", DPermiso.HEADERS, nPermiso.list(sender));
                    break;
                case Token.DELETE:
                    tabla(sender, "Permiso retirado", DRol.HEADERS, nRolPermiso.quitar(event.getParams(), sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    // ── CU2: Catálogo de Programas y Ofertas Académicas ──────────────────────

    @Override
    public void programa(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            List<String> params = event.getParams();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Programa registrado", DPrograma.HEADERS, nPrograma.save(params, sender));
                    break;
                case Token.GET:
                    if (params.isEmpty()) {
                        tabla(sender, "Catálogo de programas", DPrograma.HEADERS, nPrograma.list(sender));
                    } else {
                        enviarDetallePrograma(sender, params);
                    }
                    break;
                case Token.MODIFY:
                    tabla(sender, "Programa actualizado", DPrograma.HEADERS, nPrograma.update(params, sender));
                    break;
                case Token.DELETE:
                    tabla(sender, "Programa retirado del catálogo", DPrograma.HEADERS, nPrograma.desactivar(params, sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    private void enviarDetallePrograma(String sender, List<String> params) throws SQLException {
        long id = idDe(params);
        String[] p = nPrograma.get(id).get(0);
        ArrayList<String[]> data = new ArrayList<>();
        for (String[] o : nOferta.listAbiertasPorPrograma(id)) {
            data.add(new String[] { o[0], o[1], o[3], o[4], o[5], o[6], o[8], o[10], o[11], o[12] });
        }
        String titulo = p[2] + " (" + p[4] + ") - " + (p[3] != null ? p[3] : "");
        tableNotifySuccess(sender, titulo, new String[] { "id_oferta", "codigo", "modalidad", "inicio", "fin", "horas",
                "cupos_libres", "tipo_cobro", "cuotas", "costo_total_Bs" }, data);
    }

    @Override
    public void malla(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Malla registrada", DMalla.HEADERS, nMalla.save(event.getParams(), sender));
                    break;
                case Token.GET:
                    tabla(sender, "Mallas del programa", DMalla.HEADERS, nMalla.list(event.getParams(), sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    @Override
    public void oferta(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            List<String> params = event.getParams();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Oferta registrada", DOferta.HEADERS, nOferta.save(params, sender));
                    break;
                case Token.GET:
                    if (params.isEmpty()) {
                        tabla(sender, "Ofertas académicas", DOferta.HEADERS, nOferta.list(sender));
                    } else {
                        tabla(sender, "Detalle de la oferta", DOferta.HEADERS, nOferta.get(idDe(params)));
                    }
                    break;
                case Token.MODIFY:
                    tabla(sender, "Oferta actualizada", DOferta.HEADERS, nOferta.update(params, sender));
                    break;
                case Token.DELETE:
                    tabla(sender, "Oferta cerrada", DOferta.HEADERS, nOferta.cerrar(params, sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    // ── CU3: Preinscripción y Ciclo de Vida del Estudiante ───────────────────

    @Override
    public void preinscripcion(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            List<String> params = event.getParams();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Preinscripción registrada: queda pendiente de revisión",
                            DPreinscripcion.HEADERS, nPreinscripcion.save(params, sender));
                    break;
                case Token.GET:
                    tabla(sender, "Preinscripciones", DPreinscripcion.HEADERS, nPreinscripcion.list(params, sender));
                    break;
                case Token.APPROVE:
                    List<String[]> matricula = nPreinscripcion.aprobar(params, sender);
                    tabla(sender, "Preinscripción aprobada", DMatricula.HEADERS, matricula);
                    avisarAprobacion(matricula.get(0));
                    break;
                case Token.REJECT:
                    List<String[]> rechazada = nPreinscripcion.rechazar(params, sender);
                    tabla(sender, "Preinscripción rechazada", DPreinscripcion.HEADERS, rechazada);
                    simpleNotify(rechazada.get(0)[3], "Tu preinscripción fue rechazada",
                            "Solicitud " + rechazada.get(0)[0] + " en " + rechazada.get(0)[5] + ". Motivo: " + rechazada.get(0)[9]);
                    break;
                case Token.CANCEL:
                    tabla(sender, "Preinscripción cancelada", DPreinscripcion.HEADERS, nPreinscripcion.cancelar(params, sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    // Informa al estudiante el costo, la modalidad de pago y los siguientes pasos.
    private void avisarAprobacion(String[] m) throws SQLException {
        String correo = m[4];
        ArrayList<String[]> data = new ArrayList<>();
        for (String[] c : nMensualidad.listPorMatricula(Long.parseLong(m[0]))) {
            data.add(new String[] { c[0], c[2], c[3], c[4], c[7], c[10] });
        }
        tableNotifySuccess(correo,
                "Solicitud aprobada. Matrícula " + m[1] + " (" + m[9] + "), total Bs " + m[12]
                        + ". Paga con QR usando PAGCUO[\"id_cuota\"], o en Secretaría.",
                new String[] { "id_cuota", "concepto", "cuota", "vencimiento", "monto_Bs", "estado" }, data);
    }

    @Override
    public void matricula(ParamsAction event) {
        atender(event, () -> {
            if (event.getAction() != Token.GET) {
                noDisponible(event);
                return;
            }
            tabla(event.getSender(), "Matrículas", DMatricula.HEADERS,
                    nMatricula.list(event.getParams(), event.getSender()));
        });
    }

    // ── CU4: Aranceles, Becas y Conciliación de Pagos ────────────────────────

    @Override
    public void arancel(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Arancel registrado", DArancel.HEADERS, nArancel.save(event.getParams(), sender));
                    break;
                case Token.GET:
                    tabla(sender, "Costos de la oferta", DArancel.HEADERS, nArancel.list(event.getParams()));
                    break;
                case Token.DELETE:
                    tabla(sender, "Arancel desactivado", DArancel.HEADERS, nArancel.desactivar(event.getParams(), sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    @Override
    public void mensualidad(ParamsAction event) {
        atender(event, () -> {
            if (event.getAction() != Token.GET) {
                noDisponible(event);
                return;
            }
            tabla(event.getSender(), "Plan de pagos", DMensualidad.HEADERS,
                    nMensualidad.list(event.getParams(), event.getSender()));
        });
    }

    @Override
    public void beca(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Beca registrada", DBeca.HEADERS, nBeca.save(event.getParams(), sender));
                    break;
                case Token.GET:
                    tabla(sender, "Becas disponibles", DBeca.HEADERS, nBeca.list());
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    @Override
    public void becaEstudiante(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            switch (event.getAction()) {
                case Token.ADD:
                    avisarCambioBeca(sender, "Beca aplicada", nBecaEstudiante.asignar(event.getParams(), sender));
                    break;
                case Token.DELETE:
                    avisarCambioBeca(sender, "Beca retirada", nBecaEstudiante.quitar(event.getParams(), sender));
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    private void avisarCambioBeca(String sender, String titulo, List<String[]> matricula) {
        tabla(sender, titulo, DMatricula.HEADERS, matricula);
        String[] m = matricula.get(0);
        simpleNotify(m[4], titulo, "Matrícula " + m[1] + ": original Bs " + m[10] + ", descuento Bs " + m[11]
                + ", a pagar Bs " + m[12] + ". Estado: " + m[8] + ".");
    }

    @Override
    public void metodoPago(ParamsAction event) {
        atender(event, () -> {
            if (event.getAction() != Token.GET) {
                noDisponible(event);
                return;
            }
            tabla(event.getSender(), "Métodos de pago", DMetodoPago.HEADERS, nMetodoPago.list());
        });
    }

    @Override
    public void pago(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            List<String> params = event.getParams();
            switch (event.getAction()) {
                case Token.ADD:
                    tabla(sender, "Pago registrado: queda pendiente de verificación", DPago.HEADERS, nPago.registrar(params, sender));
                    break;
                case Token.GET:
                    tabla(sender, "Pagos", DPago.HEADERS, nPago.list(params, sender));
                    break;
                case Token.CONFIRM:
                    String[] resultado = nPago.confirmar(params, sender);
                    String detalle = "Matrícula " + resultado[1] + ": " + resultado[2] + ". Cuota: " + resultado[3] + ".";
                    simpleNotifySuccess(sender, "Pago confirmado", detalle);
                    simpleNotifySuccess(resultado[0], "Tu pago fue confirmado", detalle);
                    break;
                case Token.REJECT:
                    String correo = nPago.rechazar(params, sender);
                    simpleNotify(sender, "Pago rechazado", "Se rechazó el pago " + params.get(0) + ".");
                    simpleNotify(correo, "Tu pago fue rechazado", "Pago " + params.get(0) + ". Motivo: " + params.get(1)
                            + ". Puedes registrarlo nuevamente con los datos correctos.");
                    break;
                default:
                    noDisponible(event);
                    break;
            }
        });
    }

    @Override
    public void pagarQR(ParamsAction event) {
        atender(event, () -> enviarQr(event.getSender(), nPago.regenerarQr(event.getParams(), event.getSender())));
    }

    @Override
    public void pagcuo(ParamsAction event) {
        atender(event, () -> enviarQr(event.getSender(), nPago.generarQrCuota(event.getParams(), event.getSender())));
    }

    @Override
    public void respag(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            List<String> params = event.getParams();
            if (nPago.eligioQr(params)) {
                enviarQr(sender, nPago.generarQrSiguienteCuota(params, sender));
            } else {
                String[] c = nPago.siguienteCuota(params, sender);
                simpleNotify(sender, "Forma de pago registrada", "Pasa por Secretaría a pagar Bs " + c[4] + " de "
                        + c[1] + " (cuota " + c[2] + ", vence " + c[3] + "). Allí registrarán tu pago en efectivo.");
            }
        });
    }

    @Override
    public void pedpag(ParamsAction event) {
        atender(event, () -> {
            String sender = event.getSender();
            String[] c = nPago.pedirFormaDePago(event.getParams(), sender);
            String matricula = event.getParams().get(0);
            simpleNotify(c[5], "Elige tu forma de pago", "Cuota pendiente: " + c[1] + " " + c[2] + ", Bs " + c[4]
                    + ", vence " + c[3] + ". Responde con RESPAG[\"" + matricula + "\",\"QR\"] para recibir tu QR, o RESPAG[\""
                    + matricula + "\",\"FISICO\"] para pagar en Secretaría.");
            simpleNotify(sender, "Solicitud enviada", "Se pidió la forma de pago a " + c[5] + ".");
        });
    }

    // Envía el QR a quien lo pidió, al estudiante y, si se indicó, a quien pagará.
    private void enviarQr(String sender, NPago.QrPago qr) {
        String titulo = "Código QR para tu pago";
        String html = HtmlRes.generateQrMessage(titulo, new String[][] {
                { "Pago", String.valueOf(qr.idPago()) },
                { "Concepto", qr.detalle() },
                { "Monto", "Bs " + qr.monto().toPlainString() },
                { "Transacción", qr.transaccion() },
                { "Expira", qr.expira() },
                { "Estado", "PENDIENTE: se confirma automáticamente al pagar" }
        }, CID_QR);
        Set<String> destinos = new TreeSet<>(String.CASE_INSENSITIVE_ORDER);
        destinos.add(sender);
        destinos.add(qr.correoEstudiante());
        if (qr.correoPagador() != null) {
            destinos.add(qr.correoPagador());
        }
        for (String destino : destinos) {
            sendEmailQr(destino, titulo, html, qr.imagen());
        }
    }

    // ── Recepción de correos ─────────────────────────────────────────────────

    @Override
    public void onReceiptEmail(List<Email> emails) {
        for (Email email : emails) {
            String sender = email.getFrom();
            // Los rebotes llegan sin remitente; responderlos crearía un ciclo de correos.
            if (sender == null || sender.isBlank() || sender.toLowerCase(Locale.ROOT).startsWith("mailer-daemon")
                    || sender.equalsIgnoreCase(EmailSend.MAIL)) {
                continue;
            }
            String comando = Interpreter.normalizar(decodificarAsunto(email.getSubject()));
            ParamsAction contexto = new ParamsAction(this, sender, comando);
            atender(contexto, () -> {
                if (!esPublico(comando) && !dUsuario.estaActivo(sender)) {
                    simpleNotify(sender, "Acceso no disponible", dUsuario.existeCorreo(sender)
                            ? "Tu cuenta está inactiva. Comunícate con la administración del ICAP."
                            : "Tu correo no está registrado. Envía HELP para ver cómo registrarte.");
                    return;
                }
                // Secuencial: los comandos de un mismo lote se atienden en orden de llegada.
                Interpreter interpreter = new Interpreter(comando, sender);
                interpreter.setCasoUsoListener(this);
                interpreter.run();
            });
        }
    }

    private String decodificarAsunto(String asunto) {
        try {
            return MimeUtility.decodeText(asunto);
        } catch (Exception e) {
            return asunto;
        }
    }

    private boolean esPublico(String comando) {
        String normalizado = comando.toUpperCase(Locale.ROOT);
        for (String publico : COMANDOS_PUBLICOS) {
            if (normalizado.startsWith(publico)) {
                return true;
            }
        }
        return false;
    }

    // ── Respuestas ───────────────────────────────────────────────────────────

    private long idDe(List<String> params) {
        try {
            return Long.parseLong(params.get(0).trim());
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("El id debe ser un número.");
        }
    }

    private void tabla(String email, String title, String[] headers, List<String[]> data) {
        if (data.isEmpty()) {
            simpleNotify(email, title, "No hay registros para mostrar.");
        } else {
            tableNotifySuccess(email, title, headers, data);
        }
    }

    private void tableNotifySuccess(String email, String title, String[] headers, List<String[]> data) {
        sendEmail(new Email(email, Email.SUBJECT, HtmlRes.generateTable(title, headers, data)));
    }

    private void simpleNotifySuccess(String email, String title, String message) {
        sendEmail(new Email(email, title, HtmlRes.generateSuccessMessage(title, message)));
    }

    private void simpleNotify(String email, String title, String message) {
        sendEmail(new Email(email, title, HtmlRes.generateInfoMessage(title, message)));
    }

    protected void sendEmailQr(String to, String subject, String html, byte[] imagen) {
        new Thread(() -> new EmailSend().sendEmailWithInlineImage(to, subject, html, imagen, "image/png", CID_QR)).start();
    }

    protected void sendEmail(Email email) {
        Thread thread = new Thread(new EmailSend(email));
        thread.setName("EmailSend-Thread");
        thread.start();
    }
}
