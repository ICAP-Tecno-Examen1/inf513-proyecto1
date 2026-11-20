-- =====================================================================
-- Oferta de prueba de 1 Bs para probar el flujo de pago con Pago Fácil.
-- SOLO para la base local prueba15sa. No ejecutar en la base del ing.
--
-- Crea la oferta OF-PRUEBA-1BS sobre la malla 1 (Diplomado en Educación
-- Superior) con un único arancel UNICO de Bs 1.00: genera una sola cuota.
-- =====================================================================

BEGIN;

WITH oferta AS (
    INSERT INTO oferta_academica (codigo, id_malla, modalidad, fecha_inicio, fecha_fin,
                                  cupo_maximo, cupo_minimo, gestion)
    VALUES ('OF-PRUEBA-1BS', 1, 'VIRTUAL', DATE '2027-03-01', DATE '2027-06-30', 30, 1, 2027)
    RETURNING id_oferta
)
INSERT INTO arancel (id_oferta, concepto, monto, tipo_cobro)
SELECT id_oferta, 'Prueba Pago Fácil', 1.00, 'UNICO' FROM oferta;

COMMIT;

-- Verificación (opcional):
-- SELECT o.id_oferta, o.codigo, o.estado, a.concepto, a.monto, a.tipo_cobro
-- FROM oferta_academica o JOIN arancel a ON a.id_oferta = o.id_oferta
-- WHERE o.codigo = 'OF-PRUEBA-1BS';
