package data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla PAGO: registro y conciliación de pagos.
 */
public class DPago extends DBase {

    public static final String[] HEADERS = { "id", "matricula", "estudiante", "correo", "concepto", "cuota", "metodo",
            "monto_Bs", "fecha", "referencia", "comprobante", "pagador", "parentesco", "estado" };

    private static final String SELECT = "SELECT pg.id_pago, ma.numero_registro, u.nombres || ' ' || u.apellidos, u.correo, "
            + "a.concepto, c.numero_cuota, me.nombre, pg.monto, to_char(pg.fecha, 'YYYY-MM-DD HH24:MI'), pg.referencia, "
            + "pg.comprobante_url, pa.nombres || ' ' || pa.apellidos, pg.parentesco_pagador, pg.estado_conciliacion "
            + "FROM pago pg JOIN matricula ma ON ma.id_matricula = pg.id_matricula "
            + "JOIN usuario u ON u.id_usuario = ma.id_usuario JOIN arancel a ON a.id_arancel = pg.id_arancel "
            + "LEFT JOIN mensualidad c ON c.id_mensualidad = pg.id_mensualidad "
            + "JOIN metodo_pago me ON me.id_metodo_pago = pg.id_metodo_pago "
            + "JOIN pagador pa ON pa.id_pagador = pg.id_pagador ";

    public List<String[]> get(long id) throws SQLException {
        return consultar(SELECT + "WHERE pg.id_pago = ?", id);
    }

    public ArrayList<String[]> listPorCorreo(String correo, String estado) throws SQLException {
        return consultar(SELECT + "WHERE lower(u.correo) = lower(?) AND pg.estado_conciliacion = COALESCE(?, "
                + "pg.estado_conciliacion) ORDER BY pg.id_pago DESC", correo, estado);
    }

    public ArrayList<String[]> listTodos(String estado) throws SQLException {
        return consultar(SELECT + "WHERE pg.estado_conciliacion = COALESCE(?, pg.estado_conciliacion) "
                + "ORDER BY pg.id_pago DESC", estado);
    }

    /** @return { id_mensualidad, monto, referencia, estado, id_metodo_pago }, o null si no existe. */
    public String[] getParaQr(long idPago) throws SQLException {
        List<String[]> filas = consultar("SELECT id_mensualidad, monto, referencia, estado_conciliacion, "
                + "id_metodo_pago FROM pago WHERE id_pago = ?", idPago);
        return filas.isEmpty() ? null : filas.get(0);
    }

    public Long pendientePorCuotaYMetodo(long idCuota, long idMetodo) throws SQLException {
        List<String[]> filas = consultar("SELECT id_pago FROM pago WHERE id_mensualidad = ? AND id_metodo_pago = ? "
                + "AND estado_conciliacion = 'PENDIENTE' ORDER BY id_pago DESC LIMIT 1", idCuota, idMetodo);
        return filas.isEmpty() ? null : Long.valueOf(filas.get(0)[0]);
    }

    /** Monto de la cuota sin pagos pendientes ni confirmados. */
    public BigDecimal saldoDisponible(long idCuota) throws SQLException {
        return new BigDecimal(consultar("SELECT c.monto_final - COALESCE((SELECT SUM(p.monto) FROM pago p "
                + "WHERE p.id_mensualidad = c.id_mensualidad AND p.estado_conciliacion IN ('PENDIENTE', 'CONFIRMADO')), 0) "
                + "FROM mensualidad c WHERE c.id_mensualidad = ?", idCuota).get(0)[0]);
    }

    /**
     * Registra un pago en estado PENDIENTE.
     * @param pagador { nombres, apellidos, tipo_doc, numero_doc, telefono, correo }; null si paga el estudiante
     */
    public long registrar(long idCuota, long idMetodo, BigDecimal monto, String referencia, String comprobante,
            String[] pagador, String parentesco) throws SQLException {
        return enTransaccion(conn -> {
            String[] cuota = DMensualidad.bloquear(conn, idCuota);
            if ("CANCELADA".equals(cuota[4])) {
                throw new IllegalStateException("La matrícula está cancelada.");
            }
            if ("PAGADA".equals(cuota[2])) {
                throw new IllegalStateException("Esta cuota ya está pagada.");
            }
            BigDecimal comprometido = new BigDecimal(consultar(conn, "SELECT COALESCE(SUM(monto), 0) FROM pago "
                    + "WHERE id_mensualidad = ? AND estado_conciliacion IN ('PENDIENTE', 'CONFIRMADO')",
                    idCuota).get(0)[0]);
            BigDecimal saldo = new BigDecimal(cuota[3]).subtract(comprometido);
            if (monto.compareTo(saldo) > 0) {
                throw new IllegalArgumentException("El monto supera el saldo disponible de la cuota: Bs " + saldo
                        + " (considera pagos aún sin verificar).");
            }
            if (!consultar(conn, "SELECT 1 FROM pago WHERE id_metodo_pago = ? AND upper(referencia) = upper(?) "
                    + "AND estado_conciliacion <> 'RECHAZADO'", idMetodo, referencia).isEmpty()) {
                throw new IllegalArgumentException("Ya existe un pago registrado con la referencia '" + referencia + "'.");
            }

            long idPagador = pagador != null
                    ? DPagador.obtener(conn, pagador)
                    : DPagador.obtenerDesdeUsuario(conn, Long.parseLong(cuota[5]));

            return insertar(conn, "INSERT INTO pago (id_matricula, id_arancel, id_mensualidad, id_metodo_pago, "
                    + "id_pagador, monto, referencia, comprobante_url, parentesco_pagador) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?) RETURNING id_pago",
                    Long.parseLong(cuota[0]), Long.parseLong(cuota[1]), idCuota, idMetodo, idPagador, monto,
                    referencia, comprobante, parentesco);
        });
    }

    /**
     * Confirma un pago, actualiza su cuota y la matrícula.
     * @return { correo del estudiante, numero_registro, estado de la matrícula, estado de la cuota }
     */
    public String[] confirmar(long idPago) throws SQLException {
        return enTransaccion(conn -> {
            String[] pago = bloquearPendiente(conn, idPago);
            long idMatricula = Long.parseLong(pago[0]);
            long idCuota = Long.parseLong(pago[1]);
            String[] cuota = DMensualidad.bloquear(conn, idCuota);

            ejecutar(conn, "UPDATE pago SET estado_conciliacion = 'CONFIRMADO' WHERE id_pago = ?", idPago);

            BigDecimal pagado = new BigDecimal(consultar(conn, "SELECT COALESCE(SUM(monto), 0) FROM pago "
                    + "WHERE id_mensualidad = ? AND estado_conciliacion = 'CONFIRMADO'", idCuota).get(0)[0]);
            String estadoCuota = "PENDIENTE";
            if (pagado.compareTo(new BigDecimal(cuota[3])) >= 0) {
                estadoCuota = "PAGADA";
                DMensualidad.marcarPagada(conn, idCuota);
            }
            String estadoMatricula = DMatricula.recalcularEstado(conn, idMatricula);
            String[] ma = consultar(conn, "SELECT u.correo, ma.numero_registro FROM matricula ma "
                    + "JOIN usuario u ON u.id_usuario = ma.id_usuario WHERE ma.id_matricula = ?", idMatricula).get(0);
            return new String[] { ma[0], ma[1], estadoMatricula, estadoCuota };
        });
    }

    /** Rechaza un pago pendiente. @return correo del estudiante. */
    public String rechazar(long idPago) throws SQLException {
        return enTransaccion(conn -> {
            String[] pago = bloquearPendiente(conn, idPago);
            ejecutar(conn, "UPDATE pago SET estado_conciliacion = 'RECHAZADO' WHERE id_pago = ?", idPago);
            return consultar(conn, "SELECT u.correo FROM matricula ma JOIN usuario u ON u.id_usuario = ma.id_usuario "
                    + "WHERE ma.id_matricula = ?", Long.parseLong(pago[0])).get(0)[0];
        });
    }

    private String[] bloquearPendiente(Connection conn, long idPago) throws SQLException {
        List<String[]> filas = consultar(conn, "SELECT id_matricula, id_mensualidad, estado_conciliacion FROM pago "
                + "WHERE id_pago = ? FOR UPDATE", idPago);
        if (filas.isEmpty()) {
            throw new IllegalArgumentException("El pago " + idPago + " no existe.");
        }
        if (!"PENDIENTE".equals(filas.get(0)[2])) {
            throw new IllegalStateException("El pago ya está " + filas.get(0)[2] + ".");
        }
        return filas.get(0);
    }

    static boolean tieneVigentes(Connection conn, long idMatricula) throws SQLException {
        return !consultar(conn, "SELECT 1 FROM pago WHERE id_matricula = ? "
                + "AND estado_conciliacion IN ('PENDIENTE', 'CONFIRMADO')", idMatricula).isEmpty();
    }
}
