-- ============================================================
-- DATOS INICIALES - SISTEMA ICAP (ejecutar después de database_script.sql)
-- ANTES DE EJECUTAR: pon tus correos en lugar de los @ejemplo.com (más abajo, en
-- "Cuentas del personal"). El sistema identifica a cada persona por el correo desde
-- el que escribe, y cada rol necesita un correo distinto. Los estudiantes se registran
-- solos con INSUSU.
-- Contraseña inicial de las cuentas: Icap2026*  (cámbiala con MODCLA)
-- ============================================================
SET search_path TO public;
SET client_encoding TO 'UTF8';

BEGIN;

INSERT INTO ROL (nombre, descripcion) VALUES
    ('Director', 'Administra usuarios, roles y supervisa el sistema'),
    ('Secretaria', 'Revisa solicitudes y verifica pagos'),
    ('Coordinador Académico', 'Gestiona programas, ofertas, aranceles y becas'),
    ('Profesor', 'Sin funciones académicas principales en este parcial'),
    ('Estudiante', 'Se preinscribe, paga y consulta su matrícula');

INSERT INTO PERMISO (accion, descripcion, modulo) VALUES
    ('USUARIO_GESTIONAR', 'Registrar, modificar y desactivar usuarios', 'CU1'),
    ('USUARIO_VER', 'Consultar datos de otros usuarios', 'CU1'),
    ('ROL_GESTIONAR', 'Asignar roles y permisos', 'CU1'),
    ('CATALOGO_GESTIONAR', 'Gestionar programas, mallas, ofertas y aranceles', 'CU2'),
    ('PREINSCRIPCION_CREAR', 'Preinscribirse en una oferta', 'CU3'),
    ('PREINSCRIPCION_REVISAR', 'Aprobar o rechazar preinscripciones', 'CU3'),
    ('INSCRIPCION_VER', 'Consultar matrículas de todos los estudiantes', 'CU3'),
    ('BECA_GESTIONAR', 'Registrar becas', 'CU4'),
    ('BECA_ASIGNAR', 'Aplicar o quitar becas a una matrícula', 'CU4'),
    ('PAGO_REGISTRAR', 'Registrar pagos', 'CU4'),
    ('PAGO_CONFIRMAR', 'Registrar efectivo y confirmar o rechazar pagos', 'CU4'),
    ('PAGO_VER', 'Consultar pagos de todos los estudiantes', 'CU4');

INSERT INTO ROL_PERMISO (id_rol, id_permiso)
SELECT r.id_rol, p.id_permiso FROM ROL r JOIN PERMISO p ON (r.nombre, p.accion) IN (
    ('Director', 'USUARIO_GESTIONAR'), ('Director', 'USUARIO_VER'), ('Director', 'ROL_GESTIONAR'),
    ('Director', 'CATALOGO_GESTIONAR'), ('Director', 'INSCRIPCION_VER'), ('Director', 'BECA_GESTIONAR'),
    ('Director', 'PAGO_VER'),
    ('Secretaria', 'USUARIO_VER'), ('Secretaria', 'PREINSCRIPCION_REVISAR'), ('Secretaria', 'INSCRIPCION_VER'),
    ('Secretaria', 'BECA_ASIGNAR'), ('Secretaria', 'PAGO_REGISTRAR'), ('Secretaria', 'PAGO_CONFIRMAR'),
    ('Secretaria', 'PAGO_VER'),
    ('Coordinador Académico', 'USUARIO_VER'), ('Coordinador Académico', 'CATALOGO_GESTIONAR'),
    ('Coordinador Académico', 'PREINSCRIPCION_REVISAR'), ('Coordinador Académico', 'INSCRIPCION_VER'),
    ('Coordinador Académico', 'BECA_GESTIONAR'), ('Coordinador Académico', 'BECA_ASIGNAR'),
    ('Coordinador Académico', 'PAGO_CONFIRMAR'), ('Coordinador Académico', 'PAGO_REGISTRAR'),
    ('Coordinador Académico', 'PAGO_VER'),
    ('Estudiante', 'PREINSCRIPCION_CREAR'), ('Estudiante', 'PAGO_REGISTRAR')
);

INSERT INTO METODO_PAGO (nombre, descripcion) VALUES
    ('QR', 'Paga al QR institucional del ICAP y registra la referencia con INSPAG'),
    ('EFECTIVO', 'Paga en Secretaría; el personal autorizado registra el recibo'),
    ('QR Pago Facil', 'Genera tu QR con PAGCUO; el pago se confirma automáticamente');

INSERT INTO BECA (nombre, descripcion, porcentaje, institucion_financiadora) VALUES
    ('Beca Docente UAGRM', 'Para docentes de la UAGRM', 30.00, 'UAGRM'),
    ('Beca Excelencia', 'Por excelencia académica', 50.00, 'ICAP');

-- Cuentas del personal. Los estudiantes se registran solos con INSUSU.
INSERT INTO USUARIO (nombres, apellidos, ci, tipo_documento, correo, username, password_hash) VALUES
    ('Ana', 'Directora', '1000001', 'CI', 'director@ejemplo.com', 'director', '$2a$10$NQpwJensQsyEk5F0tIYgru0feUgrUdHVixuYbrbGLXFScuuSlE1WO'),
    ('Sara', 'Secretaria', '1000002', 'CI', 'secretaria@ejemplo.com', 'secretaria', '$2a$10$NQpwJensQsyEk5F0tIYgru0feUgrUdHVixuYbrbGLXFScuuSlE1WO'),
    ('Carlos', 'Coordinador', '1000003', 'CI', 'coordinador@ejemplo.com', 'coordinador', '$2a$10$NQpwJensQsyEk5F0tIYgru0feUgrUdHVixuYbrbGLXFScuuSlE1WO'),
    ('Pablo', 'Profesor', '1000004', 'CI', 'profesor@ejemplo.com', 'profesor', '$2a$10$NQpwJensQsyEk5F0tIYgru0feUgrUdHVixuYbrbGLXFScuuSlE1WO');

INSERT INTO USUARIO_ROL (id_usuario, id_rol)
SELECT u.id_usuario, r.id_rol FROM USUARIO u JOIN ROL r ON (u.username, r.nombre) IN (
    ('director', 'Director'), ('secretaria', 'Secretaria'),
    ('coordinador', 'Coordinador Académico'), ('profesor', 'Profesor')
);

-- Catálogo de ejemplo: un programa de pago único y otro con mensualidades.
INSERT INTO PROGRAMA (codigo, nombre, descripcion, tipo) VALUES
    ('DIP-EDS', 'Diplomado en Educación Superior', 'Formación en docencia universitaria', 'DIPLOMADO'),
    ('MAE-GPU', 'Maestría en Gestión Pública', 'Gestión y políticas públicas', 'MAESTRIA');

INSERT INTO MALLA_CURRICULAR (id_programa, version, horas_totales, fecha_vigencia)
SELECT id_programa, '2027-V1', CASE codigo WHEN 'DIP-EDS' THEN 240 ELSE 960 END, DATE '2027-01-01' FROM PROGRAMA;

INSERT INTO OFERTA_ACADEMICA (codigo, id_malla, modalidad, fecha_inicio, fecha_fin, cupo_maximo, cupo_minimo, gestion)
SELECT CASE p.codigo WHEN 'DIP-EDS' THEN 'DIP-EDS-2027-1' ELSE 'MAE-GPU-2027-1' END, m.id_malla,
       CASE p.codigo WHEN 'DIP-EDS' THEN 'VIRTUAL' ELSE 'PRESENCIAL' END, DATE '2027-03-01',
       CASE p.codigo WHEN 'DIP-EDS' THEN DATE '2027-06-30' ELSE DATE '2028-02-29' END, 30, 5, 2027
FROM MALLA_CURRICULAR m JOIN PROGRAMA p ON p.id_programa = m.id_programa;

INSERT INTO ARANCEL (id_oferta, concepto, monto, tipo_cobro)
SELECT id_oferta, 'Colegiatura del diplomado', 3000.00, 'UNICO' FROM OFERTA_ACADEMICA WHERE codigo = 'DIP-EDS-2027-1'
UNION ALL
SELECT id_oferta, 'Mensualidad de maestría', 1000.00, 'MENSUAL' FROM OFERTA_ACADEMICA WHERE codigo = 'MAE-GPU-2027-1';

COMMIT;
