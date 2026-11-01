package data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.Date;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

/**
 * DAO para la tabla MENSUALIDAD.
 * Cada cobro es una fila de esta tabla: un arancel UNICO genera una sola cuota.
 */
public class DMensualidad extends DBase {

    public static final String[] HEADERS = { "id", "id_matricula", "concepto", "cuota", "vencimiento",
            "original_Bs", "descuento_Bs", "final_Bs", "pagado_Bs", "saldo_Bs", "estado" };

    public ArrayList<String[]> listPorMatricula(long idMatricula) throws SQLException {
        return consultar("SELECT c.id_mensualidad, c.id_matricula, a.concepto, c.numero_cuota, c.fecha_vencimiento, "
                + "c.monto_original, c.monto_descuento, c.monto_final, "
                + "(SELECT COALESCE(SUM(pg.monto), 0) FROM pago pg WHERE pg.id_mensualidad = c.id_mensualidad "
                + "  AND pg.estado_conciliacion = 'CONFIRMADO'), "
                + "c.monto_final - (SELECT COALESCE(SUM(pg.monto), 0) FROM pago pg "
                + "  WHERE pg.id_mensualidad = c.id_mensualidad AND pg.estado_conciliacion = 'CONFIRMADO'), "
                + "CASE WHEN c.estado = 'PENDIENTE' AND c.fecha_vencimiento < CURRENT_DATE THEN 'VENCIDA' "
                + "  ELSE c.estado END "
                + "FROM mensualidad c JOIN arancel a ON a.id_arancel = c.id_arancel "
                + "WHERE c.id_matricula = ? ORDER BY c.fecha_vencimiento, c.id_mensualidad", idMatricula);
    }

    /** Correo del estudiante dueño de la cuota, o null si la cuota no existe. */
    public String correoDe(long idCuota) throws SQLException {
        List<String[]> filas = consultar("SELECT u.correo FROM mensualidad c "
                + "JOIN matricula ma ON ma.id_matricula = c.id_matricula "
                + "JOIN usuario u ON u.id_usuario = ma.id_usuario WHERE c.id_mensualidad = ?", idCuota);
        return filas.isEmpty() ? null : filas.get(0)[0];
    }

    /** @return { nombre, ci, telefono, correo, concepto, numero_cuota } */
    public String[] getDatosCliente(long idCuota) throws SQLException {
        return consultar("SELECT u.nombres || ' ' || u.apellidos, u.ci, u.telefono, u.correo, a.concepto, c.numero_cuota "
                + "FROM mensualidad c JOIN matricula ma ON ma.id_matricula = c.id_matricula "
                + "JOIN usuario u ON u.id_usuario = ma.id_usuario JOIN arancel a ON a.id_arancel = c.id_arancel "
                + "WHERE c.id_mensualidad = ?", idCuota).get(0);
    }

    /** @return { id_cuota, concepto, numero, vencimiento, monto_final, correo }, o null si no quedan. */
    public String[] siguientePendiente(long idMatricula) throws SQLException {
        List<String[]> filas = consultar("SELECT c.id_mensualidad, a.concepto, c.numero_cuota, c.fecha_vencimiento, "
                + "c.monto_final, u.correo FROM mensualidad c JOIN arancel a ON a.id_arancel = c.id_arancel "
                + "JOIN matricula ma ON ma.id_matricula = c.id_matricula JOIN usuario u ON u.id_usuario = ma.id_usuario "
                + "WHERE c.id_matricula = ? AND c.estado = 'PENDIENTE' AND ma.estado <> 'CANCELADA' "
                + "ORDER BY c.fecha_vencimiento, c.id_mensualidad LIMIT 1", idMatricula);
        return filas.isEmpty() ? null : filas.get(0);
    }

    /** Indica si a la matrícula de esta cuota le quedan otras cuotas sin pagar. */
    public boolean quedanPendientes(long idCuota) throws SQLException {
        return !consultar("SELECT 1 FROM mensualidad c JOIN mensualidad o ON o.id_matricula = c.id_matricula "
                + "WHERE c.id_mensualidad = ? AND o.estado <> 'PAGADA'", idCuota).isEmpty();
    }

    static void insertar(Connection conn, long idMatricula, long idArancel, int numero, Date vencimiento,
            BigDecimal monto) throws SQLException {
        ejecutar(conn, "INSERT INTO mensualidad (id_matricula, id_arancel, numero_cuota, "
                + "fecha_vencimiento, monto_original, monto_final) VALUES (?, ?, ?, ?, ?, ?)",
                idMatricula, idArancel, numero, vencimiento, monto, monto);
    }

    static void eliminarPorMatricula(Connection conn, long idMatricula) throws SQLException {
        ejecutar(conn, "DELETE FROM mensualidad WHERE id_matricula = ?", idMatricula);
    }

    /** @return filas { id_mensualidad, monto_original } */
    static List<String[]> listMontos(Connection conn, long idMatricula) throws SQLException {
        return consultar(conn, "SELECT id_mensualidad, monto_original FROM mensualidad WHERE id_matricula = ?",
                idMatricula);
    }

    static void aplicarRebaja(Connection conn, long idCuota, BigDecimal rebaja, BigDecimal montoFinal)
            throws SQLException {
        ejecutar(conn, "UPDATE mensualidad SET monto_descuento = ?, monto_final = ?, estado = 'PENDIENTE' "
                + "WHERE id_mensualidad = ?", rebaja, montoFinal, idCuota);
    }

    static void marcarPagadasSinSaldo(Connection conn, long idMatricula) throws SQLException {
        ejecutar(conn, "UPDATE mensualidad SET estado = 'PAGADA' WHERE id_matricula = ? AND monto_final = 0",
                idMatricula);
    }

    static void marcarPagada(Connection conn, long idCuota) throws SQLException {
        ejecutar(conn, "UPDATE mensualidad SET estado = 'PAGADA' WHERE id_mensualidad = ?", idCuota);
    }

    /**
     * El bloqueo evita que dos pagos simultáneos superen el saldo de la cuota.
     * @return { id_matricula, id_arancel, estado, monto_final, estado de la matrícula, id_usuario }
     */
    static String[] bloquear(Connection conn, long idCuota) throws SQLException {
        return consultar(conn, "SELECT c.id_matricula, c.id_arancel, c.estado, c.monto_final, ma.estado, "
                + "ma.id_usuario FROM mensualidad c JOIN matricula ma ON ma.id_matricula = c.id_matricula "
                + "WHERE c.id_mensualidad = ? FOR UPDATE OF c", idCuota).get(0);
    }
}
