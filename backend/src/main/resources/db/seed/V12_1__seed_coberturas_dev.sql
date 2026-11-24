-- C-18: coberturas FICTICIAS para el entorno de desarrollo (perfil dev).
-- Fuente: knowledge-base/04_modelo_de_datos.md, sección "Seed data inicial". Nunca usar datos reales de personas.
-- Se aplica fuera de orden (out-of-order) después de V22 en las bases de desarrollo que ya existen.
-- ON CONFLICT DO NOTHING deja pasar lo que ya cargó alguien a mano en su base de desarrollo.

INSERT INTO obra_social (nombre, sigla, tipo) VALUES
    ('Obra Social Ejemplo', 'OSE', 'OBRA_SOCIAL'),
    ('Prepaga Prueba', NULL, 'PREPAGA')
ON CONFLICT DO NOTHING;

INSERT INTO plan (obra_social_id, nombre)
SELECT o.id, p.nombre
FROM (VALUES ('Obra Social Ejemplo', 'Plan Base'),
             ('Obra Social Ejemplo', 'Plan Integral'),
             ('Prepaga Prueba', 'Plan Oro')) AS p (obra_social, nombre)
JOIN obra_social o ON o.nombre = p.obra_social
ON CONFLICT DO NOTHING;

-- N-0101 y N-0201 se vinculan a su prestación por nombre; N-0301 queda sin vínculo, como lo enumera la KB.
INSERT INTO nomenclador (codigo, descripcion, prestacion_id) VALUES
    ('N-0101', 'Consulta', (SELECT id FROM prestacion WHERE nombre = 'Consulta o control')),
    ('N-0201', 'Limpieza', (SELECT id FROM prestacion WHERE nombre = 'Limpieza (profilaxis)')),
    ('N-0301', 'Extracción simple', NULL)
ON CONFLICT DO NOTHING;

-- El seed de desarrollo no crea pacientes: se agregan dos ficticios, uno con cobertura y otro particular.
INSERT INTO paciente (nombre, apellido, dni) VALUES
    ('Paciente', 'Uno', '99000001'),
    ('Paciente', 'Dos', '99000002')
ON CONFLICT (dni) DO NOTHING;

-- "Paciente Uno" con "Plan Base" vigente desde 2026-01-01 y el número OSE-000001; "Paciente Dos" queda particular.
INSERT INTO afiliacion_paciente (paciente_id, plan_id, obra_social_id, numero_afiliado, vigente_desde)
SELECT pac.id, p.id, p.obra_social_id, 'OSE-000001', DATE '2026-01-01'
FROM paciente pac
JOIN plan p ON p.nombre = 'Plan Base'
JOIN obra_social o ON o.id = p.obra_social_id AND o.nombre = 'Obra Social Ejemplo'
WHERE pac.dni = '99000001'
ON CONFLICT DO NOTHING;
