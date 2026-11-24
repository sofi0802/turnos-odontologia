-- C-43 registro-asistencia (RN-AG-20, design D1 y D2).
-- 1. Hora de llegada del paciente. Es una columna y no un estado: el turno sigue RESERVADO o CONFIRMADO, así las
--    restricciones EXCLUDE de V4 lo siguen contando sin tocarlas (regla dura 5). Admite nulos: los turnos
--    existentes no cambian.
ALTER TABLE turno ADD COLUMN llegada_en TIMESTAMPTZ;

-- 2. Índice parcial para la tarea que pasa a ATENDIDO los turnos con llegada cuyo fin con margen ya pasó.
CREATE INDEX turno_con_llegada_activo ON turno (fin_con_margen)
    WHERE llegada_en IS NOT NULL AND estado IN ('RESERVADO', 'CONFIRMADO');

-- 3. Auditoría: LLEGADA, ATENDIDO y DESHACER_AUSENCIA. Copia ENTERA la lista de V7 y V8 (con CONFIRMAR y AUSENTE),
--    así el orden entre migraciones no importa. Una migración futura que sume otra acción tiene que copiar esta.
ALTER TABLE turno_auditoria DROP CONSTRAINT IF EXISTS turno_auditoria_accion_valida;
ALTER TABLE turno_auditoria ADD CONSTRAINT turno_auditoria_accion_valida
    CHECK (accion IN ('CREAR', 'CANCELAR', 'REPROGRAMAR', 'SOBRETURNO', 'EXTENDER', 'CONFIRMAR', 'AUSENTE',
                      'LLEGADA', 'ATENDIDO', 'DESHACER_AUSENCIA'));
