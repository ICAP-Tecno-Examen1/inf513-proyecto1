-- =====================================================================
-- Alta del Director de pruebas (Jazmin Torrez) en la base LOCAL de ICAP.
-- No ejecutar en la base del ing. (db_grupo15sa en www.tecnoweb.org.bo)
--
-- Antes de correrlo:
--   1. La base local ya debe tener database_script.sql y datos_iniciales.sql.
--   2. Si el correo o la CI ya existen, el INSERT falla por unicidad: no
--      hay nada que cambiar, la cuenta ya fue creada.
--
-- Contraseña: 123456789 (hash BCrypt generado con jbcrypt 0.4, el mismo
-- de DUsuario). El username se deja en NULL a propósito.
-- =====================================================================

BEGIN;

WITH nuevo AS (
    INSERT INTO usuario (nombres, apellidos, ci, tipo_documento, correo,
                         telefono, direccion, fecha_nacimiento, password_hash)
    VALUES (
        'Jazmin',
        'Torrez',
        '10307793',
        'CI',
        'jazz.tb.99@gmail.com',
        '61889462',
        'Los Pozos',
        DATE '2000-09-12',
        '$2a$10$8vI1MMbMpvMDbpaMI0kgQOuWUncy.9h7hmmRuMDnCwPgbyGEQh3cq'
    )
    RETURNING id_usuario
)
INSERT INTO usuario_rol (id_usuario, id_rol)
SELECT nuevo.id_usuario, r.id_rol
FROM nuevo, rol r
WHERE r.nombre = 'Director';

COMMIT;

-- Verificación (opcional):
-- SELECT u.id_usuario, u.correo, r.nombre AS rol
-- FROM usuario u
-- JOIN usuario_rol ur ON ur.id_usuario = u.id_usuario
-- JOIN rol r          ON r.id_rol = ur.id_rol
-- WHERE u.correo = 'jazz.tb.99@gmail.com';

-- =====================================================================
-- CONFIGURACIÓN: base del ing vs. base local
-- =====================================================================
--
-- A) BASE DEL INGENIERO (la que se usó hasta ahora)
--    Archivo: src/main/java/postgresConecction/DBConnection.java
--      server   = "www.tecnoweb.org.bo"
--      port     = "5432"
--      database = "db_grupo15sa"
--      user     = "grupo15sa"
--      password = (la de DBConnection.java)
--
--    Buzón POP3 (lectura de correos): EmailReceipt.java
--      HOST = "www.tecnoweb.org.bo", PORT_POP = 110
--      USER = "grupo15sa"
--
--    Envío (SMTP): run.java, USE_GMAIL = true -> Gmail SMTP puerto 465
--      (usuario y clave de aplicación en run.java o en .env).
--
-- B) BASE LOCAL (la que se usará ahora)
--    En DBConnection.java cambiar:
--      server   = "localhost"
--      port     = "5432"
--      database = "<nombre de la base local>"   (ej. icap)
--      user     = "<usuario local de PostgreSQL>"
--      password = "<clave local de PostgreSQL>"
--    El campo url se arma solo a partir de estos valores.
--
--    Pasos para la base local:
--      a) Crear la base:        CREATE DATABASE icap;
--      b) Ejecutar en ella:     database_script.sql, luego datos_iniciales.sql
--      c) Ejecutar este archivo (insertar_director_local.sql)
--
--    Puntos a tener en cuenta:
--      - EmailReceipt sigue apuntando al buzón de tecnoweb. Mientras el
--        sistema corre, lee y BORRA los correos de ese buzón, así que
--        si se prueba en local con el buzón del ing, se consumen sus
--        correos. Para aislar las pruebas, cambiar HOST a "localhost"
--        (o a un servidor de correo de prueba) en EmailReceipt.java.
--      - Las pruebas de JUnit (mvn test) usan PostgreSQL embebido y no
--        dependen de ninguna de las dos configuraciones.
--      - Los datos de conexión no deben subirse al repositorio: lo
--        ideal es moverlos a .env (que ya está en .gitignore).
-- =====================================================================
