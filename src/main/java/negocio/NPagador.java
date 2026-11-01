package negocio;

import java.util.List;

/**
 * Lógica de negocio para PAGADOR (CU4): valida los datos de quien paga, que puede
 * no ser el estudiante. Se indican a partir de la posición "inicio" del comando.
 */
class NPagador {

    /** @return { nombres, apellidos, tipo_doc, numero_doc, telefono, correo }, o null si paga el estudiante. */
    String[] leerDatos(List<String> params, int inicio) {
        if (Validar.opcional(params, inicio) == null) {
            return null;
        }
        return new String[] {
                Validar.texto(params, inicio, "pagador_nombres"),
                Validar.texto(params, inicio + 1, "pagador_apellidos"),
                Validar.opcionDe(Validar.texto(params, inicio + 2, "pagador_tipo_doc"), "pagador_tipo_doc",
                        "CI", "PASAPORTE", "NIT"),
                Validar.texto(params, inicio + 3, "pagador_num_doc"),
                Validar.opcional(params, inicio + 4),
                Validar.opcional(params, inicio + 5)
        };
    }

    String leerParentesco(List<String> params, int inicio) {
        return Validar.opcional(params, inicio) == null ? "TITULAR" : Validar.texto(params, inicio + 6, "parentesco");
    }
}
