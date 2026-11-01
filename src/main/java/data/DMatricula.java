package data;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla MATRICULA. Coordina las transacciones que crean, cancelan o
 * recalculan una matrícula junto con sus tablas relacionadas.
 */
public class DMatricula extends DBase {

    public static final String[] HEADERS = { "id", "numero_registro", "estudiante", "ci", "correo", "oferta", "programa",
            "fecha", "estado", "tipo_cobro", "original_Bs", "descuento_Bs", "final_Bs", "beca" };

    private static final String SELECT = "SELECT ma.id_matricula, ma.numero_registro, u.nombres || ' ' || u.apellidos, "
            + "u.ci, u.correo, o.codigo, p.nombre, ma.fecha, ma.estado, ma.tipo_cobro, ma.monto_original, "
            + "ma.monto_descuento, ma.monto_final, "
            + "(SELECT b.nombre || ' (' || be.porcentaje_aplicado || '%)' FROM beca_estudiante be "
            + " JOIN beca b ON b.id_beca = be.id_beca WHERE be.id_matricula = ma.id_matricula AND be.estado = 'ACTIVA') "
            + "FROM matricula ma JOIN usuario u ON u.id_usuario = ma.id_usuario "
            + "JOIN oferta_academica o ON o.id_oferta = ma.id_oferta "
            + "JOIN malla_curricular m ON m.id_malla = o.id_malla "
            + "JOIN programa p ON p.id_programa = m.id_programa ";

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "WHERE ma.id_matricula = ?", id);
    }

    public ArrayList<String[]> listPorCorreo(String correo) throws SQLException {
        return consultar(SELECT + "WHERE lower(u.correo) = lower(?) ORDER BY ma.id_matricula DESC", correo);
    }

    public ArrayList<String[]> listTodas() throws SQLException {
        return consultar(SELECT + "ORDER BY ma.id_matricula DESC");
    }

    /**
     * Aprueba la preinscripción: reserva el cupo, crea la matrícula en
     * PENDIENTE_PAGO y genera sus cuotas. @return id de la matrícula.
     */
    public long aprobar(long idPreinscripcion, String observacion) throws SQLException {
        return enTransaccion(conn -> {
            String[] pre = DPreinscripcion.bloquear(conn, idPreinscripcion);
            if (!"PENDIENTE".equals(pre[2])) {
                throw new IllegalStateException("La preinscripción ya está " + pre[2] + ".");
            }
            long idUsuario = Long.parseLong(pre[0]);
            long idOferta = Long.parseLong(pre[1]);

            String[] oferta = DOferta.bloquear(conn, idOferta);
            if (!"ABIERTA".equals(oferta[0])) {
                throw new IllegalStateException("La oferta ya no está abierta.");
            }
            int ocupados = Integer.parseInt(consultar(conn, "SELECT count(*) FROM matricula "
                    + "WHERE id_oferta = ? AND estado <> 'CANCELADA'", idOferta).get(0)[0]);
            if (ocupados >= Integer.parseInt(oferta[3])) {
                throw new IllegalStateException("La oferta ya no tiene cupos disponibles.");
            }
            List<String[]> aranceles = DArancel.listActivos(conn, idOferta);
            if (aranceles.isEmpty()) {
                throw new IllegalStateException("La oferta no tiene aranceles definidos. "
                        + "El Coordinador debe registrarlos con INSARA.");
            }

            LocalDate inicio = LocalDate.parse(oferta[1]);
            int meses = Integer.parseInt(oferta[4]);
            BigDecimal total = BigDecimal.ZERO;
            String tipoCobro = "UNICO";
            for (String[] arancel : aranceles) {
                boolean mensual = "MENSUAL".equals(arancel[2]);
                total = total.add(new BigDecimal(arancel[1]).multiply(BigDecimal.valueOf(mensual ? meses : 1)));
                if (mensual) {
                    tipoCobro = "MENSUAL";
                }
            }

            long idMatricula = crearOReactivar(conn, idUsuario, idOferta, oferta[2], tipoCobro, total);

            for (String[] arancel : aranceles) {
                int cuotas = "MENSUAL".equals(arancel[2]) ? meses : 1;
                for (int n = 1; n <= cuotas; n++) {
                    DMensualidad.insertar(conn, idMatricula, Long.parseLong(arancel[0]), n,
                            Date.valueOf(inicio.plusMonths(n - 1L)), new BigDecimal(arancel[1]));
                }
            }

            DPreinscripcion.marcarAprobada(conn, idPreinscripcion, observacion);
            recalcularEstado(conn, idMatricula);
            return idMatricula;
        });
    }

    // UNIQUE (id_usuario, id_oferta): si la persona canceló antes, se reutiliza su matrícula.
    private long crearOReactivar(Connection conn, long idUsuario, long idOferta, String gestion, String tipoCobro,
            BigDecimal total) throws SQLException {
        List<String[]> anterior = consultar(conn, "SELECT id_matricula FROM matricula "
                + "WHERE id_usuario = ? AND id_oferta = ? AND estado = 'CANCELADA'", idUsuario, idOferta);
        if (!anterior.isEmpty()) {
            long id = Long.parseLong(anterior.get(0)[0]);
            DMensualidad.eliminarPorMatricula(conn, id);
            ejecutar(conn, "UPDATE matricula SET estado = 'PENDIENTE_PAGO', fecha = CURRENT_DATE, tipo_cobro = ?, "
                    + "monto_original = ?, monto_descuento = 0, monto_final = ? WHERE id_matricula = ?",
                    tipoCobro, total, total, id);
            return id;
        }
        long id = Long.parseLong(consultar(conn,
                "SELECT nextval(pg_get_serial_sequence('matricula', 'id_matricula'))").get(0)[0]);
        ejecutar(conn, "INSERT INTO matricula (id_matricula, id_usuario, id_oferta, estado, numero_registro, "
                + "tipo_cobro, monto_original, monto_final) VALUES (?, ?, ?, 'PENDIENTE_PAGO', ?, ?, ?, ?)",
                id, idUsuario, idOferta, "ICAP-" + gestion + "-" + String.format("%05d", id), tipoCobro, total, total);
        return id;
    }

    /** Cancela una preinscripción y, si fue aprobada, libera su matrícula y su cupo. */
    public void cancelar(long idPreinscripcion) throws SQLException {
        enTransaccion(conn -> {
            String[] pre = DPreinscripcion.bloquear(conn, idPreinscripcion);
            String estado = pre[2];
            if (!"PENDIENTE".equals(estado) && !"APROBADA".equals(estado)) {
                throw new IllegalStateException("La preinscripción ya está " + estado + ".");
            }
            if ("APROBADA".equals(estado)) {
                long idMatricula = Long.parseLong(consultar(conn, "SELECT id_matricula FROM matricula "
                        + "WHERE id_usuario = ? AND id_oferta = ? FOR UPDATE",
                        Long.parseLong(pre[0]), Long.parseLong(pre[1])).get(0)[0]);
                if (DPago.tieneVigentes(conn, idMatricula)) {
                    throw new IllegalStateException("No se puede cancelar: la matrícula tiene pagos "
                            + "pendientes o confirmados.");
                }
                DBecaEstudiante.anularPorMatricula(conn, idMatricula);
                ejecutar(conn, "UPDATE matricula SET estado = 'CANCELADA' WHERE id_matricula = ?", idMatricula);
            }
            DPreinscripcion.marcarCancelada(conn, idPreinscripcion);
            return idPreinscripcion;
        });
    }

    /** Verifica que a la matrícula se le pueda cambiar la beca y la bloquea. */
    static void bloquearParaDescuento(Connection conn, long idMatricula) throws SQLException {
        List<String[]> ma = consultar(conn, "SELECT estado FROM matricula WHERE id_matricula = ? FOR UPDATE",
                idMatricula);
        if (ma.isEmpty()) {
            throw new IllegalArgumentException("La matrícula " + idMatricula + " no existe.");
        }
        if ("CANCELADA".equals(ma.get(0)[0])) {
            throw new IllegalStateException("La matrícula está cancelada.");
        }
        if (DPago.tieneVigentes(conn, idMatricula)) {
            throw new IllegalStateException("No se puede cambiar la beca: la matrícula ya tiene pagos "
                    + "pendientes o confirmados.");
        }
    }

    /** Recalcula cuotas y totales con el porcentaje indicado. @return estado de la matrícula. */
    static String aplicarDescuento(Connection conn, long idMatricula, BigDecimal porcentaje) throws SQLException {
        BigDecimal original = BigDecimal.ZERO;
        BigDecimal descuento = BigDecimal.ZERO;
        for (String[] cuota : DMensualidad.listMontos(conn, idMatricula)) {
            BigDecimal monto = new BigDecimal(cuota[1]);
            BigDecimal rebaja = monto.multiply(porcentaje).divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
            DMensualidad.aplicarRebaja(conn, Long.parseLong(cuota[0]), rebaja, monto.subtract(rebaja));
            original = original.add(monto);
            descuento = descuento.add(rebaja);
        }
        ejecutar(conn, "UPDATE matricula SET monto_descuento = ?, monto_final = ?, estado = 'PENDIENTE_PAGO' "
                + "WHERE id_matricula = ?", descuento, original.subtract(descuento), idMatricula);
        return recalcularEstado(conn, idMatricula);
    }

    /**
     * Las cuotas en Bs 0 quedan pagadas. La matrícula se confirma cuando están pagados
     * los cobros únicos y la primera cuota de cada arancel mensual.
     * @return estado resultante de la matrícula.
     */
    static String recalcularEstado(Connection conn, long idMatricula) throws SQLException {
        DMensualidad.marcarPagadasSinSaldo(conn, idMatricula);
        ejecutar(conn, "UPDATE matricula ma SET estado = 'CONFIRMADA' WHERE ma.id_matricula = ? "
                + "AND ma.estado = 'PENDIENTE_PAGO' AND NOT EXISTS (SELECT 1 FROM mensualidad c "
                + "  JOIN arancel a ON a.id_arancel = c.id_arancel WHERE c.id_matricula = ma.id_matricula "
                + "  AND c.estado <> 'PAGADA' AND (a.tipo_cobro = 'UNICO' OR c.numero_cuota = 1))", idMatricula);
        return consultar(conn, "SELECT estado FROM matricula WHERE id_matricula = ?", idMatricula).get(0)[0];
    }
}
