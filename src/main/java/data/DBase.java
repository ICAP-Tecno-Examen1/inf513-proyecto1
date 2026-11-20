package data;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;

import postgresConecction.DBConnection;
import postgresConecction.SqlConnection;

/**
 * Base de los DAO: conexión y ejecución de SQL con PreparedStatement.
 */
abstract class DBase {

    protected Connection conectar() throws SQLException {
        Connection conn = new SqlConnection(DBConnection.database, DBConnection.server,
                DBConnection.port, DBConnection.user, DBConnection.password).connect();
        if (conn == null) {
            // SqlConnection ya dejó la causa en la consola; el usuario recibe un aviso claro.
            System.err.println("Sin conexión a la base " + DBConnection.database + " en " + DBConnection.server
                    + ":" + DBConnection.port);
            throw new IllegalStateException("La base de datos no está disponible en este momento. "
                    + "Intenta nuevamente en unos minutos.");
        }
        return conn;
    }

    protected ArrayList<String[]> consultar(String sql, Object... params) throws SQLException {
        try (Connection conn = conectar()) {
            return consultar(conn, sql, params);
        }
    }

    protected int ejecutar(String sql, Object... params) throws SQLException {
        try (Connection conn = conectar()) {
            return ejecutar(conn, sql, params);
        }
    }

    protected static ArrayList<String[]> consultar(Connection conn, String sql, Object... params)
            throws SQLException {
        ArrayList<String[]> filas = new ArrayList<>();
        try (PreparedStatement ps = preparar(conn, sql, params);
                ResultSet rs = ps.executeQuery()) {
            int columnas = rs.getMetaData().getColumnCount();
            while (rs.next()) {
                String[] fila = new String[columnas];
                for (int i = 0; i < columnas; i++) {
                    fila[i] = rs.getString(i + 1);
                }
                filas.add(fila);
            }
        }
        return filas;
    }

    protected static int ejecutar(Connection conn, String sql, Object... params) throws SQLException {
        try (PreparedStatement ps = preparar(conn, sql, params)) {
            return ps.executeUpdate();
        }
    }

    /** Ejecuta un INSERT ... RETURNING id y devuelve el id generado. */
    protected static long insertar(Connection conn, String sql, Object... params) throws SQLException {
        return Long.parseLong(consultar(conn, sql, params).get(0)[0]);
    }

    private static PreparedStatement preparar(Connection conn, String sql, Object... params) throws SQLException {
        PreparedStatement ps = conn.prepareStatement(sql);
        for (int i = 0; i < params.length; i++) {
            if (params[i] == null) {
                ps.setNull(i + 1, Types.NULL);
            } else {
                ps.setObject(i + 1, params[i]);
            }
        }
        return ps;
    }

    /** Ejecuta un bloque en una transacción; si falla, deshace todo. */
    protected <T> T enTransaccion(Transaccion<T> bloque) throws SQLException {
        try (Connection conn = conectar()) {
            conn.setAutoCommit(false);
            try {
                T resultado = bloque.ejecutar(conn);
                conn.commit();
                return resultado;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            }
        }
    }

    protected interface Transaccion<T> {
        T ejecutar(Connection conn) throws SQLException;
    }
}
