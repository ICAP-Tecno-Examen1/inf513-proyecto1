package interfaces;

import librerias.ParamsAction;

/**
 * Interfaz ICasoUsoListener define los métodos para los casos de uso soportados
 * por el Sistema de Gestión Académica ICAP-UAGRM (primer parcial).
 */
public interface ICasoUsoListener {
    // ── Infraestructura ──────────────────────────────────────────────────────
    void error(ParamsAction event);

    void help(ParamsAction event);

    // ── CU1: Gestión de Usuarios ─────────────────────────────────────────────
    void usuario(ParamsAction event);

    void clave(ParamsAction event);

    void rol(ParamsAction event);

    void permiso(ParamsAction event);

    // ── CU2: Catálogo de Programas y Ofertas Académicas ──────────────────────
    void programa(ParamsAction event);

    void malla(ParamsAction event);

    void oferta(ParamsAction event);

    // ── CU3: Preinscripción y Ciclo de Vida del Estudiante ───────────────────
    void preinscripcion(ParamsAction event);

    void matricula(ParamsAction event);

    // ── CU4: Aranceles, Becas y Conciliación de Pagos ────────────────────────
    void arancel(ParamsAction event);

    void mensualidad(ParamsAction event);

    void beca(ParamsAction event);

    void becaEstudiante(ParamsAction event);

    void metodoPago(ParamsAction event);

    void pago(ParamsAction event);

    void pagarQR(ParamsAction event);

    void pedpag(ParamsAction event);

    void pagcuo(ParamsAction event);

    void respag(ParamsAction event);
}
