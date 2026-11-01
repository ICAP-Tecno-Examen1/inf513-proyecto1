package data;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * DAO para la tabla BECA_ESTUDIANTE.
 */
public class DBecaEstudiante extends DBase {

    /** Aplica una beca y recalcula los montos. @return estado resultante de la matrícula. */
    public String asignar(long idMatricula, long idBeca, BigDecimal porcentaje) throws SQLException {
        return enTransaccion(conn -> {
            DMatricula.bloquearParaDescuento(conn, idMatricula);
            if (!consultar(conn, "SELECT 1 FROM beca_estudiante WHERE id_matricula = ? AND estado = 'ACTIVA'",
                    idMatricula).isEmpty()) {
                throw new IllegalStateException("La matrícula ya tiene una beca activa. Quítala primero con DELBES.");
            }
            ejecutar(conn, "INSERT INTO beca_estudiante (id_beca, id_matricula, porcentaje_aplicado) VALUES (?, ?, ?)",
                    idBeca, idMatricula, porcentaje);
            return DMatricula.aplicarDescuento(conn, idMatricula, porcentaje);
        });
    }

    /** Anula la beca activa y restaura los montos. @return estado resultante de la matrícula. */
    public String quitar(long idMatricula) throws SQLException {
        return enTransaccion(conn -> {
            DMatricula.bloquearParaDescuento(conn, idMatricula);
            if (anularPorMatricula(conn, idMatricula) == 0) {
                throw new IllegalStateException("La matrícula no tiene una beca activa.");
            }
            return DMatricula.aplicarDescuento(conn, idMatricula, BigDecimal.ZERO);
        });
    }

    /** @return cantidad de becas activas anuladas. */
    static int anularPorMatricula(Connection conn, long idMatricula) throws SQLException {
        return ejecutar(conn, "UPDATE beca_estudiante SET estado = 'ANULADA' "
                + "WHERE id_matricula = ? AND estado = 'ACTIVA'", idMatricula);
    }
}
