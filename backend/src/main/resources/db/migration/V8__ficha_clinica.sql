-- C-13 ficha-clinica-odontograma: ficha básica y odontograma FDI con historial de solo agregado (RN-CL-01,
-- Ley 26.529), y la acción AUSENTE en la auditoría de turnos. Son datos de salud: no van a logs ni a errores.

-- 1. Ficha básica: una fila por paciente que se reemplaza (design D2).
CREATE TABLE ficha_clinica (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id     BIGINT        NOT NULL UNIQUE REFERENCES paciente (id),
    antecedentes    VARCHAR(4000),
    alergias        VARCHAR(4000),
    medicacion      VARCHAR(4000),
    actualizado_por VARCHAR(120)  NOT NULL,
    actualizado_en  TIMESTAMPTZ   NOT NULL
);

-- 2. Odontograma: cada cambio es un registro nuevo; nunca se modifica ni se borra (design D3).
--    Va contra paciente y no contra ficha_clinica, para no exigir la ficha básica antes.
CREATE TABLE odontograma_registro (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    paciente_id    BIGINT      NOT NULL REFERENCES paciente (id),
    pieza          SMALLINT    NOT NULL,
    cara           VARCHAR(1),
    estado         VARCHAR(20) NOT NULL,
    profesional_id BIGINT      NOT NULL REFERENCES profesional (id),
    registrado_en  TIMESTAMPTZ NOT NULL,

    -- Notación FDI: permanentes 11-18, 21-28, 31-38, 41-48; temporales 51-55, 61-65, 71-75, 81-85.
    CONSTRAINT odontograma_pieza_fdi CHECK (
        pieza BETWEEN 11 AND 18 OR pieza BETWEEN 21 AND 28 OR pieza BETWEEN 31 AND 38 OR pieza BETWEEN 41 AND 48
        OR pieza BETWEEN 51 AND 55 OR pieza BETWEEN 61 AND 65 OR pieza BETWEEN 71 AND 75 OR pieza BETWEEN 81 AND 85),
    -- Sin cara es la pieza completa.
    CONSTRAINT odontograma_cara_valida CHECK (cara IN ('O', 'M', 'D', 'V', 'L', 'P')),
    -- Lingual solo en piezas inferiores (cuadrantes 3, 4, 7 y 8); palatina solo en superiores (1, 2, 5 y 6).
    CONSTRAINT odontograma_cara_lingual CHECK (cara <> 'L' OR pieza / 10 IN (3, 4, 7, 8)),
    CONSTRAINT odontograma_cara_palatina CHECK (cara <> 'P' OR pieza / 10 IN (1, 2, 5, 6)),
    CONSTRAINT odontograma_estado_valido CHECK (
        estado IN ('SANO', 'CARIES', 'OBTURADO', 'A_TRATAR', 'AUSENTE', 'CORONA', 'ENDODONCIA'))
);
CREATE INDEX odontograma_registro_vigente_idx ON odontograma_registro (paciente_id, pieza, cara, registrado_en DESC);

-- Solo agregado también en la base. El TRUNCATE de los tests no dispara triggers de fila.
CREATE FUNCTION odontograma_solo_agregado() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION 'El odontograma es de solo agregado: no se modifican ni se borran registros';
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER odontograma_registro_solo_agregado
    BEFORE UPDATE OR DELETE ON odontograma_registro
    FOR EACH ROW EXECUTE FUNCTION odontograma_solo_agregado();

-- 3. Auditoría: la ausencia del paciente registra AUSENTE (RN-AG-17). Es la misma lista que deja la V7 de C-11,
--    así que el orden entre V7 y V8 no importa (design D8).
--    OJO: una migración futura que sume otra acción tiene que copiar ESTA LISTA ENTERA, con CONFIRMAR y AUSENTE.
ALTER TABLE turno_auditoria DROP CONSTRAINT IF EXISTS turno_auditoria_accion_valida;
ALTER TABLE turno_auditoria ADD CONSTRAINT turno_auditoria_accion_valida
    CHECK (accion IN ('CREAR', 'CANCELAR', 'REPROGRAMAR', 'SOBRETURNO', 'EXTENDER', 'CONFIRMAR', 'AUSENTE'));
