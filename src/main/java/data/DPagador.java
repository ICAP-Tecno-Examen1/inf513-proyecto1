package data;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;

/**
 * DAO para la tabla PAGADOR: la persona que paga, que puede no ser el estudiante.
 */
public class DPagador extends DBase {

    /** Reutiliza al pagador con el mismo documento o lo registra. @param p { nombres, apellidos, tipo_doc, numero_doc, telefono, correo } */
    static long obtener(Connection conn, String[] p) throws SQLException {
        List<String[]> existente = consultar(conn, "SELECT id_pagador FROM pagador "
                + "WHERE tipo_documento = ? AND numero_documento = ?", p[2], p[3]);
        if (!existente.isEmpty()) {
            return Long.parseLong(existente.get(0)[0]);
        }
        return insertar(conn, "INSERT INTO pagador (nombres, apellidos, tipo_documento, numero_documento, telefono, "
                + "correo) VALUES (?, ?, ?, ?, ?, ?) RETURNING id_pagador", p[0], p[1], p[2], p[3], p[4], p[5]);
    }

    /** Registra como pagador al propio estudiante. */
    static long obtenerDesdeUsuario(Connection conn, long idUsuario) throws SQLException {
        return obtener(conn, consultar(conn, "SELECT nombres, apellidos, tipo_documento, ci, telefono, correo "
                + "FROM usuario WHERE id_usuario = ?", idUsuario).get(0));
    }
}
