-- Se ejecuta una sola vez, cuando PostgreSQL inicializa el volumen por primera vez
-- (docker-entrypoint-initdb.d). Crea la base de los tests de integración, que vacían las tablas y por
-- eso nunca deben apuntar a la base de desarrollo (turnos). Flyway crea las tablas al correr los tests.
-- Si el volumen ya existía, la base no se crea sola: ver "Base de datos y tests de integración" en el README.
CREATE DATABASE turnos_test;
