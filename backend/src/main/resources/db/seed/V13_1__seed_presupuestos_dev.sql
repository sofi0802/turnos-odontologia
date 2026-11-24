-- C-19: aranceles y cobertura FICTICIOS para el entorno de desarrollo (perfil dev).
-- Fuente: knowledge-base/04_modelo_de_datos.md, sección "Seed data inicial" (fila "Aranceles y cobertura (C-19)").
-- Nunca usar datos reales de personas. Requiere V2_1 (prestaciones), V12 y V12_1 (obras sociales, planes, códigos y
-- pacientes). Se aplica fuera de orden (out-of-order) después de V22 en las bases de desarrollo que ya existen.
-- ON CONFLICT DO NOTHING deja pasar lo que ya cargó alguien a mano en su base de desarrollo.
-- No crea presupuestos: dependen de la fecha y del odontólogo de la sesión.

-- Precios particulares ficticios, en pesos.
INSERT INTO arancel (prestacion_id, precio, actualizado_en)
SELECT p.id, a.precio, now()
FROM (VALUES ('Consulta o control', 15000.00),
             ('Limpieza (profilaxis)', 25000.00),
             ('Tratamiento de conducto', 90000.00),
             ('Extracción simple', 30000.00)) AS a (prestacion, precio)
JOIN prestacion p ON p.nombre = a.prestacion
ON CONFLICT (prestacion_id) DO NOTHING;

-- V12_1 dejó N-0301 sin prestación: se vincula a "Extracción simple" para que el 70 % de "Plan Integral" y el 80 % de
-- "Plan Oro" para N-0301 se apliquen al presupuestar. Solo si nadie lo vinculó antes.
UPDATE nomenclador
SET prestacion_id = (SELECT id FROM prestacion WHERE nombre = 'Extracción simple')
WHERE codigo = 'N-0301' AND prestacion_id IS NULL;

-- Cobertura por plan y código: "Plan Base" 100 % y 50 %; "Plan Integral" 100 %, 100 % y 70 %; "Plan Oro" 80 % los tres.
INSERT INTO cobertura_plan (plan_id, nomenclador_id, porcentaje)
SELECT pl.id, n.id, c.porcentaje
FROM (VALUES ('Obra Social Ejemplo', 'Plan Base', 'N-0101', 100),
             ('Obra Social Ejemplo', 'Plan Base', 'N-0201', 50),
             ('Obra Social Ejemplo', 'Plan Integral', 'N-0101', 100),
             ('Obra Social Ejemplo', 'Plan Integral', 'N-0201', 100),
             ('Obra Social Ejemplo', 'Plan Integral', 'N-0301', 70),
             ('Prepaga Prueba', 'Plan Oro', 'N-0101', 80),
             ('Prepaga Prueba', 'Plan Oro', 'N-0201', 80),
             ('Prepaga Prueba', 'Plan Oro', 'N-0301', 80)) AS c (obra_social, plan, codigo, porcentaje)
JOIN obra_social o ON o.nombre = c.obra_social
JOIN plan pl ON pl.obra_social_id = o.id AND pl.nombre = c.plan
JOIN nomenclador n ON n.codigo = c.codigo
ON CONFLICT (plan_id, nomenclador_id) DO NOTHING;

-- "Paciente Tres" con "Plan Oro" de "Prepaga Prueba" vigente desde 2026-01-01 y el número PP-000003: así hay en
-- desarrollo un paciente con obra social ("Paciente Uno"), uno con prepaga y uno particular ("Paciente Dos").
INSERT INTO paciente (nombre, apellido, dni) VALUES
    ('Paciente', 'Tres', '99000003')
ON CONFLICT (dni) DO NOTHING;

INSERT INTO afiliacion_paciente (paciente_id, plan_id, obra_social_id, numero_afiliado, vigente_desde)
SELECT pac.id, p.id, p.obra_social_id, 'PP-000003', DATE '2026-01-01'
FROM paciente pac
JOIN plan p ON p.nombre = 'Plan Oro'
JOIN obra_social o ON o.id = p.obra_social_id AND o.nombre = 'Prepaga Prueba'
WHERE pac.dni = '99000003'
ON CONFLICT DO NOTHING;
